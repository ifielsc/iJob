package com.ifielsc.ijob.migration;

import java.util.List;
import java.util.Map;

record BundleManifest(
        String formatVersion,
        int logicalSchemaVersion,
        String createdAtUtc,
        Source source,
        Map<String, Long> coordinationSnapshot,
        long maximumNumericPrimaryKey,
        List<Table> tables) {

    record Source(String product, String productVersion, String schema) {}

    record Table(
            String name,
            List<Column> columns,
            List<String> primaryKey,
            List<ForeignKey> foreignKeys,
            long rowCount,
            String sha256,
            long normalizedEmptyStrings) {}

    record Column(String name, LogicalType type, boolean nullable) {}

    record ForeignKey(
            List<String> columns, String referencedTable, List<String> referencedColumns) {}

    enum LogicalType {
        TEXT,
        NUMBER,
        BOOLEAN,
        TIMESTAMP,
        DATE,
        TIME,
        BINARY
    }
}
