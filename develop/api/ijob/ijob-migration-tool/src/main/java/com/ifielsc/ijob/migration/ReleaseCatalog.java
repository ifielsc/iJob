package com.ifielsc.ijob.migration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ReleaseCatalog {
    static final int LOGICAL_VERSION = 38;
    static final String FORMAT = "ijob-release-v1-from-v38";
    static final List<String> TABLES;

    static {
        var tables = new ArrayList<>(SchemaCatalog.TABLES);
        tables.removeAll(List.of("ifj_job_node", "ifj_job_edge", "ifj_job_node_policy"));
        tables.addAll(
                List.of(
                        "ifj_etl_file_record",
                        "ifj_mail_channel",
                        "ifj_parameter_template",
                        "ifj_parameter_binding",
                        "ifj_notify_rate_limit"));
        tables.sort(String::compareTo);
        TABLES = List.copyOf(tables);
    }

    private ReleaseCatalog() {}

    static void verifyEmptySeeds(Connection connection, String table, String sqlName)
            throws SQLException {
        try (var resource =
                ReleaseCatalog.class.getResourceAsStream("/release-history/baseline-seeds.json")) {
            var seed = new ObjectMapper().readTree(resource).get(table);
            var columns = new ArrayList<String>();
            for (var column : seed.get("columns")) columns.add(column.asText());
            try (var statement = connection.createStatement();
                    var rows =
                            statement.executeQuery(
                                    "SELECT "
                                            + String.join(",", columns)
                                            + " FROM "
                                            + sqlName
                                            + " ORDER BY "
                                            + columns.getFirst())) {
                for (var expected : seed.get("rows")) {
                    if (!rows.next()) throw new IllegalStateException("Missing baseline seed");
                    for (int index = 0; index < columns.size(); index++) {
                        if (columns.get(index).endsWith("_time")) continue;
                        String actual =
                                columns.get(index).equals("enabled")
                                        ? (rows.getBoolean(index + 1) ? "1" : "0")
                                        : rows.getString(index + 1);
                        if (!java.util.Objects.equals(
                                expected.get(index).isNull() ? null : expected.get(index).asText(),
                                actual)) {
                            throw new IllegalStateException(
                                    "Target contains modified baseline seeds: " + table);
                        }
                    }
                }
                if (rows.next()) throw new IllegalStateException("Unexpected baseline seed");
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String vendor(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (product.contains("mysql")) return "mysql";
        if (product.contains("postgresql")) return "postgresql";
        if (product.contains("sql server")) return "sqlserver";
        if (product.contains("oracle")) return "oracle";
        if (product.contains("sqlite")) return "sqlite";
        throw new IllegalArgumentException("Unsupported database product");
    }

    static void verifyHistory(Connection connection, String history, boolean target)
            throws SQLException {
        String quote = connection.getMetaData().getIdentifierQuoteString().trim();
        String sql =
                "SELECT "
                        + quote
                        + "version"
                        + quote
                        + ", "
                        + quote
                        + "script"
                        + quote
                        + ", "
                        + quote
                        + "checksum"
                        + quote
                        + ", "
                        + quote
                        + "success"
                        + quote
                        + " FROM "
                        + history
                        + " WHERE "
                        + quote
                        + "version"
                        + quote
                        + " IS NOT NULL";
        try (var resource =
                ReleaseCatalog.class.getResourceAsStream(
                        "/release-history/"
                                + (target ? "release-" : "legacy-")
                                + vendor(connection)
                                + ".json")) {
            if (resource == null)
                throw new IllegalStateException("Missing frozen migration history");
            var expected = new ObjectMapper().readTree(resource);
            var found = new java.util.HashSet<String>();
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery(sql)) {
                while (rows.next()) {
                    String version = rows.getString(1);
                    var entry = expected.get(version);
                    int checksum = rows.getInt(3);
                    boolean nullChecksum = rows.wasNull();
                    if (entry == null
                            || !found.add(version)
                            || !rows.getBoolean(4)
                            || nullChecksum
                            || !entry.get("script").asText().equals(rows.getString(2))
                            || entry.get("checksum").asInt() != checksum) {
                        throw new IllegalStateException(
                                "Migration history does not match the frozen "
                                        + (target ? "release V1" : "development V38")
                                        + " lineage");
                    }
                }
            }
            if (found.size() != expected.size())
                throw new IllegalStateException("Incomplete migration history");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read frozen migration history", exception);
        }
    }

    static void verifyIdSegments(
            Connection connection, java.util.function.Function<String, String> tableName)
            throws SQLException {
        var expected = new java.util.HashSet<String>();
        try (var resource =
                ReleaseCatalog.class.getResourceAsStream("/release-history/id-segments.json")) {
            if (resource == null) throw new IllegalStateException("Missing ID namespace catalog");
            var entries = new ObjectMapper().readTree(resource);
            for (var entry : entries) expected.add(entry.asText());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        try (var statement = connection.createStatement();
                var rows =
                        statement.executeQuery(
                                "SELECT segment_key, next_value, segment_size FROM "
                                        + tableName.apply("ifj_id_segment"))) {
            var found = new java.util.HashSet<String>();
            while (rows.next()) {
                String key = rows.getString(1);
                long next = rows.getLong(2);
                if (rows.wasNull()
                        || !expected.contains(key)
                        || !found.add(key)
                        || rows.getInt(3) < 1
                        || rows.getInt(3) > 100000)
                    throw new IllegalStateException("Invalid ID namespace seed");
                if (key.equals("platform-primary-id")) {
                    if (next != Long.MAX_VALUE)
                        throw new IllegalStateException("Retired allocator must remain exhausted");
                } else {
                    String table = key.substring(3);
                    if (!TABLES.contains(table) || next < 1 || next == Long.MAX_VALUE)
                        throw new IllegalStateException("Invalid ID high watermark");
                    try (var maximum = connection.createStatement();
                            var values =
                                    maximum.executeQuery(
                                            "SELECT MAX(id) FROM " + tableName.apply(table))) {
                        values.next();
                        if (next <= values.getLong(1))
                            throw new IllegalStateException(
                                    "ID namespace is behind existing records: " + table);
                    }
                }
            }
            if (!found.equals(expected))
                throw new IllegalStateException("Incomplete ID namespaces");
        }
    }
}
