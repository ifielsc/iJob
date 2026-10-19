// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.loader.database;

import com.ifielsc.ijob.plugin.etl.loader.TargetMappingPlan.Column;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The caller supplies metadata from a zero-row query against the exact qualified target. */
final class TargetColumnMetadata {
    private TargetColumnMetadata() {}

    static List<Column> read(Connection connection, ResultSetMetaData result) throws SQLException {
        DatabaseMetaData database = connection.getMetaData();
        Map<String, Column> described = new LinkedHashMap<>();
        String table = result.getTableName(1);
        if (table == null || table.isBlank())
            throw new SQLException("ETL AUTO mapping requires target table column metadata");
        String schema = result.getSchemaName(1);
        String catalog = result.getCatalogName(1);
        try (ResultSet columns =
                database.getColumns(
                        blankToNull(catalog),
                        pattern(database, blankToNull(schema)),
                        pattern(database, table),
                        null)) {
            while (columns.next()) {
                if (!table.equals(columns.getString("TABLE_NAME"))) continue;
                if (schema != null
                        && !schema.isBlank()
                        && !schema.equals(columns.getString("TABLE_SCHEM"))) continue;
                String name = columns.getString("COLUMN_NAME");
                boolean generated =
                        "YES".equalsIgnoreCase(columns.getString("IS_AUTOINCREMENT"))
                                || "YES".equalsIgnoreCase(columns.getString("IS_GENERATEDCOLUMN"));
                String defaultValue = columns.getString("COLUMN_DEF");
                boolean required =
                        columns.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls
                                && (defaultValue == null
                                        || "NULL".equalsIgnoreCase(defaultValue.trim()))
                                && !generated;
                if (described.putIfAbsent(name, new Column(name, generated, required)) != null)
                    throw new SQLException("ETL target column metadata is ambiguous: " + name);
            }
        }
        List<Column> ordered = new ArrayList<>();
        for (int i = 1; i <= result.getColumnCount(); i++) {
            String name = result.getColumnName(i);
            Column column = described.get(name);
            if (column == null)
                throw new SQLException("ETL target metadata is incomplete: " + name);
            ordered.add(
                    new Column(
                            name,
                            column.generated() || result.isAutoIncrement(i),
                            column.required()));
        }
        return List.copyOf(ordered);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String pattern(DatabaseMetaData database, String value) throws SQLException {
        if (value == null) return null;
        String escape = database.getSearchStringEscape();
        if (escape == null || escape.isEmpty()) return value;
        return value.replace(escape, escape + escape)
                .replace("_", escape + "_")
                .replace("%", escape + "%");
    }
}
