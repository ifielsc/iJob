package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

class SqlServerPreMigrationGuardTest {
    @Test
    void shouldAcceptReadCommittedSnapshotOff() {
        assertDoesNotThrow(() -> new SqlServerPreMigrationGuard().verify(dataSource(true, false)));
    }

    @Test
    void shouldRejectReadCommittedSnapshotOn() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> new SqlServerPreMigrationGuard().verify(dataSource(true, true)));

        assertEquals(
                "SQL Server READ_COMMITTED_SNAPSHOT must be OFF for the V19 work queue",
                exception.getMessage());
    }

    @Test
    void shouldRejectMissingDatabaseState() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> new SqlServerPreMigrationGuard().verify(dataSource(false, false)));

        assertEquals(
                "Unable to verify SQL Server READ_COMMITTED_SNAPSHOT mode", exception.getMessage());
    }

    private DataSource dataSource(boolean hasRow, boolean rcsi) {
        AtomicBoolean first = new AtomicBoolean(true);
        ResultSet result =
                proxy(
                        ResultSet.class,
                        (methodName) ->
                                switch (methodName) {
                                    case "next" -> hasRow && first.getAndSet(false);
                                    case "getBoolean" -> rcsi;
                                    default -> null;
                                });
        PreparedStatement statement =
                proxy(
                        PreparedStatement.class,
                        (methodName) -> "executeQuery".equals(methodName) ? result : null);
        Connection connection =
                proxy(
                        Connection.class,
                        (methodName) -> "prepareStatement".equals(methodName) ? statement : null);
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() {
                return connection;
            }

            @Override
            public Connection getConnection(String username, String password) {
                return connection;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, MethodResult result) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            Object value = result.value(method.getName());
                            if (value != null || method.getReturnType() == Void.TYPE) {
                                return value;
                            }
                            if (!method.getReturnType().isPrimitive()) {
                                return null;
                            }
                            if (method.getReturnType() == Boolean.TYPE) {
                                return false;
                            }
                            return 0;
                        });
    }

    @FunctionalInterface
    private interface MethodResult {
        Object value(String methodName);
    }
}
