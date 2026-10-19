// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

class JdbcExecutorTest {
    @Test
    void scalarShouldUseConfiguredResourceReadOnlyAndTimeout() {
        JdbcFixture jdbc = new JdbcFixture(List.of("value"), List.of(List.of("READY")));
        JdbcExecutor executor = executor(jdbc);

        Object value = executor.queryForObject(12L, "select state", 17, context());

        assertEquals("READY", value);
        assertEquals(1, jdbc.resolveCount.get());
        assertTrue(jdbc.readOnly);
        assertEquals(17, jdbc.queryTimeout);
        assertEquals("select state", jdbc.sql);
        assertEquals(1, jdbc.resultClose.get());
        assertEquals(1, jdbc.statementClose.get());
        assertEquals(1, jdbc.connectionClose.get());
    }

    @Test
    void rowsExistsAndCountShouldHaveDefinedExtraction() {
        JdbcFixture rows =
                new JdbcFixture(
                        List.of("id", "state"), List.of(List.of(1L, "A"), List.of(2L, "B")));
        List<Map<String, Object>> values = executor(rows).query(12L, "select rows", 10, context());
        assertEquals(List.of("id", "state"), List.copyOf(values.getFirst().keySet()));
        assertEquals(2, values.size());

        JdbcFixture exists = new JdbcFixture(List.of("id"), List.of(List.of(1L)));
        assertTrue(executor(exists).exists(12L, "select one", 10, context()));

        JdbcFixture count = new JdbcFixture(List.of("count"), List.of(List.of(9L)));
        assertEquals(9L, executor(count).queryCount(12L, "select count", 10, context()));
    }

    @Test
    void emptyScalarShouldReturnNull() {
        JdbcFixture jdbc = new JdbcFixture(List.of("value"), List.of());

        Object value = executor(jdbc).queryForObject(12L, "select none", 10, context());

        assertEquals(null, value);
    }

    @Test
    void queryFailureShouldClosePartialResources() {
        JdbcFixture jdbc = new JdbcFixture(List.of("value"), List.of(List.of("ignored")));
        jdbc.failure = new SQLException("password=secret-value");

        assertThrows(
                IllegalStateException.class,
                () -> executor(jdbc).queryForObject(12L, "select broken", 10, context()));

        assertEquals(1, jdbc.statementCancel.get());
        assertEquals(1, jdbc.statementClose.get());
        assertEquals(1, jdbc.connectionClose.get());
    }

    @Test
    void cleanupShouldCancelBlockedExecuteQueryAndReleaseAllResources() throws Exception {
        JdbcFixture jdbc = new JdbcFixture(List.of("value"), List.of(List.of("ignored")));
        jdbc.block = true;
        TaskContext context = context();

        try (ExecutorService service = Executors.newSingleThreadExecutor()) {
            Future<?> future =
                    service.submit(
                            () ->
                                    executor(jdbc)
                                            .queryForObject(12L, "select blocked", 10, context));
            assertTrue(jdbc.executing.await(2, TimeUnit.SECONDS));
            context.cleanupResources();

            assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        }

        assertEquals(1, jdbc.statementCancel.get());
        assertEquals(1, jdbc.statementClose.get());
        assertEquals(1, jdbc.connectionClose.get());
    }

    private JdbcExecutor executor(JdbcFixture jdbc) {
        return new JdbcExecutor(
                new ResourceResolver(null) {
                    @Override
                    public DataSource getDataSource(Long resourceId) {
                        assertEquals(12L, resourceId);
                        jdbc.resolveCount.incrementAndGet();
                        return jdbc.dataSource();
                    }
                });
    }

    private TaskContext context() {
        return new TaskContext(
                1L,
                new ExecutionHandle(1L),
                null,
                new RecordingInstanceLogs(),
                new VariableResolver());
    }

    private static final class JdbcFixture {
        private final List<String> labels;
        private final List<List<Object>> rows;
        private final AtomicInteger resolveCount = new AtomicInteger();
        private final AtomicInteger resultClose = new AtomicInteger();
        private final AtomicInteger statementCancel = new AtomicInteger();
        private final AtomicInteger statementClose = new AtomicInteger();
        private final AtomicInteger connectionClose = new AtomicInteger();
        private final CountDownLatch executing = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile boolean readOnly;
        private volatile int queryTimeout;
        private volatile String sql;
        private volatile boolean block;
        private volatile SQLException failure;

        private JdbcFixture(List<String> labels, List<List<Object>> rows) {
            this.labels = labels;
            this.rows = rows;
        }

        private DataSource dataSource() {
            AtomicInteger index = new AtomicInteger(-1);
            ResultSetMetaData metadata =
                    proxy(
                            ResultSetMetaData.class,
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getColumnCount" -> labels.size();
                                        case "getColumnLabel" -> labels.get((Integer) args[0] - 1);
                                        default -> defaultValue(method.getReturnType());
                                    });
            ResultSet resultSet =
                    proxy(
                            ResultSet.class,
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "next" -> index.incrementAndGet() < rows.size();
                                        case "getMetaData" -> metadata;
                                        case "getObject" ->
                                                rows.get(index.get()).get((Integer) args[0] - 1);
                                        case "close" -> {
                                            resultClose.incrementAndGet();
                                            yield null;
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
            PreparedStatement statement =
                    proxy(
                            PreparedStatement.class,
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "setQueryTimeout" -> {
                                            queryTimeout = (Integer) args[0];
                                            yield null;
                                        }
                                        case "executeQuery" -> {
                                            executing.countDown();
                                            if (block) {
                                                release.await();
                                                throw new SQLException("cancelled");
                                            }
                                            if (failure != null) {
                                                throw failure;
                                            }
                                            yield resultSet;
                                        }
                                        case "cancel" -> {
                                            statementCancel.incrementAndGet();
                                            release.countDown();
                                            yield null;
                                        }
                                        case "close" -> {
                                            statementClose.incrementAndGet();
                                            yield null;
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
            Connection connection =
                    proxy(
                            Connection.class,
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "setReadOnly" -> {
                                            readOnly = (Boolean) args[0];
                                            yield null;
                                        }
                                        case "prepareStatement" -> {
                                            sql = (String) args[0];
                                            yield statement;
                                        }
                                        case "close" -> {
                                            connectionClose.incrementAndGet();
                                            yield null;
                                        }
                                        default -> defaultValue(method.getReturnType());
                                    });
            return proxy(
                    DataSource.class,
                    (proxy, method, args) ->
                            "getConnection".equals(method.getName())
                                    ? connection
                                    : defaultValue(method.getReturnType()));
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        new ObjectMethodHandler(handler)));
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

    private record ObjectMethodHandler(InvocationHandler delegate) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "toString" -> "DataCheckJdbcFixture";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> delegate.invoke(proxy, method, args);
            };
        }
    }
}
