package com.ifielsc.ijob.plugin.etl.incremental;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.core.etl.EtlCheckpointSnapshot;
import com.ifielsc.ijob.core.etl.EtlCheckpointStore;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.sql.DataSource;

class EtlIncrementalCoordinatorTest {
    @Test
    void shouldPrepareSourceBeforeCapturingUpperBound() {
        List<String> operations = new ArrayList<>();
        DataSource dataSource = dataSource(operations);
        EtlIncrementalCoordinator coordinator =
                new EtlIncrementalCoordinator(checkpointStore(), resolver(dataSource));
        EtlTaskConfig config = config();

        EtlIncrementalSession session = coordinator.open(config, taskContext());
        Map<String, Object> parameters = new LinkedHashMap<>();
        session.addParameters(parameters);

        assertEquals(List.of("before", "upper"), operations);
        assertEquals(20L, parameters.get("etlUpperWatermark"));
    }

    private EtlTaskConfig config() {
        EtlTaskConfig config = new EtlTaskConfig();
        EtlTaskConfig.SourceConfig source = new EtlTaskConfig.SourceConfig();
        source.setType(EtlTaskConfig.SourceType.DATABASE);
        source.setResourceId(1L);
        source.setSql("SELECT * FROM source");
        source.getBeforeSql().add("REFRESH SOURCE");
        config.setSource(source);
        config.getIncremental().setEnabled(true);
        config.getIncremental().setCheckpointKey("orders");
        config.getIncremental().setWatermarkField("updated_at");
        config.getIncremental().setWatermarkType("LONG");
        config.getIncremental().setInitialWatermark(0L);
        config.getIncremental().setUpperBoundSql("SELECT MAX(updated_at)");
        return config;
    }

    private EtlCheckpointStore checkpointStore() {
        return new EtlCheckpointStore(null, null, null) {
            @Override
            public EtlCheckpointSnapshot loadOrCreate(
                    Long instanceId,
                    String checkpointKey,
                    String sourceFingerprint,
                    String watermarkType,
                    Object initialWatermark,
                    List<Object> initialTieBreakers) {
                return new EtlCheckpointSnapshot(
                        1L,
                        checkpointKey,
                        sourceFingerprint,
                        watermarkType,
                        initialWatermark,
                        List.copyOf(initialTieBreakers),
                        0);
            }
        };
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
                Map.of());
    }

    private DataSource dataSource(List<String> operations) {
        return new DataSource() {
            @Override
            public Connection getConnection() {
                return connection(operations);
            }

            @Override
            public Connection getConnection(String user, String password) {
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
        };
    }

    private Connection connection(List<String> operations) {
        return (Connection)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "createStatement" -> statement(operations);
                                    case "prepareStatement" -> prepared(operations);
                                    default -> defaultValue(method.getReturnType());
                                });
    }

    private Statement statement(List<String> operations) {
        return (Statement)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {Statement.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("execute")) {
                                operations.add("before");
                                return true;
                            }
                            return defaultValue(method.getReturnType());
                        });
    }

    private PreparedStatement prepared(List<String> operations) {
        return (PreparedStatement)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {PreparedStatement.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("executeQuery")) {
                                operations.add("upper");
                                return rows();
                            }
                            return defaultValue(method.getReturnType());
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
                                    case "next" -> ++cursor[0] == 0;
                                    case "getObject" -> 20L;
                                    default -> defaultValue(method.getReturnType());
                                });
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }
}
