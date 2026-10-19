// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.batchetl.metadata;

import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfig;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class JdbcMetadataInspector {
    public BatchTablePlan inspect(
            Connection sourceConnection,
            Connection targetConnection,
            BatchEtlTaskConfig config,
            String sourceTable,
            String targetTable)
            throws SQLException {
        DatabaseMetaData sourceMetadata = sourceConnection.getMetaData();
        DatabaseMetaData targetMetadata = targetConnection.getMetaData();
        validateTargetName(targetMetadata, targetTable);
        JdbcTable source = columns(sourceMetadata, config.getSourceSchema(), sourceTable);
        JdbcTable target = columns(targetMetadata, config.getTargetSchema(), targetTable);
        require(!source.columns().isEmpty(), "Source table was not found: " + sourceTable);
        require(!target.columns().isEmpty(), "Target table was not found: " + targetTable);
        Map<String, JdbcColumn> sourceByName = index(source.columns());
        List<BatchTablePlan.ColumnMapping> mappings = new ArrayList<>();
        for (JdbcColumn targetColumn : target.columns()) {
            JdbcColumn sourceColumn = sourceByName.get(canonical(targetColumn.name()));
            if (sourceColumn != null && targetColumn.writable()) {
                mappings.add(mapping(sourceColumn, targetColumn));
            } else if (sourceColumn == null
                    && targetColumn.required()
                    && config.getLoadMode() != BatchEtlTaskConfig.LoadMode.UPDATE_ONLY) {
                throw invalid(
                        "Target required column has no source column: "
                                + targetTable
                                + "."
                                + targetColumn.name());
            }
        }
        require(
                !mappings.isEmpty(),
                "No writable same-name columns for: " + sourceTable + " -> " + targetTable);
        Map<String, BatchTablePlan.ColumnMapping> mappingBySource = new HashMap<>();
        mappings.forEach(
                mapping -> mappingBySource.put(canonical(mapping.sourceColumn()), mapping));
        List<BatchTablePlan.ColumnMapping> keys = new ArrayList<>();
        for (String key : config.getLogicalKeys()) {
            BatchTablePlan.ColumnMapping mapping = mappingBySource.get(canonical(key));
            require(
                    mapping != null,
                    "Logical key must exist and be writable "
                            + "in both tables: "
                            + sourceTable
                            + "."
                            + key);
            keys.add(mapping);
        }
        BatchTablePlan.ColumnMapping watermark = null;
        if (config.getExtractMode() == BatchEtlTaskConfig.ExtractMode.INCREMENTAL) {
            watermark =
                    mappingBySource.get(canonical(config.getIncremental().getWatermarkColumn()));
            require(
                    watermark != null,
                    "Watermark column must exist and be writable in both "
                            + "tables: "
                            + sourceTable
                            + "."
                            + config.getIncremental().getWatermarkColumn());
        }
        return new BatchTablePlan(
                source.schema(),
                source.table(),
                target.schema(),
                target.table(),
                List.copyOf(mappings),
                List.copyOf(keys),
                watermark);
    }

    private JdbcTable columns(DatabaseMetaData metadata, String schema, String table)
            throws SQLException {
        JdbcTable result = readColumns(metadata, null, schema, table);
        String foldedSchema = fold(metadata, schema);
        String foldedTable = fold(metadata, table);
        if (result.columns().isEmpty()
                && (!equals(schema, foldedSchema) || !equals(table, foldedTable))) {
            result = readColumns(metadata, null, foldedSchema, foldedTable);
        }
        if (result.columns().isEmpty() && schema != null && !schema.isBlank()) {
            result = readColumns(metadata, schema, null, table);
        }
        if (result.columns().isEmpty() && schema != null && !schema.isBlank()) {
            result = readColumns(metadata, foldedSchema, null, foldedTable);
        }
        return result;
    }

    private JdbcTable readColumns(
            DatabaseMetaData metadata, String catalog, String schema, String table)
            throws SQLException {
        List<JdbcColumn> result = new ArrayList<>();
        String actualSchema = schema == null ? catalog : schema;
        String actualTable = table;
        String firstIdentity = null;
        try (ResultSet rows = metadata.getColumns(catalog, schema, table, null)) {
            while (rows.next()) {
                String rowSchema = safeString(rows, "TABLE_SCHEM");
                String rowCatalog = safeString(rows, "TABLE_CAT");
                String rowTable = safeString(rows, "TABLE_NAME");
                // JDBC treats underscores as wildcards. Match only the requested identity;
                // identifier case folding is handled by the separate fallback query.
                if (rowTable != null && !table.equals(rowTable)) continue;
                if (schema != null
                        && !schema.isBlank()
                        && rowSchema != null
                        && !schema.equals(rowSchema)) continue;
                if (catalog != null
                        && !catalog.isBlank()
                        && rowCatalog != null
                        && !catalog.equals(rowCatalog)) continue;
                if (rowSchema != null && !rowSchema.isBlank()) {
                    actualSchema = rowSchema;
                } else if (rowCatalog != null && !rowCatalog.isBlank()) {
                    actualSchema = rowCatalog;
                }
                if (rowTable != null && !rowTable.isBlank()) {
                    actualTable = rowTable;
                }
                String identity = (actualSchema == null ? "" : actualSchema) + "." + actualTable;
                if (firstIdentity == null) {
                    firstIdentity = identity;
                } else if (!firstIdentity.equals(identity)) {
                    throw invalid("Table name is ambiguous; specify schema: " + table);
                }
                result.add(
                        new JdbcColumn(
                                rows.getString("COLUMN_NAME"),
                                rows.getInt("DATA_TYPE"),
                                rows.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                                rows.getString("COLUMN_DEF") != null,
                                "YES".equalsIgnoreCase(safeString(rows, "IS_AUTOINCREMENT")),
                                "YES".equalsIgnoreCase(safeString(rows, "IS_GENERATEDCOLUMN")),
                                rows.getInt("ORDINAL_POSITION")));
            }
        }
        result.sort(Comparator.comparingInt(JdbcColumn::ordinal));
        return new JdbcTable(actualSchema, actualTable, result);
    }

    private String fold(DatabaseMetaData metadata, String value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (metadata.storesUpperCaseIdentifiers()) {
            return value.toUpperCase(Locale.ROOT);
        }
        if (metadata.storesLowerCaseIdentifiers()) {
            return value.toLowerCase(Locale.ROOT);
        }
        return value;
    }

    private boolean equals(String left, String right) {
        return java.util.Objects.equals(left, right);
    }

    private String safeString(ResultSet rows, String column) {
        try {
            return rows.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private Map<String, JdbcColumn> index(List<JdbcColumn> columns) {
        Map<String, JdbcColumn> result = new LinkedHashMap<>();
        for (JdbcColumn column : columns) {
            String key = canonical(column.name());
            if (result.put(key, column) != null) {
                throw invalid("Table contains ambiguous column names: " + column.name());
            }
        }
        return result;
    }

    private BatchTablePlan.ColumnMapping mapping(JdbcColumn source, JdbcColumn target) {
        require(
                compatible(source.jdbcType(), target.jdbcType()),
                "Source and target column types are incompatible: "
                        + source.name()
                        + " -> "
                        + target.name());
        return new BatchTablePlan.ColumnMapping(
                source.name(), target.name(),
                source.jdbcType(), target.jdbcType());
    }

    private boolean compatible(int source, int target) {
        ColumnFamily sourceFamily = family(source);
        ColumnFamily targetFamily = family(target);
        return sourceFamily == targetFamily
                && (sourceFamily != ColumnFamily.OTHER || source == target);
    }

    private ColumnFamily family(int type) {
        return switch (type) {
            case Types.TINYINT,
                            Types.SMALLINT,
                            Types.INTEGER,
                            Types.BIGINT,
                            Types.NUMERIC,
                            Types.DECIMAL,
                            Types.FLOAT,
                            Types.REAL,
                            Types.DOUBLE ->
                    ColumnFamily.NUMERIC;
            case Types.CHAR,
                            Types.VARCHAR,
                            Types.LONGVARCHAR,
                            Types.NCHAR,
                            Types.NVARCHAR,
                            Types.LONGNVARCHAR,
                            Types.CLOB,
                            Types.NCLOB,
                            Types.SQLXML ->
                    ColumnFamily.TEXT;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB ->
                    ColumnFamily.BINARY;
            case Types.DATE,
                            Types.TIME,
                            Types.TIME_WITH_TIMEZONE,
                            Types.TIMESTAMP,
                            Types.TIMESTAMP_WITH_TIMEZONE ->
                    ColumnFamily.TEMPORAL;
            case Types.BOOLEAN, Types.BIT -> ColumnFamily.BOOLEAN;
            default -> ColumnFamily.OTHER;
        };
    }

    private void validateTargetName(DatabaseMetaData metadata, String table) throws SQLException {
        int maximum = metadata.getMaxTableNameLength();
        require(
                maximum <= 0 || table.length() <= maximum,
                "Target table name exceeds database limit: " + table);
    }

    private String canonical(String value) {
        return value.toUpperCase(Locale.ROOT);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private record JdbcColumn(
            String name,
            int jdbcType,
            boolean nullable,
            boolean hasDefault,
            boolean autoIncrement,
            boolean generated,
            int ordinal) {
        private boolean writable() {
            return !autoIncrement && !generated;
        }

        private boolean required() {
            return writable() && !nullable && !hasDefault;
        }
    }

    private record JdbcTable(String schema, String table, List<JdbcColumn> columns) {}

    private enum ColumnFamily {
        NUMERIC,
        TEXT,
        BINARY,
        TEMPORAL,
        BOOLEAN,
        OTHER
    }
}
