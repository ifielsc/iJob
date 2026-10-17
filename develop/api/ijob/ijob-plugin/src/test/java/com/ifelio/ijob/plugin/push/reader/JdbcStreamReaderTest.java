package com.ifelio.ijob.plugin.push.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

class JdbcStreamReaderTest {
    @Test
    void readerShouldUseReadOnlyStreamingAndPreserveColumnOrder() throws Exception {
        JdbcFixture jdbc = new JdbcFixture(false);

        try (JdbcStreamReader reader =
                new JdbcStreamReader(jdbc.dataSource(), "select id, name", 37)) {
            assertTrue(reader.hasNext());
            Map<String, Object> row = reader.next();
            assertEquals(List.of("id", "name"), List.copyOf(row.keySet()));
            assertEquals(7L, row.get("id"));
            assertEquals("ready", row.get("name"));
            assertFalse(reader.hasNext());
        }

        assertTrue(jdbc.readOnly);
        assertEquals(List.of(false), jdbc.autoCommit);
        assertEquals(37, jdbc.fetchSize);
        assertEquals(1, jdbc.metadataCalls.get());
        assertEquals(1, jdbc.resultClose.get());
        assertEquals(1, jdbc.statementCancel.get());
        assertEquals(1, jdbc.statementClose.get());
        assertEquals(1, jdbc.connectionClose.get());
    }

    @Test
    void constructorFailureShouldClosePartialJdbcResources() {
        JdbcFixture jdbc = new JdbcFixture(true);

        assertThrows(
                SQLException.class,
                () -> new JdbcStreamReader(jdbc.dataSource(), "select broken", 10));

        assertEquals(0, jdbc.resultClose.get());
        assertEquals(1, jdbc.statementCancel.get());
        assertEquals(1, jdbc.statementClose.get());
        assertEquals(1, jdbc.connectionClose.get());
    }

    @Test
    void closeShouldBeIdempotent() throws Exception {
        JdbcFixture jdbc = new JdbcFixture(false);
        JdbcStreamReader reader = new JdbcStreamReader(jdbc.dataSource(), "select id", 10);

        reader.close();
        reader.close();

        assertEquals(1, jdbc.resultClose.get());
        assertEquals(1, jdbc.statementCancel.get());
        assertEquals(1, jdbc.statementClose.get());
        assertEquals(1, jdbc.connectionClose.get());
    }

    @Test
    void mysqlReaderShouldUseStreamingSentinel() throws Exception {
        JdbcFixture jdbc = new JdbcFixture(false, "MySQL");

        try (JdbcStreamReader reader = new JdbcStreamReader(jdbc.dataSource(), "select id", 10)) {
            assertTrue(reader.hasNext());
            assertEquals(Integer.MIN_VALUE, jdbc.fetchSize);
        }
    }

    @Test
    void registrarShouldExposeStatementToStopDuringExecuteQuery() throws Exception {
        CountDownLatch executing = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<AutoCloseable> registered = new AtomicReference<>();
        PreparedStatement statement =
                proxy(
                        PreparedStatement.class,
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "executeQuery" -> {
                                        executing.countDown();
                                        released.await();
                                        throw new SQLException("cancelled");
                                    }
                                    case "cancel" -> {
                                        cancelled.set(true);
                                        released.countDown();
                                        yield null;
                                    }
                                    default -> defaultValue(method.getReturnType());
                                });
        DatabaseMetaData databaseMetaData =
                proxy(
                        DatabaseMetaData.class,
                        (proxy, method, args) ->
                                "getDatabaseProductName".equals(method.getName())
                                        ? "PostgreSQL"
                                        : defaultValue(method.getReturnType()));
        Connection connection =
                proxy(
                        Connection.class,
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "prepareStatement" -> statement;
                                    case "getMetaData" -> databaseMetaData;
                                    default -> defaultValue(method.getReturnType());
                                });
        DataSource dataSource =
                proxy(
                        DataSource.class,
                        (proxy, method, args) ->
                                "getConnection".equals(method.getName())
                                        ? connection
                                        : defaultValue(method.getReturnType()));

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> opening =
                    executor.submit(
                            () -> {
                                try {
                                    new JdbcStreamReader(
                                            dataSource, "select blocked", 10, registered::set);
                                } catch (Exception e) {
                                    throw new IllegalStateException(e);
                                }
                            });
            assertTrue(executing.await(2, TimeUnit.SECONDS));
            registered.get().close();

            assertThrows(ExecutionException.class, () -> opening.get(2, TimeUnit.SECONDS));
            assertTrue(cancelled.get());
        }
    }

    private static final class JdbcFixture {
        private final boolean failExecute;
        private final String productName;
        private final AtomicInteger resultClose = new AtomicInteger();
        private final AtomicInteger statementCancel = new AtomicInteger();
        private final AtomicInteger statementClose = new AtomicInteger();
        private final AtomicInteger connectionClose = new AtomicInteger();
        private final AtomicInteger metadataCalls = new AtomicInteger();
        private final List<Boolean> autoCommit = new java.util.ArrayList<>();
        private volatile boolean readOnly;
        private volatile int fetchSize;

        private JdbcFixture(boolean failExecute) {
            this(failExecute, "PostgreSQL");
        }

        private JdbcFixture(boolean failExecute, String productName) {
            this.failExecute = failExecute;
            this.productName = productName;
        }

        private DataSource dataSource() {
            DatabaseMetaData databaseMetaData =
                    proxy(
                            DatabaseMetaData.class,
                            (proxy, method, args) ->
                                    "getDatabaseProductName".equals(method.getName())
                                            ? productName
                                            : defaultValue(method.getReturnType()));
            ResultSetMetaData metadata =
                    proxy(
                            ResultSetMetaData.class,
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getColumnCount" -> 2;
                                        case "getColumnLabel" ->
                                                (Integer) args[0] == 1 ? "id" : "name";
                                        default -> defaultValue(method.getReturnType());
                                    });
            AtomicInteger index = new AtomicInteger(-1);
            ResultSet resultSet =
                    proxy(
                            ResultSet.class,
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "next" -> index.incrementAndGet() == 0;
                                        case "getMetaData" -> {
                                            metadataCalls.incrementAndGet();
                                            yield metadata;
                                        }
                                        case "getObject" -> (Integer) args[0] == 1 ? 7L : "ready";
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
                                        case "setFetchSize" -> {
                                            fetchSize = (Integer) args[0];
                                            yield null;
                                        }
                                        case "executeQuery" -> {
                                            if (failExecute) {
                                                throw new SQLException("query rejected");
                                            }
                                            yield resultSet;
                                        }
                                        case "cancel" -> {
                                            statementCancel.incrementAndGet();
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
                                        case "setAutoCommit" -> {
                                            autoCommit.add((Boolean) args[0]);
                                            yield null;
                                        }
                                        case "prepareStatement" -> statement;
                                        case "getMetaData" -> databaseMetaData;
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
                case "toString" -> "PushJdbcFixture";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> delegate.invoke(proxy, method, args);
            };
        }
    }
}
