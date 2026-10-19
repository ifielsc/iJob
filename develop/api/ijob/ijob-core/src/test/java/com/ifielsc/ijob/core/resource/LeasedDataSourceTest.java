// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

class LeasedDataSourceTest {
    @Test
    void facadeMustAcquireAtConnectionTimeAndRetainUntilConnectionClose() throws Exception {
        AtomicInteger acquired = new AtomicInteger();
        AtomicInteger released = new AtomicInteger();
        AtomicInteger connectionCloses = new AtomicInteger();
        Connection raw =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("close")) {
                                        connectionCloses.incrementAndGet();
                                        return null;
                                    }
                                    if (method.getName().equals("isClosed")) return false;
                                    throw new UnsupportedOperationException(method.getName());
                                });
        DataSource target =
                new AbstractDataSource() {
                    public Connection getConnection() {
                        return raw;
                    }

                    public Connection getConnection(String username, String password) {
                        return raw;
                    }
                };
        DataSource facade =
                new LeasedDataSource(
                        () -> {
                            acquired.incrementAndGet();
                            return new ResourceClientLease<>(target, released::incrementAndGet);
                        });
        assertEquals(0, acquired.get());
        Connection connection = facade.getConnection();
        assertEquals(1, acquired.get());
        assertEquals(0, released.get());
        assertSame(connection, connection.unwrap(Connection.class));
        connection.close();
        connection.close();
        assertTrue(connection.isClosed());
        assertEquals(1, connectionCloses.get());
        assertEquals(1, released.get());
    }

    @Test
    void failedConnectionAcquisitionMustReleaseLease() {
        AtomicInteger released = new AtomicInteger();
        DataSource target =
                new AbstractDataSource() {
                    public Connection getConnection() throws SQLException {
                        throw new SQLException("synthetic");
                    }

                    public Connection getConnection(String username, String password)
                            throws SQLException {
                        return getConnection();
                    }
                };
        DataSource facade =
                new LeasedDataSource(
                        () -> new ResourceClientLease<>(target, released::incrementAndGet));
        assertThrows(SQLException.class, facade::getConnection);
        assertEquals(1, released.get());
    }
}
