package com.ifelio.ijob.plugin.etl.loader.database;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A stable unique key is required before acknowledging replayable messages. */
final class MessageTargetKeys {
    private MessageTargetKeys() {}

    static void verify(Connection connection, ResultSetMetaData columns, List<String> keys)
            throws SQLException {
        String table = columns.getTableName(1);
        String schema = empty(columns.getSchemaName(1));
        String catalog = empty(columns.getCatalogName(1));
        if (table == null || table.isBlank())
            throw new SQLException("Message target table metadata unavailable");
        Set<String> expected = new HashSet<>(keys);
        Set<String> primary = new HashSet<>();
        var metadata = connection.getMetaData();
        try (var result = metadata.getPrimaryKeys(catalog, schema, table)) {
            while (result.next()) primary.add(result.getString("COLUMN_NAME"));
        }
        if (!expected.isEmpty() && expected.equals(primary)) return;
        Map<String, Set<String>> indexes = new HashMap<>();
        Set<String> invalid = new HashSet<>();
        try (var result = metadata.getIndexInfo(catalog, schema, table, true, false)) {
            while (result.next()) {
                String name = result.getString("INDEX_NAME");
                if (name == null) continue;
                String column = result.getString("COLUMN_NAME");
                String filter = result.getString("FILTER_CONDITION");
                if (column == null
                        || result.getBoolean("NON_UNIQUE")
                        || filter != null && !filter.isBlank()) invalid.add(name);
                indexes.computeIfAbsent(name, ignored -> new HashSet<>()).add(column);
            }
        }
        for (var entry : indexes.entrySet())
            if (!invalid.contains(entry.getKey()) && expected.equals(entry.getValue())) return;
        throw new SQLException(
                "Message target logical keys must match a primary key or unfiltered unique index");
    }

    private static String empty(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
