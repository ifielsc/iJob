package com.ifielsc.ijob.plugin.etl.validator.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.core.resource.ResourceResolver;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

class SqlBatchExecutorTest {
    @Test
    void shouldSplitLargeInQueriesIntoBoundedBatches() {
        RecordingDataSource dataSource = new RecordingDataSource();
        ResourceResolver resolver =
                new ResourceResolver(null) {
                    @Override
                    public DataSource getDataSource(Long resourceId) {
                        return dataSource;
                    }
                };
        SqlBatchExecutor executor = new SqlBatchExecutor(resolver);

        List<Object> result =
                executor.queryIn(
                        1L,
                        "SELECT id FROM ref WHERE id IN (:keys)",
                        List.of(1, 2, 3, 4, 5),
                        30,
                        2,
                        null);

        assertEquals(List.of(1, 2, 3, 4, 5), result);
        assertEquals(List.of(2, 2, 1), dataSource.parameterCounts);
    }

    private static final class RecordingDataSource implements DataSource {
        private final List<Integer> parameterCounts = new ArrayList<>();

        @Override
        public Connection getConnection() {
            return (Connection)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "prepareStatement" -> statement();
                                        default -> defaultValue(method.getReturnType());
                                    });
        }

        private PreparedStatement statement() {
            List<Object> parameters = new ArrayList<>();
            return (PreparedStatement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {PreparedStatement.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "setObject" -> {
                                            int index = (Integer) args[0];
                                            while (parameters.size() < index) {
                                                parameters.add(null);
                                            }
                                            parameters.set(index - 1, args[1]);
                                            yield null;
                                        }
                                        case "executeQuery" -> {
                                            parameterCounts.add(parameters.size());
                                            yield rows(List.copyOf(parameters));
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
        }

        private ResultSet rows(List<Object> values) {
            AtomicInteger index = new AtomicInteger(-1);
            return (ResultSet)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "next" -> index.incrementAndGet() < values.size();
                                        case "getObject" -> values.get(index.get());
                                        default -> defaultValue(method.getReturnType());
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
}
