// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.plugin.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.cluster.ClusterStateStore;
import com.ifielsc.ijob.contract.resource.SqlPreviewDTO;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.resource.ResourceResolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

class SqlPreviewServiceTest {
    @TempDir Path directory;
    private final Map<String, String> states = new ConcurrentHashMap<>();

    private ClusterStateStore store() {
        return (ClusterStateStore)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {ClusterStateStore.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "get" -> states.get(args[0]);
                                    case "set", "update" -> {
                                        states.put((String) args[0], (String) args[1]);
                                        yield null;
                                    }
                                    case "delete" -> {
                                        states.remove(args[0]);
                                        yield null;
                                    }
                                    case "initializeOrRead" ->
                                            states.computeIfAbsent(
                                                    (String) args[0], k -> (String) args[1]);
                                    case "updateTimeout" -> null;
                                    default -> throw new AssertionError(method.getName());
                                });
    }

    private SqlPreviewService service() throws Exception {
        return service(false);
    }

    private SqlPreviewService service(boolean strictScalarStreams) throws Exception {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + directory.resolve("preview.db"));
        try (var conn = ds.getConnection();
                var statement = conn.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS sample (id INTEGER, name TEXT)");
        }
        DataSource source = strictScalarStreams ? strictJdbc(DataSource.class, ds) : ds;
        return new SqlPreviewService(
                new ResourceResolver(null) {
                    @Override
                    public DataSource getDataSource(Long id) {
                        return source;
                    }
                },
                new VariableResolver(),
                store(),
                new ObjectMapper());
    }

    private <T> T strictJdbc(Class<T> type, T delegate) {
        return type.cast(
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            if (delegate instanceof java.sql.ResultSet rs
                                    && method.getName().equals("getCharacterStream")) {
                                int columnType = rs.getMetaData().getColumnType((int) args[0]);
                                if (java.util.Set.of(
                                                java.sql.Types.INTEGER,
                                                java.sql.Types.BIGINT,
                                                java.sql.Types.NUMERIC,
                                                java.sql.Types.DECIMAL,
                                                java.sql.Types.DOUBLE)
                                        .contains(columnType))
                                    throw new java.sql.SQLDataException(
                                            "Driver cannot stream a scalar", "22018");
                            }
                            Object result;
                            try {
                                result = method.invoke(delegate, args);
                            } catch (java.lang.reflect.InvocationTargetException e) {
                                throw e.getCause();
                            }
                            if (result instanceof java.sql.Connection connection)
                                return strictJdbc(java.sql.Connection.class, connection);
                            if (result instanceof java.sql.PreparedStatement statement)
                                return strictJdbc(java.sql.PreparedStatement.class, statement);
                            if (method.getReturnType() == java.sql.ResultSet.class
                                    && result instanceof java.sql.ResultSet rs)
                                return strictJdbc(java.sql.ResultSet.class, rs);
                            return result;
                        }));
    }

    @Test
    void exposesRedactedDatabaseDiagnosticWithoutLosingTheVendorCode() throws Exception {
        var service = service();
        try {
            var result =
                    service.query(request("SELECT missing_col FROM sample", "SQL", 200), "one");
            assertTrue(result.databaseMessage().contains("missing_col"));
            assertTrue(result.vendorCode() != null);
            String sample = "private_" + UUID.randomUUID().toString().replace("-", "");
            var sensitive =
                    service.query(
                            new SqlPreviewDTO.Request(
                                    UUID.randomUUID().toString(),
                                    1L,
                                    "SELECT ${value} FROM sample",
                                    "SQL",
                                    Map.of("value", sample),
                                    Map.of(),
                                    200),
                            "one");
            assertFalse(sensitive.databaseMessage().contains(sample));
            assertTrue(sensitive.databaseMessage().contains("[REDACTED]"));
        } finally {
            service.close();
        }
    }

    @Test
    void connectionFailuresDoNotExposeUntrustedConnectionDetails() {
        DataSource source =
                (DataSource)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {DataSource.class},
                                (proxy, method, args) -> {
                                    throw new java.sql.SQLException(
                                            "Unlabelled private connection identity", "08001");
                                });
        var service =
                new SqlPreviewService(
                        new ResourceResolver(null) {
                            @Override
                            public DataSource getDataSource(Long id) {
                                return source;
                            }
                        },
                        new VariableResolver(),
                        store(),
                        new ObjectMapper());
        try {
            var result = service.query(request("SELECT 1", "SQL", 200), "one");
            assertEquals("08001", result.sqlState());
            assertEquals(null, result.databaseMessage());
        } finally {
            service.close();
        }
    }

    @Test
    void readsExactScalarsWhenTheDriverDoesNotSupportCharacterStreamsForNumbers() throws Exception {
        var service = service(true);
        try {
            var result =
                    service.query(
                            request(
                                    "SELECT 9007199254740993, CAST(12.50 AS NUMERIC), 'text'",
                                    "SQL",
                                    200),
                            "one");
            assertEquals("SUCCESS", result.status());
            assertEquals(
                    java.util.List.of("9007199254740993", "12.5", "text"),
                    result.rows().getFirst());
        } finally {
            service.close();
        }
    }

    private SqlPreviewDTO.Request request(String sql, String mode, int limit) {
        return new SqlPreviewDTO.Request(
                UUID.randomUUID().toString(), 1L, sql, mode, Map.of(), Map.of(), limit);
    }

    @Test
    void readsMultipleRowsAndPreservesNullAndExactNumbers() throws Exception {
        var service = service();
        try {
            var result =
                    service.query(
                            request(
                                    "SELECT 9007199254740993 AS id, NULL AS missing UNION ALL"
                                            + " SELECT 2, '中文'",
                                    "SQL",
                                    200),
                            "one");
            assertEquals("SUCCESS", result.status());
            assertEquals(2, result.rows().size());
            assertEquals("9007199254740993", result.rows().getFirst().getFirst());
            assertEquals(null, result.rows().getFirst().get(1));
            assertEquals("中文", result.rows().get(1).get(1));
            assertTrue(states.isEmpty());
        } finally {
            service.close();
        }
    }

    @Test
    void deniesWritesMultipleStatementsAndExportBeforeOpeningConnection() throws Exception {
        var service = service();
        try {
            for (String sql :
                    new String[] {
                        "INSERT INTO sample VALUES (1,'x')",
                        "SELECT 1; DELETE FROM sample",
                        "WITH changed AS (DELETE FROM sample RETURNING *) SELECT * FROM changed",
                        "SELECT * FROM sample FOR UPDATE",
                        "SELECT 1 INTO OUTFILE '/x'",
                        "SELECT 1 /*! INTO OUTFILE '/x' */"
                    })
                assertEquals(
                        "READ_ONLY", service.query(request(sql, "PUSH", 10), "one").status(), sql);
            assertEquals(
                    "SUCCESS",
                    service.query(
                                    request(
                                            "WITH x AS (SELECT 1 AS id) SELECT id FROM x;",
                                            "SQL",
                                            10),
                                    "one")
                            .status());
        } finally {
            service.close();
        }
    }

    @Test
    void bindsEtlValuesAndInterpolatesExistingVariables() throws Exception {
        var service = service();
        try {
            var request =
                    new SqlPreviewDTO.Request(
                            UUID.randomUUID().toString(),
                            1L,
                            "SELECT :value AS name, ${ijob.instance.rootId} AS id",
                            "ETL",
                            Map.of("ijob.instance.rootId", 6001),
                            Map.of("value", "x'; DELETE FROM sample;--"),
                            10);
            var result = service.query(request, "one");
            assertEquals("SUCCESS", result.status());
            assertEquals("x'; DELETE FROM sample;--", result.rows().getFirst().getFirst());
            assertEquals("6001", result.rows().getFirst().get(1));
            assertEquals(
                    "PARAMETERS",
                    service.query(request("SELECT :missing", "ETL", 10), "one").status());
        } finally {
            service.close();
        }
    }

    @Test
    void reportsLimitsEmptyResultsAndDoesNotLeakSqlErrors() throws Exception {
        var service = service();
        try {
            var limited =
                    service.query(
                            request("SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3", "SQL", 2),
                            "one");
            assertEquals("SUCCESS", limited.status());
            assertEquals("ROWS", limited.truncated());
            assertEquals(2, limited.rows().size());
            var empty = service.query(request("SELECT id FROM sample", "SQL", 2), "one");
            assertEquals(1, empty.columns().size());
            assertTrue(empty.rows().isEmpty());
            var failure =
                    service.query(
                            request("SELECT private_customer_secret FROM absent_table", "SQL", 2),
                            "one");
            assertFalse(
                    new ObjectMapper()
                            .writeValueAsString(failure)
                            .contains("private_customer_secret"));
        } finally {
            service.close();
        }
    }

    @Test
    void boundsUnicodeCellsAndTotalResultBytes() throws Exception {
        var service = service();
        try {
            String large = "中😀".repeat(4000);
            var request =
                    new SqlPreviewDTO.Request(
                            UUID.randomUUID().toString(),
                            1L,
                            "WITH RECURSIVE x(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM x WHERE"
                                    + " n<200) SELECT :text FROM x",
                            "ETL",
                            Map.of(),
                            Map.of("text", large),
                            200);
            var result = service.query(request, "one");
            assertEquals("SUCCESS", result.status());
            assertEquals("BYTES", result.truncated());
            assertTrue(result.rows().size() < 200);
            String cell = result.rows().getFirst().getFirst();
            assertTrue(cell.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 16384);
            assertTrue(cell.endsWith("…"));
            assertFalse(Character.isHighSurrogate(cell.charAt(cell.length() - 2)));
            assertTrue(new ObjectMapper().writeValueAsBytes(result).length <= 2 * 1024 * 1024);
        } finally {
            service.close();
        }
    }

    @Test
    void cancellationAcrossServiceInstancesIsActorScoped() throws Exception {
        var first = service();
        var second = service();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var request =
                    request(
                            "WITH RECURSIVE x(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM x WHERE"
                                    + " n<1000000000) SELECT sum(n) FROM x",
                            "SQL",
                            2);
            var query = worker.submit(() -> first.query(request, "one"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (states.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
            second.cancel(request.requestId(), "two");
            Thread.sleep(300);
            assertFalse(query.isDone());
            second.cancel(request.requestId(), "one");
            assertEquals("CANCELLED", query.get(5, TimeUnit.SECONDS).status());
        } finally {
            first.close();
            second.close();
        }
    }
}
