// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.etl.EtlCheckpointSnapshot;
import com.ifielsc.ijob.core.etl.EtlMessageBindingStore;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.resource.ResourceClientLease;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.resource.mq.MessageDelivery;
import com.ifielsc.ijob.core.resource.mq.MessageSourceOptions;
import com.ifielsc.ijob.core.resource.mq.MessageSourceSession;
import com.ifielsc.ijob.plugin.etl.EtlPipelineFactory;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfigValidator;
import com.ifielsc.ijob.plugin.etl.loader.EtlLoaderFactory;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;
import com.ifielsc.ijob.plugin.etl.transformer.TransformerFactory;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorFactory;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

class MessageTestSupport {
    final DriverManagerDataSource database =
            new DriverManagerDataSource(
                    "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    final EtlTaskConfig config = new EtlTaskConfig();
    int releases;

    MessageTestSupport() throws Exception {
        try (var c = database.getConnection()) {
            c.createStatement()
                    .execute(
                            "CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY, NAME VARCHAR(30) NOT"
                                    + " NULL)");
        }
        config.setVersion(2);
        var source = new EtlTaskConfig.SourceConfig();
        source.setType(EtlTaskConfig.SourceType.KAFKA);
        source.setResourceId(1L);
        source.setTopic("isolated-test");
        source.setGroupId("isolated-test");
        source.setQueue("isolated-test");
        config.setSource(source);
        var target = new EtlTaskConfig.TargetConfig();
        target.setType(EtlTaskConfig.TargetType.DATABASE);
        target.setResourceId(2L);
        target.setTable("ORDERS");
        target.setLogicalKeys(List.of("ID"));
        target.setMode(EtlTaskConfig.LoadMode.UPSERT);
        target.setTransactionScope(EtlTaskConfig.TransactionScope.BATCH);
        target.setColumns(new LinkedHashMap<>(Map.of("ID", "id", "NAME", "value")));
        config.setTarget(target);
        config.getMessage().setMaxMessages(2);
        config.getMessage().setBatchSize(2);
        config.getMessage().setIntakeSeconds(10);
        config.getMessage().setIdleSeconds(1);
    }

    EtlContext context() {
        return new EtlContext(
                new TaskContext(
                        1L,
                        new ExecutionHandle(1L),
                        null,
                        new RecordingInstanceLogs(),
                        new VariableResolver(),
                        Map.of()) {
                    @Override
                    public void progress(Integer value) {}
                },
                config);
    }

    EtlMessageRunner runner(Supplier<MessageSourceSession> sessions) {
        var resolver =
                new ResourceResolver(null) {
                    @Override
                    public javax.sql.DataSource getDataSource(Long id) {
                        return database;
                    }

                    @Override
                    public String endpointIdentity(Long id) {
                        return "isolated-" + id;
                    }

                    @Override
                    public ResourceClientLease<MessageSourceSession> leaseMessageSource(
                            Long id, ResourceType type, MessageSourceOptions options) {
                        var session = sessions.get();
                        return new ResourceClientLease<>(session, session::close);
                    }
                };
        var bindings =
                new EtlMessageBindingStore(null, null) {
                    @Override
                    public EtlCheckpointSnapshot acquire(
                            Long id, String fingerprint, String token) {
                        return new EtlCheckpointSnapshot(
                                1L, "message", fingerprint, "STRING", "BROKER", List.of(), 0);
                    }

                    @Override
                    public EtlCheckpointSnapshot verify(
                            EtlCheckpointSnapshot snapshot, Long id, String token) {
                        return snapshot;
                    }

                    @Override
                    public void release(EtlCheckpointSnapshot snapshot, String token) {
                        releases++;
                    }
                };
        return new EtlMessageRunner(
                resolver,
                bindings,
                new EtlPipelineFactory(
                        null,
                        new EtlLoaderFactory(resolver),
                        new TransformerFactory(List.of()),
                        new ValidatorFactory(List.of())),
                new ObjectMapper());
    }

    void run(Supplier<MessageSourceSession> source, EtlContext context) throws Exception {
        new EtlTaskConfigValidator().validate(config);
        runner(source).execute(context);
    }

    int count() throws Exception {
        try (var c = database.getConnection();
                var r = c.createStatement().executeQuery("SELECT COUNT(*) FROM ORDERS")) {
            r.next();
            return r.getInt(1);
        }
    }

    String value() throws Exception {
        try (var c = database.getConnection();
                var r = c.createStatement().executeQuery("SELECT NAME FROM ORDERS WHERE ID=1")) {
            return r.next() ? r.getString(1) : null;
        }
    }

    static MessageDelivery delivery(String json) {
        return new MessageDelivery(
                json.getBytes(StandardCharsets.UTF_8),
                null,
                Map.of("partition", 0, "offset", 1L),
                1L);
    }
}
