package com.ifelio.ijob.plugin.batchetl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.core.etl.EtlCheckpointSnapshot;
import com.ifelio.ijob.core.etl.EtlCheckpointStore;
import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.instance.InstanceService;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifelio.ijob.plugin.batchetl.metadata.BatchTablePlanner;
import com.ifelio.ijob.plugin.batchetl.metadata.JdbcMetadataInspector;
import com.ifelio.ijob.plugin.common.jdbc.CancelableJdbcResources;
import com.ifelio.ijob.plugin.support.RecordingInstanceLogs;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

class BatchEtlExecutionTest {
    @Test
    void otherMarkerTypesResumeAndEmptySourcesSucceed() throws Exception {
        String[][] cases = {
            {"LONG", "BIGINT", "9007199254740993", "9007199254740994"},
            {
                "DECIMAL",
                "DECIMAL(30,9)",
                "9007199254740993.123456788",
                "9007199254740993.123456789"
            },
            {"DATE", "DATE", "2026-09-27", "2026-09-28"},
            {"STRING", "VARCHAR", "batch0001", "batch0002"}
        };
        for (String[] item : cases) {
            try (var source = database();
                    var target = database()) {
                String ddl = "CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY, MARK " + item[1] + ")";
                source.createStatement().execute(ddl);
                target.createStatement().execute(ddl);
                var config = config();
                config.setExtractMode(BatchEtlTaskConfig.ExtractMode.INCREMENTAL);
                config.getIncremental().setWatermarkColumn("MARK");
                config.getIncremental().setWatermarkType(item[0]);
                config.getIncremental().setInitialWatermark(item[2]);
                config.getIncremental().setInitialLogicalKeyValues(List.of(0));
                var store = new MemoryCheckpoint();
                assertEquals(0, migrate(source, target, config, store, context()).extracted());
                source.createStatement().execute("INSERT INTO ORDERS VALUES (1,'" + item[3] + "')");
                assertEquals(1, migrate(source, target, config, store, context()).extracted());
                assertEquals(0, migrate(source, target, config, store, context()).extracted());
                assertEquals(1, count(target));
            }
        }
    }

    @Test
    void timestampCheckpointSurvivesSerializationAndPreservesNanoseconds() throws Exception {
        try (var source = database();
                var target = database()) {
            source.createStatement().execute("CREATE TABLE ORDERS (ID BIGINT, MARK TIMESTAMP(9))");
            target.createStatement()
                    .execute("CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY, MARK TIMESTAMP(9))");
            source.createStatement()
                    .execute(
                            "INSERT INTO ORDERS VALUES (1, TIMESTAMP '2026-09-28"
                                    + " 10:00:00.123456789')");
            var config = config();
            config.setExtractMode(BatchEtlTaskConfig.ExtractMode.INCREMENTAL);
            config.getIncremental().setWatermarkColumn("MARK");
            config.getIncremental().setInitialWatermark("2026-09-28T00:00:00");
            config.getIncremental().setInitialLogicalKeyValues(List.of(0));
            var store = new MemoryCheckpoint();
            assertEquals(1, migrate(source, target, config, store, context()).extracted());
            assertEquals("2026-09-28T10:00:00.123456789", store.snapshot.watermark());
            source.createStatement()
                    .execute(
                            "INSERT INTO ORDERS VALUES (2, TIMESTAMP '2026-09-28"
                                    + " 10:00:00.123456789')");
            assertEquals(1, migrate(source, target, config, store, context()).extracted());
            assertEquals(0, migrate(source, target, config, store, context()).extracted());
            assertEquals(2, count(target));
        }
    }

    @Test
    void lateFailurePreservesEarlierBatchAndRetryIsIdempotent() throws Exception {
        try (var source = database();
                var target = database()) {
            source.createStatement().execute("CREATE TABLE ORDERS (ID BIGINT, NAME VARCHAR)");
            target.createStatement()
                    .execute("CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY, NAME VARCHAR NOT NULL)");
            source.createStatement().execute("INSERT INTO ORDERS VALUES (1,'ok'),(2,NULL)");
            var config = config();
            config.getExecution().setWriteBatchSize(1);
            var context = context();
            assertThrows(Exception.class, () -> migrate(source, target, config, null, context));
            target.rollback();
            assertEquals(1, count(target));
            assertEquals(1L, context.getVariable("batchEtl.committedBatches"));
            source.createStatement().execute("UPDATE ORDERS SET NAME='fixed' WHERE ID=2");
            var result = migrate(source, target, config, null, context());
            assertEquals(1, result.inserted());
            assertEquals(1, result.updated());
            assertEquals(2, count(target));
        }
    }

    @Test
    void executorClosesConnectionsOnSuccessAndFailure() throws Exception {
        for (boolean fail : List.of(false, true)) {
            var source = database();
            var target = database();
            source.createStatement().execute("CREATE TABLE ORDERS (ID BIGINT)");
            target.createStatement().execute("CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY)");
            source.createStatement()
                    .execute(
                            fail
                                    ? "INSERT INTO ORDERS VALUES (1),(1)"
                                    : "INSERT INTO ORDERS VALUES (1)");
            ResourceResolver resolver =
                    new ResourceResolver(null) {
                        @Override
                        public javax.sql.DataSource getDataSource(Long id) {
                            return new org.springframework.jdbc.datasource
                                    .SingleConnectionDataSource(id == 1L ? source : target, false);
                        }
                    };
            var executor =
                    new BatchEtlExecutor(
                            resolver,
                            new BatchTablePlanner(new JdbcMetadataInspector()),
                            new BatchTableMigrator(null));
            if (fail)
                assertThrows(
                        IllegalArgumentException.class,
                        () -> executor.execute(config(), context()));
            else assertEquals(1, executor.execute(config(), context()).inserted());
            assertTrue(source.isClosed());
            assertTrue(target.isClosed());
        }
    }

    private BatchEtlResult.TableResult migrate(
            Connection source,
            Connection target,
            BatchEtlTaskConfig config,
            EtlCheckpointStore store,
            TaskContext context)
            throws Exception {
        var plan = new JdbcMetadataInspector().inspect(source, target, config, "ORDERS", "ORDERS");
        source.setAutoCommit(false);
        target.setAutoCommit(false);
        return new BatchTableMigrator(store)
                .migrate(
                        source,
                        target,
                        new CancelableJdbcResources(),
                        new CancelableJdbcResources(),
                        plan,
                        config,
                        context,
                        100);
    }

    private long count(Connection connection) throws Exception {
        try (var rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM ORDERS")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private Connection database() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        return source.getConnection();
    }

    private BatchEtlTaskConfig config() {
        var config = new BatchEtlTaskConfig();
        config.setSourceResourceId(1L);
        config.setTargetResourceId(2L);
        config.setTables(List.of("ORDERS"));
        config.setLogicalKeys(List.of("ID"));
        return config;
    }

    private TaskContext context() {
        var instances =
                new InstanceService(null, null, null, null) {
                    @Override
                    public void updateProgress(Long id, Integer progress) {}
                };
        return new TaskContext(
                1L,
                new ExecutionHandle(1L),
                instances,
                new RecordingInstanceLogs(),
                new VariableResolver());
    }

    private static class MemoryCheckpoint extends EtlCheckpointStore {
        private final ObjectMapper mapper = new ObjectMapper();
        private EtlCheckpointSnapshot snapshot;

        MemoryCheckpoint() {
            super(null, null, new ObjectMapper());
        }

        @Override
        public EtlCheckpointSnapshot loadOrCreate(
                Long instance,
                String key,
                String fingerprint,
                String type,
                Object initial,
                List<Object> ties) {
            if (snapshot == null)
                snapshot = new EtlCheckpointSnapshot(1L, key, fingerprint, type, initial, ties, 0L);
            return snapshot;
        }

        @Override
        public EtlCheckpointSnapshot advanceAndGet(
                EtlCheckpointSnapshot current, Long instance, Object watermark, List<Object> ties) {
            try {
                Object restored =
                        mapper.readerFor(Object.class)
                                .with(
                                        com.fasterxml.jackson.databind.DeserializationFeature
                                                .USE_BIG_DECIMAL_FOR_FLOATS)
                                .readValue(mapper.writeValueAsString(watermark));
                snapshot =
                        new EtlCheckpointSnapshot(
                                current.taskId(),
                                current.checkpointKey(),
                                current.sourceFingerprint(),
                                current.watermarkType(),
                                restored,
                                ties,
                                current.version() + 1);
                return snapshot;
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
    }
}
