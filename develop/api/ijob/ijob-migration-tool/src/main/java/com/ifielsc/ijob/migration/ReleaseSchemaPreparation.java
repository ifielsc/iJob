package com.ifielsc.ijob.migration;

import org.flywaydb.core.Flyway;

import java.sql.DriverManager;

/** Schema-only operations; this process never loads application workers or notification senders. */
final class ReleaseSchemaPreparation {
    private ReleaseSchemaPreparation() {}

    static void initialize(ConnectionSettings settings) throws Exception {
        var flyway = flyway(settings, true);
        try (var connection =
                DriverManager.getConnection(settings.url(), settings.user(), settings.password())) {
            String vendor = ReleaseCatalog.vendor(connection);
            try (var tables =
                    connection
                            .getMetaData()
                            .getTables(
                                    connection.getCatalog(),
                                    vendor.equals("mysql") || vendor.equals("sqlite")
                                            ? null
                                            : settings.schema(),
                                    "%",
                                    new String[] {"TABLE"})) {
                if (tables.next())
                    throw new IllegalStateException(
                            "Initialization requires an empty dedicated database/schema");
            }
        }
        flyway.migrate();
        flyway.validate();
        System.out.println("INIT_OK release=1 logical=38");
    }

    static void prepare(ConnectionSettings settings) throws Exception {
        if (ReleaseSchemaPreparation.class.getResource("/db/migration/mysql/V1__baseline.sql")
                == null) {
            throw new IllegalStateException(
                    "Historical SQL resources are not installed; build the migration tool with the"
                            + " verified legacy-db resource pack");
        }
        var flyway = flyway(settings, false);
        var current = flyway.info().current();
        if (current == null
                || !(current.getVersion().toString().equals("35")
                        || current.getVersion().toString().equals("38"))) {
            throw new IllegalStateException(
                    "Replica preparation requires an intact development V35 or V38 database");
        }
        // Validate the complete frozen prefix before any DDL; Flyway validation alone permits gaps.
        try (var connection =
                DriverManager.getConnection(settings.url(), settings.user(), settings.password())) {
            var expected =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(
                                    ReleaseCatalog.class.getResourceAsStream(
                                            "/release-history/legacy-"
                                                    + ReleaseCatalog.vendor(connection)
                                                    + ".json"));
            var applied = flyway.info().applied();
            int maximum = Integer.parseInt(current.getVersion().toString());
            long count =
                    java.util.stream.StreamSupport.stream(
                                    java.util.Spliterators.spliteratorUnknownSize(
                                            expected.fieldNames(), 0),
                                    false)
                            .filter(version -> Integer.parseInt(version) <= maximum)
                            .count();
            if (applied.length != count)
                throw new IllegalStateException("Incomplete development history");
            for (var migration : applied) {
                var entry = expected.get(migration.getVersion().toString());
                if (entry == null
                        || !entry.get("script").asText().equals(migration.getScript())
                        || !java.util.Objects.equals(
                                entry.get("checksum").asInt(), migration.getChecksum())
                        || migration.getState().isFailed())
                    throw new IllegalStateException("Unknown development history");
            }
        }
        flyway.migrate();
        flyway.validate();
        System.out.println("PREPARE_OK development=38; source replica retained");
    }

    private static Flyway flyway(ConnectionSettings settings, boolean release) throws Exception {
        try (var connection =
                DriverManager.getConnection(settings.url(), settings.user(), settings.password())) {
            String vendor = ReleaseCatalog.vendor(connection);
            if (!vendor.equals("sqlite")
                    && (settings.schema() == null || settings.schema().isBlank())) {
                throw new IllegalArgumentException("A dedicated schema is required");
            }
            if ((vendor.equals("postgresql") && !"public".equals(settings.schema()))
                    || (vendor.equals("sqlserver") && !"dbo".equalsIgnoreCase(settings.schema()))
                    || (vendor.equals("mysql")
                            && !connection.getCatalog().equals(settings.schema()))
                    || (vendor.equals("oracle")
                            && !settings.user().equalsIgnoreCase(settings.schema()))) {
                throw new IllegalArgumentException(
                        "Schema must match the supported database namespace");
            }
            var builder =
                    Flyway.configure()
                            .dataSource(settings.url(), settings.user(), settings.password())
                            .table("ifj_schema_history")
                            .locations(
                                    "classpath:db/"
                                            + (release ? "release" : "migration")
                                            + "/"
                                            + vendor)
                            .cleanDisabled(true)
                            .baselineOnMigrate(false)
                            .target(release ? "1" : "38");
            if (settings.schema() != null) builder.schemas(settings.schema());
            return builder.load();
        }
    }
}
