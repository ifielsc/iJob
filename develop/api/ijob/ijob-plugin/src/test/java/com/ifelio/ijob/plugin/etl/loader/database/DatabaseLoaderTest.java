package com.ifelio.ijob.plugin.etl.loader.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.loader.LoadBatchResult;
import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.model.EtlContext;
import com.ifelio.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.sql.DataSource;

class DatabaseLoaderTest {
    @Test
    void runCommitIsRecordedBeforeAnyLaterCheckpointFailure() {
        RecordingDataSource dataSource = new RecordingDataSource();
        EtlTaskConfig config = config();
        config.getTarget().setTransactionScope(EtlTaskConfig.TransactionScope.RUN);
        EtlContext context = new EtlContext(taskContext(), config);
        DatabaseLoader loader = new DatabaseLoader(resolver(dataSource), config.getTarget());
        try {
            loader.open(context);
            loader.writeBatch(List.of(record(1), record(2)));
            loader.writeBatch(List.of(record(3)));
            assertNull(context.getTaskContext().getVariable("etl.committedBatches"));
            loader.flush();
            assertEquals(1L, context.getTaskContext().getVariable("etl.committedBatches"));
            assertEquals(3L, context.getTaskContext().getVariable("etl.committedRows"));
        } finally {
            loader.close();
        }
    }

    @Test
    void fileRunCommitsCountOnlyEachLoadersRows() {
        RecordingDataSource dataSource = new RecordingDataSource();
        EtlTaskConfig config = config();
        config.getTarget().setTransactionScope(EtlTaskConfig.TransactionScope.RUN);
        EtlContext context = new EtlContext(taskContext(), config);
        for (int count : List.of(2, 1)) {
            DatabaseLoader loader = new DatabaseLoader(resolver(dataSource), config.getTarget());
            try {
                loader.open(context);
                List<DataRecord> records = new ArrayList<>();
                for (int index = 0; index < count; index++) records.add(record(index));
                loader.writeBatch(records);
                context.setLoadCount(context.getLoadCount() + count);
                loader.flush();
            } finally {
                loader.close();
            }
        }
        assertEquals(2L, context.getTaskContext().getVariable("etl.committedBatches"));
        assertEquals(3L, context.getTaskContext().getVariable("etl.committedRows"));
    }

    @Test
    void failedRunCommitIsNotReportedAsAcknowledged() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.failCommit = true;
        EtlTaskConfig config = config();
        config.getTarget().setTransactionScope(EtlTaskConfig.TransactionScope.RUN);
        EtlContext context = new EtlContext(taskContext(), config);
        DatabaseLoader loader = new DatabaseLoader(resolver(dataSource), config.getTarget());
        try {
            loader.open(context);
            loader.writeBatch(List.of(record(1)));
            assertThrows(IllegalStateException.class, loader::flush);
            assertNull(context.getTaskContext().getVariable("etl.committedBatches"));
            assertNull(context.getTaskContext().getVariable("etl.committedRows"));
        } finally {
            loader.close();
        }
    }

    @Test
    void missingManualFieldRejectsWholeInputBatchBeforeAnyDatabaseWrite() {
        RecordingDataSource dataSource = new RecordingDataSource();
        EtlTaskConfig config = config();
        config.getExecution().setWriteBatchSize(1);
        config.getTarget().getColumns().put("name", "source_name");
        DataRecord first = record(1);
        first.put("source_name", "valid");
        DatabaseLoader loader = new DatabaseLoader(resolver(dataSource), config.getTarget());
        try {
            loader.open(new EtlContext(taskContext(), config));
            IllegalStateException error =
                    assertThrows(
                            IllegalStateException.class,
                            () -> loader.writeBatch(List.of(first, record(2))));
            assertTrue(error.getCause().getMessage().contains("mapped source field is missing"));
            assertTrue(dataSource.batchSizes.isEmpty());
            assertEquals(0, dataSource.commits.get());
        } finally {
            loader.close();
        }
    }

    @Test
    void explicitNullManualFieldRemainsAValidDatabaseValue() {
        RecordingDataSource dataSource = new RecordingDataSource();
        EtlTaskConfig config = config();
        config.getTarget().getColumns().put("name", "source_name");
        DataRecord record = record(1);
        record.put("source_name", null);
        DatabaseLoader loader = new DatabaseLoader(resolver(dataSource), config.getTarget());
        try {
            loader.open(new EtlContext(taskContext(), config));
            assertEquals(1, loader.writeBatch(List.of(record)).inserted());
            loader.flush();
        } finally {
            loader.close();
        }
        assertEquals(List.of(1), dataSource.batchSizes);
    }

    @Test
    void shouldSplitAppendWritesByConfiguredBatchSize() {
        RecordingDataSource dataSource = new RecordingDataSource();
        EtlTaskConfig config = config();
        DatabaseLoader loader = new DatabaseLoader(resolver(dataSource), config.getTarget());
        EtlContext context = new EtlContext(taskContext(), config);

        loader.open(context);
        LoadBatchResult result = loader.writeBatch(List.of(record(1), record(2), record(3)));
        loader.flush();
        loader.close();

        assertEquals(3, result.inserted());
        assertEquals(List.of(2, 1), dataSource.batchSizes);
        assertEquals(3, dataSource.commits.get());
        assertEquals(2L, context.getTaskContext().getVariable("etl.committedBatches"));
    }

    @Test
    void shouldCloseConnectionWhenStopWinsOpenRace() {
        AtomicReference<DatabaseLoader> loaderRef = new AtomicReference<>();
        AtomicInteger closes = new AtomicInteger();
        DataSource dataSource = stoppingDataSource(loaderRef, closes);
        EtlTaskConfig config = config();
        DatabaseLoader loader = new DatabaseLoader(resolver(dataSource), config.getTarget());
        loaderRef.set(loader);

        assertThrows(
                IllegalStateException.class,
                () -> loader.open(new EtlContext(taskContext(), config)));
        assertEquals(1, closes.get());
    }

    @Test
    void shouldInsertFirstScd2Version() {
        RecordingDataSource dataSource = new RecordingDataSource();
        EtlTaskConfig config = config();
        config.getTarget().setMode(EtlTaskConfig.LoadMode.SCD2);
        config.getTarget().getColumns().put("name", "name");
        EtlTaskConfig.SCD2Config scd2 = new EtlTaskConfig.SCD2Config();
        scd2.getBusinessKeys().add("id");
        scd2.getCompareFields().add("name");
        scd2.setEffectiveValueField("effective");
        scd2.setValidFromColumn("valid_from");
        scd2.setValidToColumn("valid_to");
        config.getTarget().setScd2(scd2);
        DatabaseLoader loader = new DatabaseLoader(resolver(dataSource), config.getTarget());
        EtlContext context = new EtlContext(taskContext(), config);
        DataRecord record = record(1);
        record.put("name", "first");
        record.put("effective", "2026-08-29T00:00:00");

        loader.open(context);
        LoadBatchResult result = loader.writeBatch(List.of(record));
        loader.flush();
        loader.close();

        assertEquals(1, result.loaded());
        assertEquals(1, result.inserted());
        assertEquals(0, result.updated());
        assertTrue(dataSource.preparedSqls.stream().anyMatch(sql -> sql.startsWith("SELECT")));
        assertTrue(
                dataSource.preparedSqls.stream()
                        .anyMatch(
                                sql ->
                                        sql.startsWith("INSERT")
                                                && sql.contains("`valid_from`")
                                                && sql.contains("`valid_to`")));
    }

    @Test
    void shouldUpdateAnExistingScd2Version() {
        RecordingDataSource dataSource = new RecordingDataSource();
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("name", "old");
        current.put("valid_from", LocalDateTime.of(2026, 8, 28, 0, 0));
        dataSource.queryRows.add(current);
        EtlTaskConfig config = config();
        config.getTarget().setMode(EtlTaskConfig.LoadMode.SCD2);
        config.getTarget().getColumns().put("name", "name");
        EtlTaskConfig.SCD2Config scd2 = new EtlTaskConfig.SCD2Config();
        scd2.getBusinessKeys().add("id");
        scd2.getCompareFields().add("name");
        scd2.setEffectiveValueField("effective");
        scd2.setValidFromColumn("valid_from");
        scd2.setValidToColumn("valid_to");
        config.getTarget().setScd2(scd2);
        DataRecord record = record(1);
        record.put("name", "new");
        record.put("effective", "2026-08-29T00:00:00");
        DatabaseLoader loader = new DatabaseLoader(resolver(dataSource), config.getTarget());

        loader.open(new EtlContext(taskContext(), config));
        LoadBatchResult result = loader.writeBatch(List.of(record));
        loader.flush();
        loader.close();

        assertEquals(1, result.inserted());
        assertEquals(1, result.updated());
    }

    @Test
    void shouldMatchExistingNumericKeysAcrossJdbcNumberTypes() {
        RecordingDataSource dataSource = new RecordingDataSource();
        dataSource.queryRows.add(new LinkedHashMap<>(Map.of("id", 1L)));
        EtlTaskConfig config = config();
        config.getTarget().setMode(EtlTaskConfig.LoadMode.INSERT_ONLY);
        config.getTarget().getLogicalKeys().add("id");
        DatabaseLoader loader = new DatabaseLoader(resolver(dataSource), config.getTarget());

        loader.open(new EtlContext(taskContext(), config));
        LoadBatchResult result = loader.writeBatch(List.of(record(1)));
        loader.flush();
        loader.close();

        assertEquals(0, result.inserted());
        assertEquals(1, result.skipped());
    }

    private EtlTaskConfig config() {
        EtlTaskConfig config = new EtlTaskConfig();
        config.getExecution().setWriteBatchSize(2);
        EtlTaskConfig.TargetConfig target = new EtlTaskConfig.TargetConfig();
        target.setType(EtlTaskConfig.TargetType.DATABASE);
        target.setResourceId(1L);
        target.setTable("target_table");
        target.setMode(EtlTaskConfig.LoadMode.APPEND);
        target.getColumns().put("id", "id");
        config.setTarget(target);
        return config;
    }

    private ResourceResolver resolver(DataSource dataSource) {
        return new ResourceResolver(null) {
            @Override
            public DataSource getDataSource(Long resourceId) {
                return dataSource;
            }
        };
    }

    private TaskContext taskContext() {
        return new TaskContext(
                1L,
                new ExecutionHandle(1L),
                null,
                new RecordingInstanceLogs(),
                new VariableResolver(),
                Map.of()) {
            @Override
            public void progress(Integer progress) {}
        };
    }

    private DataRecord record(int id) {
        DataRecord record = new DataRecord();
        record.put("id", id);
        return record;
    }

    private DataSource stoppingDataSource(
            AtomicReference<DatabaseLoader> loader, AtomicInteger closes) {
        return new DataSource() {
            @Override
            public Connection getConnection() {
                loader.get().close();
                return (Connection)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("close")) {
                                        closes.incrementAndGet();
                                    }
                                    return RecordingDataSource.defaultValue(method.getReturnType());
                                });
            }

            @Override
            public Connection getConnection(String username, String password) {
                return getConnection();
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {}

            @Override
            public void setLoginTimeout(int seconds) {}

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public Logger getParentLogger() throws SQLFeatureNotSupportedException {
                throw new SQLFeatureNotSupportedException();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                throw new SQLException("not a wrapper");
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }

    private static final class RecordingDataSource implements DataSource {
        private final List<Integer> batchSizes = new ArrayList<>();
        private final List<String> preparedSqls = new ArrayList<>();
        private final List<Map<String, Object>> queryRows = new ArrayList<>();
        private final AtomicInteger commits = new AtomicInteger();
        private boolean failCommit;

        @Override
        public Connection getConnection() {
            DatabaseMetaData metadata =
                    (DatabaseMetaData)
                            Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] {DatabaseMetaData.class},
                                    (proxy, method, args) ->
                                            method.getName().equals("getIdentifierQuoteString")
                                                    ? "`"
                                                    : defaultValue(method.getReturnType()));
            return (Connection)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getMetaData" -> metadata;
                                        case "prepareStatement" -> statement((String) args[0]);
                                        case "commit" -> {
                                            if (failCommit)
                                                throw new SQLException("commit rejected");
                                            commits.incrementAndGet();
                                            yield null;
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
        }

        private PreparedStatement statement(String sql) {
            preparedSqls.add(sql);
            AtomicInteger pending = new AtomicInteger();
            return (PreparedStatement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {PreparedStatement.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "addBatch" -> {
                                            pending.incrementAndGet();
                                            yield null;
                                        }
                                        case "executeBatch" -> {
                                            int size = pending.getAndSet(0);
                                            batchSizes.add(size);
                                            yield java.util.stream.IntStream.range(0, size)
                                                    .map(index -> 1)
                                                    .toArray();
                                        }
                                        case "executeQuery" -> rows();
                                        case "executeUpdate" -> 1;
                                        default -> defaultValue(method.getReturnType());
                                    });
        }

        private ResultSet rows() {
            int[] cursor = {-1};
            return (ResultSet)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "next" -> ++cursor[0] < queryRows.size();
                                        case "getObject" -> {
                                            if (cursor[0] < 0 || cursor[0] >= queryRows.size()) {
                                                throw new SQLException(
                                                        "ResultSet has no current row");
                                            }
                                            Map<String, Object> row = queryRows.get(cursor[0]);
                                            yield args[0] instanceof Integer index
                                                    ? new ArrayList<>(row.values()).get(index - 1)
                                                    : row.get(args[0].toString());
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            return 0;
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter output) {}

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> type) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> type) {
            return false;
        }
    }
}
