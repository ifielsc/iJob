// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.plugin.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.task.TaskDetailDTO;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.task.TaskService;
import com.ifielsc.ijob.infra.entity.runtime.EtlCheckpoint;
import com.ifielsc.ijob.infra.mapper.runtime.EtlCheckpointMapper;
import com.ifielsc.ijob.plugin.batchetl.metadata.JdbcMetadataInspector;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.incremental.EtlIncrementalCoordinator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;

class EtlInspectionServiceTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final PluginConfigCodec codec = new PluginConfigCodec(json);
    private final TaskDetailDTO task = new TaskDetailDTO();
    private EtlCheckpoint saved;

    private EtlInspectionService service(ResourceResolver resources) {
        var tasks =
                new TaskService(null, null, null, null, null, null) {
                    @Override
                    public TaskDetailDTO getDetail(Long id) {
                        return task;
                    }
                };
        var mapper =
                (EtlCheckpointMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {EtlCheckpointMapper.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "select" -> saved;
                                            case "selectByTask" ->
                                                    saved == null ? List.of() : List.of(saved);
                                            default ->
                                                    throw new AssertionError(
                                                            "Read endpoint attempted write: "
                                                                    + method.getName());
                                        });
        return new EtlInspectionService(
                tasks, mapper, resources, codec, json, new JdbcMetadataInspector());
    }

    private void etl() {
        task.setPluginType(PluginType.ETL);
        task.setCurrentRevision(3L);
        task.setPluginConfig(
                """
                {"source":{"type":"DATABASE","resourceId":1,"sql":"SELECT id, mark FROM orders"},
                 "incremental":{"enabled":true,"checkpointKey":"orders","watermarkField":"mark",
                 "watermarkType":"DECIMAL","initialWatermark":0,"tieBreakerFields":["id"]}}
                """);
    }

    @Test
    void noRecordDoesNotInitializeCheckpoint() {
        etl();
        var row = service(null).progress(1L).checkpoints().getFirst();
        assertEquals("EMPTY", row.status());
        assertEquals("0", row.initialValue());
    }

    @Test
    void preservesExactPersistedMarkersAndDistinguishesInitialized() {
        etl();
        saved = new EtlCheckpoint();
        saved.setVersion(0L);
        saved.setWatermarkType("DECIMAL");
        saved.setWatermarkValue("9007199254740993.123456789");
        saved.setTieBreakerValue("[9007199254740994]");
        saved.setLastInstanceId(99L);
        saved.setSourceFingerprint(
                EtlIncrementalCoordinator.fingerprint(
                        codec.read(task.getPluginConfig(), EtlTaskConfig.class, "ETL")));
        var row = service(null).progress(1L).checkpoints().getFirst();
        assertEquals("INITIALIZED", row.status());
        assertEquals("MATCH", row.configurationMatch());
        assertEquals("9007199254740993.123456789", row.watermarkValue());
        saved.setVersion(2L);
        assertEquals("ADVANCED", service(null).progress(1L).checkpoints().getFirst().status());
    }

    @Test
    void changedAndUnresolvedConfigurationNeverClaimsToMatch() {
        etl();
        saved = new EtlCheckpoint();
        saved.setVersion(2L);
        saved.setWatermarkType("DECIMAL");
        saved.setSourceFingerprint("different");
        assertEquals(
                "CHANGED",
                service(null).progress(1L).checkpoints().getFirst().configurationMatch());
        task.setPluginConfig(task.getPluginConfig().replace("FROM orders", "FROM ${table}"));
        assertEquals(
                "UNVERIFIED",
                service(null).progress(1L).checkpoints().getFirst().configurationMatch());
    }

    @Test
    void readsTablesAndViewsAndChecksCompatibilityWithoutChangingData() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("source.db"));
        SQLiteDataSource target = new SQLiteDataSource();
        target.setUrl("jdbc:sqlite:" + directory.resolve("target.db"));
        try (var c = source.getConnection();
                var s = c.createStatement()) {
            s.execute("CREATE TABLE orders(id INTEGER, mark INTEGER, extra TEXT)");
            s.execute("INSERT INTO orders VALUES(1,7,'keep')");
            s.execute("CREATE VIEW orders_view AS SELECT * FROM orders");
        }
        try (var c = target.getConnection();
                var s = c.createStatement()) {
            s.execute("CREATE TABLE orders(id INTEGER, mark INTEGER)");
            s.execute("INSERT INTO orders VALUES(2,9)");
        }
        var resolver =
                new ResourceResolver(null) {
                    @Override
                    public javax.sql.DataSource getDataSource(Long id) {
                        return id == 1L ? source : target;
                    }
                };
        var service = service(resolver);
        assertEquals(
                List.of("orders"),
                service.tables(1L, "", false).tables().stream().map(r -> r.name()).toList());
        assertEquals(2, service.tables(1L, "", true).tables().size());
        String config =
                """
{"sourceResourceId":1,"targetResourceId":2,"tables":["orders","absent"],
 "logicalKeys":["id"],"extractMode":"INCREMENTAL","incremental":{"watermarkColumn":"mark","watermarkType":"LONG"}}
""";
        var result = service.check(config);
        assertEquals("WARNING", result.tables().get(0).status());
        assertEquals("FAIL", result.tables().get(1).status());
        assertTrue(
                service.check(config.replace("\"mark\"", "\"missing\"")).tables().stream()
                        .allMatch(r -> r.status().equals("FAIL")));
        try (var c = target.getConnection();
                var s = c.createStatement();
                var r = s.executeQuery("SELECT id,mark FROM orders")) {
            assertTrue(r.next());
            assertEquals(2, r.getInt(1));
            assertEquals(9, r.getInt(2));
        }
    }

    @Test
    void invalidDraftIsRejectedBeforeConnecting() {
        assertThrows(BusinessException.class, () -> service(null).check("{\"tables\":[]}"));
        assertThrows(BusinessException.class, () -> service(null).check("${config}"));
    }
}
