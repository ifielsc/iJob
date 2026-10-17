package com.ifelio.ijob.plugin.common.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicInteger;

class JdbcStreamingTest {
    @Test
    void shouldUseMysqlStreamingSentinel() throws Exception {
        AtomicInteger fetchSize = new AtomicInteger();

        JdbcStreaming.configureFetch(statement(fetchSize), metadata("MySQL"), 2000);

        assertEquals(Integer.MIN_VALUE, fetchSize.get());
    }

    @Test
    void shouldKeepConfiguredFetchSizeForOtherDatabases() throws Exception {
        AtomicInteger fetchSize = new AtomicInteger();

        JdbcStreaming.configureFetch(statement(fetchSize), metadata("PostgreSQL"), 2000);

        assertEquals(2000, fetchSize.get());
    }

    private DatabaseMetaData metadata(String product) {
        return (DatabaseMetaData)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {DatabaseMetaData.class},
                        (proxy, method, args) ->
                                method.getName().equals("getDatabaseProductName") ? product : null);
    }

    private PreparedStatement statement(AtomicInteger fetchSize) {
        return (PreparedStatement)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {PreparedStatement.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("setFetchSize")) {
                                fetchSize.set((Integer) args[0]);
                            }
                            return null;
                        });
    }
}
