package com.ifielsc.ijob.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

class RealCrossDatabaseMigrationIT {

    @TempDir Path temporaryDirectory;

    @Test
    void postgresqlV27ShouldRoundTripIntoSqliteV27() throws Exception {
        ConnectionSettings source =
                new ConnectionSettings(
                        required("IJOB_POSTGRESQL_IT_URL"),
                        required("IJOB_POSTGRESQL_IT_USERNAME"),
                        required("IJOB_POSTGRESQL_IT_PASSWORD"),
                        "public",
                        "org.postgresql.Driver");
        Path targetDatabase = temporaryDirectory.resolve("postgresql-to-sqlite.db");
        migrateSqlite(targetDatabase);
        ConnectionSettings target =
                new ConnectionSettings(
                        "jdbc:sqlite:" + targetDatabase.toAbsolutePath().normalize(),
                        "unused",
                        "",
                        null,
                        "org.sqlite.JDBC");

        JdbcMigrationService service = new JdbcMigrationService(new ObjectMapper());
        Path bundle = temporaryDirectory.resolve("postgresql-bundle");
        service.exportBundle(source, bundle);
        service.importBundle(target, bundle);
        service.verifyBundle(target, bundle);

        try (Connection connection = DriverManager.getConnection(target.url());
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM ifj_app_run")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getLong(1));
        }
    }

    private static void migrateSqlite(Path database) {
        Flyway.configure()
                .dataSource("jdbc:sqlite:" + database.toAbsolutePath().normalize(), "unused", "")
                .locations("classpath:db/migration/sqlite")
                .failOnMissingLocations(true)
                .table("ifj_schema_history")
                .target("27")
                .load()
                .migrate();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing integration setting: " + name);
        }
        return value;
    }
}
