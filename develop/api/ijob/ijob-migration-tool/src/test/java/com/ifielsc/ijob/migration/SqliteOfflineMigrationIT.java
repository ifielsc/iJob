package com.ifielsc.ijob.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

class SqliteOfflineMigrationIT {

    @TempDir Path temporaryDirectory;

    @Test
    void exportsImportsAndVerifiesTheFullV27Catalog() throws Exception {
        Path source = temporaryDirectory.resolve("source.db");
        Path target = temporaryDirectory.resolve("target.db");
        migrate(source);
        migrate(target);
        seedSource(source);

        JdbcMigrationService service = new JdbcMigrationService(new ObjectMapper());
        Path bundle = temporaryDirectory.resolve("bundle");
        service.exportBundle(settings(source), bundle);
        service.importBundle(settings(target), bundle);
        service.verifyBundle(settings(target), bundle);

        try (Connection connection = DriverManager.getConnection(url(target));
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT name, enabled, description FROM ifj_task WHERE id = 100")) {
            rows.next();
            assertEquals("迁移任务-✓", rows.getString(1));
            assertEquals(1, rows.getInt(2));
            assertNull(rows.getString(3));
        }
        try (Connection connection = DriverManager.getConnection(url(target));
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT revision FROM ifj_runtime_guard WHERE"
                                        + " guard_key='concurrency-policy'")) {
            rows.next();
            assertEquals(7L, rows.getLong(1));
        }
        try (Connection connection = DriverManager.getConnection(url(target));
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT login_id, role_code, enabled, must_change_password,"
                                        + " password_hash FROM ifj_auth_user WHERE id=9001")) {
            assertTrue(rows.next());
            assertEquals("legacy-admin", rows.getString(1));
            assertEquals("ADMIN", rows.getString(2));
            assertEquals(1, rows.getInt(3));
            assertEquals(0, rows.getInt(4));
            assertEquals("synthetic-hash-not-a-real-secret", rows.getString(5));
        }
        try (Connection connection = DriverManager.getConnection(url(target));
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT actor_login_id, result_code FROM ifj_operation_audit WHERE"
                                        + " id=9002")) {
            assertTrue(rows.next());
            assertEquals("legacy-admin", rows.getString(1));
            assertEquals("SUCCEEDED", rows.getString(2));
        }
    }

    @Test
    void refusesAnActiveSourceAndLeavesAnIncompleteMarker() throws Exception {
        Path source = temporaryDirectory.resolve("active-source.db");
        migrate(source);
        try (Connection connection = DriverManager.getConnection(url(source));
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO ifj_app_run (run_id, status, started_at, heartbeat_at) "
                            + "VALUES ('active-run', 'RUNNING', '2026-09-16 00:00:00.000', "
                            + "'2026-09-16 00:00:00.000')");
        }
        Path bundle = temporaryDirectory.resolve("active-bundle");
        JdbcMigrationService service = new JdbcMigrationService(new ObjectMapper());

        assertThrows(
                IllegalStateException.class, () -> service.exportBundle(settings(source), bundle));
        assertTrue(Files.isRegularFile(bundle.resolve("INCOMPLETE")));
    }

    @Test
    void refusesTamperedBundlesAndDirtyTargets() throws Exception {
        Path source = temporaryDirectory.resolve("guard-source.db");
        Path target = temporaryDirectory.resolve("dirty-target.db");
        migrate(source);
        migrate(target);
        JdbcMigrationService service = new JdbcMigrationService(new ObjectMapper());
        Path bundle = temporaryDirectory.resolve("guard-bundle");
        service.exportBundle(settings(source), bundle);

        try (Connection connection = DriverManager.getConnection(url(target));
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO ifj_task (id, name, plugin_type, plugin_config, "
                            + "retry_times, retry_interval, enabled, retired) VALUES "
                            + "(101, 'dirty', 'SCRIPT', '{}', 0, 0, 1, 0)");
        }
        assertThrows(
                IllegalStateException.class, () -> service.importBundle(settings(target), bundle));

        Files.writeString(
                bundle.resolve("data/ifj_id_segment.jsonl"),
                " ",
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        assertThrows(
                IllegalStateException.class, () -> service.verifyBundle(settings(source), bundle));
    }

    private static void migrate(Path database) {
        migrate(database, "27");
    }

    @Test
    void rejectsNewerSchemaInsteadOfExportingAnIncompleteV27Catalog() throws Exception {
        Path source = temporaryDirectory.resolve("newer-source.db");
        migrate(source, "30");
        Path bundle = temporaryDirectory.resolve("newer-bundle");
        JdbcMigrationService service = new JdbcMigrationService(new ObjectMapper());
        assertThrows(
                IllegalStateException.class, () -> service.exportBundle(settings(source), bundle));
        assertTrue(Files.isRegularFile(bundle.resolve("INCOMPLETE")));
    }

    private static void migrate(Path database, String target) {
        Flyway.configure()
                .dataSource(url(database), "unused", "")
                .locations("classpath:db/migration/sqlite")
                .failOnMissingLocations(true)
                .table("ifj_schema_history")
                .target(target)
                .load()
                .migrate();
    }

    private static void seedSource(Path database) throws Exception {
        try (Connection connection = DriverManager.getConnection(url(database));
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO ifj_auth_user"
                        + " (id,login_id,username,password_hash,role_code,enabled,must_change_password,"
                        + " created_by,create_time,update_time) VALUES"
                        + " (9001,'legacy-admin','admin','synthetic-hash-not-a-real-secret',"
                        + " 'ADMIN',1,0,'SYSTEM','2026-09-20 00:00:00.000','2026-09-20"
                        + " 00:00:00.000')");
            statement.executeUpdate(
                    "INSERT INTO ifj_operation_audit"
                        + " (id,occurred_at,actor_login_id,action_code,target_type,target_id,result_code)"
                        + " VALUES (9002,'2026-09-20 00:00:00.000','legacy-admin',"
                        + " 'ACCOUNT_CREATED','ACCOUNT','9001','SUCCEEDED')");
            statement.executeUpdate(
                    "INSERT INTO ifj_task (id, name, plugin_type, plugin_config, retry_times,"
                        + " retry_interval, enabled, description, retired) VALUES (100, '迁移任务-✓',"
                        + " 'SCRIPT', '{\"script\":\"return 1\"}', 2, 15, 1, '', 0)");
            statement.executeUpdate(
                    "UPDATE ifj_id_segment SET next_value = 10001 "
                            + "WHERE segment_key = 'platform-primary-id'");
            statement.executeUpdate(
                    "UPDATE ifj_runtime_guard SET revision=7 WHERE guard_key='concurrency-policy'");
        }
    }

    private static ConnectionSettings settings(Path database) {
        return new ConnectionSettings(url(database), "unused", "", null, "org.sqlite.JDBC");
    }

    private static String url(Path database) {
        return "jdbc:sqlite:" + database.toAbsolutePath().normalize();
    }
}
