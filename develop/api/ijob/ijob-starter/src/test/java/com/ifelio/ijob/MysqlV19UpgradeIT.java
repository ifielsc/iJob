package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

class MysqlV19UpgradeIT {
    private static final String UPGRADE_DATABASE = "ijob_it_upgrade_test";
    private static final long INSTANCE_ID = 9_001L;
    private static final long WAIT_ID = 9_002L;

    @Test
    void v18DatabaseShouldBackfillOperatorsAndSeedStableIdsWhenUpgradedToV19() throws Exception {
        MysqlTarget target = MysqlTarget.fromEnvironment();
        recreateIsolatedDatabase(target);

        Flyway v18 = flyway(target, MigrationVersion.fromVersion("18"));
        MigrateResult baseline = v18.migrate();
        assertTrue(baseline.success);
        assertEquals("18", currentVersion(target));

        insertV18Fixtures(target, "[]");
        assertThrows(FlywayException.class, () -> flyway(target, null).migrate());
        assertEquals(
                0,
                scalarLong(
                        target,
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name IN "
                                + "('ifj_event_wait_operator', 'ifj_schema_capability')"));

        recreateIsolatedDatabase(target);
        v18 = flyway(target, MigrationVersion.fromVersion("18"));
        baseline = v18.migrate();
        assertTrue(baseline.success);
        assertEquals("18", currentVersion(target));

        insertV18Fixtures(target, "[\"reviewer\",\"owner\",\"reviewer\"]");

        Flyway latest = flyway(target, MigrationVersion.fromVersion("19"));
        MigrateResult upgrade = latest.migrate();
        latest.validate();
        MigrateResult repeat = latest.migrate();

        assertTrue(upgrade.success);
        assertEquals(1, upgrade.migrationsExecuted);
        assertEquals(0, repeat.migrationsExecuted);
        assertEquals("19", currentVersion(target));
        assertEquals(2, scalarLong(target, "SELECT COUNT(*) FROM ifj_event_wait_operator"));
        assertEquals(
                "owner,reviewer",
                scalarString(
                        target,
                        "SELECT GROUP_CONCAT(operator_id ORDER BY operator_id) "
                                + "FROM ifj_event_wait_operator WHERE wait_id = "
                                + WAIT_ID));
        assertEquals(
                3,
                scalarLong(target, "SELECT COUNT(*) FROM ifj_schema_capability WHERE enabled = 1"));
        assertEquals(
                19,
                scalarLong(
                        target,
                        "SELECT capability_version FROM ifj_schema_capability "
                                + "WHERE capability_key = 'platform-schema'"));
        assertEquals(
                WAIT_ID + 1,
                scalarLong(
                        target,
                        "SELECT next_value FROM ifj_id_segment "
                                + "WHERE segment_key = 'platform-primary-id'"));
    }

    private void recreateIsolatedDatabase(MysqlTarget target) throws Exception {
        assertEquals(UPGRADE_DATABASE, databaseName(target.url()));
        String adminUrl = target.url().replaceFirst("/" + UPGRADE_DATABASE + "(?=\\?|$)", "/mysql");
        try (Connection connection =
                        DriverManager.getConnection(
                                adminUrl, target.username(), target.password());
                var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + UPGRADE_DATABASE + "`");
            statement.execute(
                    "CREATE DATABASE `"
                            + UPGRADE_DATABASE
                            + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
    }

    private void insertV18Fixtures(MysqlTarget target, String operators) throws Exception {
        try (Connection connection =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            try (var statement =
                    connection.prepareStatement(
                            "INSERT INTO ifj_instance (id, target_type, target_id, status) "
                                    + "VALUES (?, 'TASK', 1, 'WAITING_EVENT')")) {
                statement.setLong(1, INSTANCE_ID);
                assertEquals(1, statement.executeUpdate());
            }
            try (var statement =
                    connection.prepareStatement(
                            "UPDATE ifj_instance SET root_instance_id = id WHERE id = ?")) {
                statement.setLong(1, INSTANCE_ID);
                assertEquals(1, statement.executeUpdate());
            }
            try (var statement =
                    connection.prepareStatement(
                            "INSERT INTO ifj_event_wait (id, instance_id, root_instance_id, "
                                    + "attempt_no, wait_type, status, accept_after, "
                                    + "allowed_operator_ids) VALUES (?, ?, ?, 0, 'APPROVAL', "
                                    + "'WAITING', UTC_TIMESTAMP(3), ?)")) {
                statement.setLong(1, WAIT_ID);
                statement.setLong(2, INSTANCE_ID);
                statement.setLong(3, INSTANCE_ID);
                statement.setString(4, operators);
                assertEquals(1, statement.executeUpdate());
            }
            try (var statement =
                    connection.prepareStatement(
                            "INSERT INTO ifj_id_segment (segment_key, next_value, segment_size,"
                                    + " update_time) VALUES ('platform-primary-id', 100, 1000,"
                                    + " UTC_TIMESTAMP(3))")) {
                assertEquals(1, statement.executeUpdate());
            }
        }
    }

    private Flyway flyway(MysqlTarget target, MigrationVersion targetVersion) {
        var configuration =
                Flyway.configure()
                        .dataSource(target.url(), target.username(), target.password())
                        .locations("classpath:db/migration/mysql")
                        .table("ifj_schema_history")
                        .baselineOnMigrate(false)
                        .validateOnMigrate(true)
                        .cleanDisabled(true);
        if (targetVersion != null) {
            configuration.target(targetVersion);
        }
        return configuration.load();
    }

    private String currentVersion(MysqlTarget target) throws Exception {
        return scalarString(
                target,
                "SELECT version FROM ifj_schema_history WHERE success = 1 "
                        + "ORDER BY installed_rank DESC LIMIT 1");
    }

    private long scalarLong(MysqlTarget target, String sql) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private String scalarString(MysqlTarget target, String sql) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                var statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static String databaseName(String url) {
        if (url == null || !url.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("MySQL integration JDBC URL is invalid");
        }
        try {
            java.net.URI uri = java.net.URI.create(url.substring("jdbc:".length()));
            String path = uri.getRawPath();
            if (uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getRawFragment() != null
                    || path == null
                    || !path.matches("/ijob_it_[A-Za-z0-9_]+")) {
                throw new IllegalStateException("An isolated ijob_it_ database is required");
            }
            return path.substring(1);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MySQL integration JDBC URL is invalid", exception);
        }
    }

    private record MysqlTarget(String url, String username, String password) {
        private static MysqlTarget fromEnvironment() {
            String url = required("IJOB_MYSQL_IT_URL");
            String username = required("IJOB_MYSQL_IT_USERNAME");
            String password = System.getenv("IJOB_MYSQL_IT_PASSWORD");
            databaseName(url);
            return new MysqlTarget(url, username, password == null ? "" : password);
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Required MySQL integration environment is missing: " + name);
            }
            return value;
        }
    }
}
