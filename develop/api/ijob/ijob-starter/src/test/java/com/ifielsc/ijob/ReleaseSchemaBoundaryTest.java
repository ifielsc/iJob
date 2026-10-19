package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class ReleaseSchemaBoundaryTest {
    @TempDir Path directory;

    @Test
    void releasesPreflightConnectionBeforeFlywayValidationWithSingleConnectionPool() {
        var config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + directory.resolve("single-connection.db"));
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(300);
        try (var pool = new com.zaxxer.hikari.HikariDataSource(config)) {
            var release =
                    Flyway.configure()
                            .dataSource(pool)
                            .locations("classpath:db/release/sqlite")
                            .table("ifj_schema_history")
                            .cleanDisabled(true)
                            .load();
            ReleaseSchemaBoundary.beforeMigration(release, "sqlite");
            release.migrate();
            assertDoesNotThrow(() -> ReleaseSchemaBoundary.beforeMigration(release, "sqlite"));
        }
    }

    private Flyway flyway(String name, boolean release) {
        return Flyway.configure()
                .dataSource("jdbc:sqlite:" + directory.resolve(name + ".db"), "", "")
                .locations("classpath:db/" + (release ? "release" : "migration") + "/sqlite")
                .table("ifj_schema_history")
                .cleanDisabled(true)
                .load();
    }

    @Test
    void newReleaseInstallsOnceAndPassesCurrentStructureGuard() {
        var flyway = flyway("fresh", true);
        ReleaseSchemaBoundary.beforeMigration(flyway, "sqlite");
        assertEquals(1, flyway.migrate().migrationsExecuted);
        ReleaseSchemaBoundary.beforeMigration(flyway, "sqlite");
        assertEquals(0, flyway.migrate().migrationsExecuted);
        new SqliteSchemaCompatibilityInspector(flyway.getConfiguration().getDataSource()).verify();
        assertThrows(
                org.flywaydb.core.api.FlywayException.class,
                () -> flyway("fresh", false).validate());
    }

    @Test
    void developmentDatabaseIsRejectedBeforeMigration() throws Exception {
        var legacy = flyway("legacy", false);
        legacy.migrate();
        assertThrows(
                IllegalStateException.class,
                () -> ReleaseSchemaBoundary.beforeMigration(flyway("legacy", true), "sqlite"));
        assertEquals("38", legacy.info().current().getVersion().toString());
        try (var connection = legacy.getConfiguration().getDataSource().getConnection();
                var statement = connection.createStatement();
                var rows =
                        statement.executeQuery(
                                "SELECT COUNT(*) FROM sqlite_master WHERE"
                                        + " name='ifj_release_state'")) {
            rows.next();
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test
    void importedDatabaseCannotStartUntilReviewed() throws Exception {
        var release = flyway("blocked", true);
        release.migrate();
        try (var connection = release.getConfiguration().getDataSource().getConnection();
                var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE ifj_release_state SET state='REVIEW_REQUIRED'");
        }
        assertThrows(
                IllegalStateException.class,
                () -> ReleaseSchemaBoundary.beforeMigration(release, "sqlite"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        new SqliteSchemaCompatibilityInspector(
                                        release.getConfiguration().getDataSource())
                                .verify());
    }

    @Test
    void historylessPlatformTablesAndLegacyLocationsAreRejected() throws Exception {
        var release = flyway("unknown", true);
        try (var connection = release.getConfiguration().getDataSource().getConnection();
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ifj_task (id INTEGER PRIMARY KEY)");
        }
        assertThrows(
                IllegalStateException.class,
                () -> ReleaseSchemaBoundary.beforeMigration(release, "sqlite"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        ReleaseSchemaBoundary.beforeMigration(
                                flyway("legacy-location", false), "sqlite"));
    }

    @Test
    void laterReleaseMigrationCanUpgradeAndRestartWithoutRewritingV1() throws Exception {
        var release = flyway("later", true);
        release.migrate();
        var resources = directory.resolve("future/db/release/sqlite");
        java.nio.file.Files.createDirectories(resources);
        java.nio.file.Files.writeString(
                resources.resolve("V2__test_only.sql"),
                "CREATE TABLE future_test (id INTEGER PRIMARY KEY);");
        try (var loader =
                new java.net.URLClassLoader(
                        new java.net.URL[] {directory.resolve("future").toUri().toURL()},
                        getClass().getClassLoader())) {
            var future =
                    Flyway.configure(loader)
                            .dataSource(release.getConfiguration().getDataSource())
                            .table("ifj_schema_history")
                            .locations("classpath:db/release/sqlite")
                            .cleanDisabled(true)
                            .load();
            ReleaseSchemaBoundary.beforeMigration(future, "sqlite");
            assertEquals(1, future.migrate().migrationsExecuted);
            ReleaseSchemaBoundary.beforeMigration(future, "sqlite");
            assertEquals(0, future.migrate().migrationsExecuted);
            assertThrows(
                    org.flywaydb.core.api.FlywayException.class,
                    () -> ReleaseSchemaBoundary.beforeMigration(release, "sqlite"));
        }
    }
}
