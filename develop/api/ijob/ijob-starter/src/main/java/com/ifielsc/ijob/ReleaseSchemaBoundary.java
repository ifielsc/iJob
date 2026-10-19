package com.ifielsc.ijob;

import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/** Rejects development histories before migration and imported data before worker startup. */
final class ReleaseSchemaBoundary {
    private ReleaseSchemaBoundary() {}

    static void beforeMigration(Flyway flyway, String vendor) {
        var configuration = flyway.getConfiguration();
        var locations = configuration.getLocations();
        if (locations.length != 1
                || !locations[0].getDescriptor().equals("classpath:db/release/" + vendor)) {
            throw new IllegalStateException(
                    "Application startup requires the release migration location");
        }
        if (configuration.isBaselineOnMigrate() || !configuration.isCleanDisabled()) {
            throw new IllegalStateException(
                    "Release migrations require clean disabled and baseline-on-migrate disabled");
        }
        try (var connection = configuration.getDataSource().getConnection()) {
            String schema = configuration.getDefaultSchema();
            if (schema == null) schema = connection.getSchema();
            boolean platformTables = false;
            String history = null;
            var metadata = connection.getMetaData();
            String quote = metadata.getIdentifierQuoteString().trim();
            try (var tables =
                    metadata.getTables(
                            connection.getCatalog(), schema, "%", new String[] {"TABLE"})) {
                while (tables.next()) {
                    String name = tables.getString("TABLE_NAME");
                    if (name.toLowerCase(Locale.ROOT).startsWith("ifj_")) platformTables = true;
                    if (name.equalsIgnoreCase("ifj_schema_history")) {
                        String owner = tables.getString("TABLE_SCHEM");
                        history =
                                (owner == null ? "" : quote + owner + quote + ".")
                                        + quote
                                        + name
                                        + quote;
                    }
                }
            }
            if (history == null) {
                if (platformTables)
                    throw new IllegalStateException(
                            "Existing platform tables have no release history; use the offline"
                                    + " conversion tool");
                return;
            }
            try (var statement = connection.createStatement();
                    var rows =
                            statement.executeQuery(
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
                                            + "success"
                                            + quote
                                            + " FROM "
                                            + history
                                            + " WHERE "
                                            + quote
                                            + "version"
                                            + quote
                                            + " IS NOT NULL")) {
                int count = 0;
                boolean baseline = false;
                while (rows.next()) {
                    count++;
                    String version = rows.getString(1);
                    if ("1".equals(version))
                        baseline = "V1__platform_baseline.sql".equals(rows.getString(2));
                    if (new java.math.BigDecimal(version).compareTo(java.math.BigDecimal.ONE) < 0
                            || !rows.getBoolean(3)) {
                        throw new IllegalStateException(
                                "Unsupported database lineage; development V35/V38 databases"
                                    + " require offline conversion into a new release database");
                    }
                }
                if (count < 1 || !baseline)
                    throw new IllegalStateException("A complete release V1 history is required");
            }
            verifyReady(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Release schema preflight failed", exception);
        }
        Flyway.configure(configuration.getClassLoader())
                .configuration(configuration)
                .ignoreMigrationPatterns("*:pending")
                .load()
                .validate();
    }

    static void verifyReady(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var rows =
                        statement.executeQuery(
                                "SELECT singleton_id, state FROM ifj_release_state")) {
            if (!rows.next()
                    || rows.getInt(1) != 1
                    || !("EMPTY".equals(rows.getString(2)) || "APPROVED".equals(rows.getString(2)))
                    || rows.next()) {
                throw new IllegalStateException(
                        "Imported database requires offline review before application startup");
            }
        }
    }
}
