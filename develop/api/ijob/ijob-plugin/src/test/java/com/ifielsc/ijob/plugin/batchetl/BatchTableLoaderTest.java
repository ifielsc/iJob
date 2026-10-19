// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.batchetl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifielsc.ijob.plugin.batchetl.metadata.BatchTablePlan;
import com.ifielsc.ijob.plugin.common.jdbc.CancelableJdbcResources;
import com.ifielsc.ijob.plugin.common.jdbc.JdbcIdentifierQuoter;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class BatchTableLoaderTest {
    @Test
    void shouldUpsertInBatchesAndKeepTargetForNullSource() throws Exception {
        MemoryTable target = new MemoryTable();
        target.rows.add(row(1L, "old", "old-note"));
        Connection connection = target.connection();
        BatchEtlTaskConfig config = new BatchEtlTaskConfig();
        config.setLoadMode(BatchEtlTaskConfig.LoadMode.UPSERT);
        config.setNullPolicy(BatchEtlTaskConfig.NullPolicy.KEEP_TARGET);
        BatchTableLoader loader =
                new BatchTableLoader(
                        connection,
                        new CancelableJdbcResources(),
                        JdbcIdentifierQuoter.from(connection.getMetaData()),
                        plan(),
                        config);

        BatchTableLoader.LoadCounts counts =
                loader.write(
                        List.of(
                                new Object[] {1L, null, "changed"},
                                new Object[] {2L, "new", "new-note"}));

        assertEquals(new BatchTableLoader.LoadCounts(1, 1, 0), counts);
        assertEquals(List.of(row(1L, "old", "changed"), row(2L, "new", "new-note")), target.rows);
    }

    @Test
    void shouldSkipExistingRowsInInsertOnlyMode() throws Exception {
        MemoryTable target = new MemoryTable();
        target.rows.add(row(1L, "old", "old-note"));
        Connection connection = target.connection();
        BatchEtlTaskConfig config = new BatchEtlTaskConfig();
        config.setLoadMode(BatchEtlTaskConfig.LoadMode.INSERT_ONLY);
        BatchTableLoader loader =
                new BatchTableLoader(
                        connection,
                        new CancelableJdbcResources(),
                        JdbcIdentifierQuoter.from(connection.getMetaData()),
                        plan(),
                        config);

        BatchTableLoader.LoadCounts counts =
                loader.write(List.<Object[]>of(new Object[] {1L, "ignored", "ignored"}));

        assertEquals(new BatchTableLoader.LoadCounts(0, 0, 1), counts);
        assertEquals(List.of(row(1L, "old", "old-note")), target.rows);
    }

    private BatchTablePlan plan() {
        BatchTablePlan.ColumnMapping id = mapping("id", Types.BIGINT);
        BatchTablePlan.ColumnMapping name = mapping("name", Types.VARCHAR);
        BatchTablePlan.ColumnMapping note = mapping("note", Types.VARCHAR);
        return new BatchTablePlan(
                "source",
                "source_table",
                "target",
                "target_table",
                List.of(id, name, note),
                List.of(id),
                null);
    }

    private BatchTablePlan.ColumnMapping mapping(String name, int type) {
        return new BatchTablePlan.ColumnMapping(name, name, type, type);
    }

    private Map<String, Object> row(long id, String name, String note) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("note", note);
        return row;
    }

    private final class MemoryTable {
        private final List<Map<String, Object>> rows = new ArrayList<>();

        private Connection connection() {
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
                                        default -> defaultValue(method.getReturnType());
                                    });
        }

        private PreparedStatement statement(String sql) {
            Map<Integer, Object> parameters = new HashMap<>();
            List<List<Object>> batches = new ArrayList<>();
            return (PreparedStatement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {PreparedStatement.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "setObject" -> {
                                            parameters.put((Integer) args[0], args[1]);
                                            yield null;
                                        }
                                        case "addBatch" -> {
                                            batches.add(values(parameters));
                                            yield null;
                                        }
                                        case "executeQuery" -> existing(parameters);
                                        case "executeBatch" -> execute(sql, batches);
                                        default -> defaultValue(method.getReturnType());
                                    });
        }

        private ResultSet existing(Map<Integer, Object> parameters) {
            List<List<Object>> found = new ArrayList<>();
            for (Object id : parameters.values()) {
                if (find(id) != null
                        && found.stream().noneMatch(row -> row.getFirst().equals(id))) {
                    found.add(List.of(id));
                }
            }
            int[] cursor = {-1};
            return (ResultSet)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "next" -> ++cursor[0] < found.size();
                                        case "getObject" ->
                                                found.get(cursor[0]).get((Integer) args[0] - 1);
                                        default -> defaultValue(method.getReturnType());
                                    });
        }

        private int[] execute(String sql, List<List<Object>> batches) {
            int[] result = new int[batches.size()];
            for (int index = 0; index < batches.size(); index++) {
                List<Object> values = batches.get(index);
                if (sql.startsWith("INSERT")) {
                    rows.add(
                            row(
                                    ((Number) values.get(0)).longValue(),
                                    (String) values.get(1),
                                    (String) values.get(2)));
                    result[index] = 1;
                } else {
                    List<String> columns = updateColumns(sql);
                    Object id = values.getLast();
                    Map<String, Object> target = find(id);
                    if (target == null) {
                        result[index] = 0;
                    } else {
                        for (int column = 0; column < columns.size(); column++) {
                            target.put(columns.get(column), values.get(column));
                        }
                        result[index] = 1;
                    }
                }
            }
            return result;
        }

        private List<String> updateColumns(String sql) {
            String set = sql.substring(sql.indexOf(" SET ") + 5, sql.indexOf(" WHERE "));
            List<String> result = new ArrayList<>();
            for (String assignment : set.split(",")) {
                result.add(
                        assignment.substring(
                                assignment.indexOf('`') + 1, assignment.lastIndexOf('`')));
            }
            return result;
        }

        private Map<String, Object> find(Object id) {
            return rows.stream()
                    .filter(
                            row ->
                                    ((Number) row.get("id")).longValue()
                                            == ((Number) id).longValue())
                    .findFirst()
                    .orElse(null);
        }

        private List<Object> values(Map<Integer, Object> parameters) {
            List<Object> result = new ArrayList<>();
            for (int index = 1; index <= parameters.size(); index++) {
                result.add(parameters.get(index));
            }
            return result;
        }
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
