package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;

class SqliteMigrationBoundaryTest {
    @TempDir Path directory;

    @Test
    void fkEnabledUpgradePreservesPoliciesAndRestoresPooledConnection() throws Exception {
        try (var source = dataSource()) {
            Flyway.configure()
                    .dataSource(source)
                    .locations("classpath:db/migration/sqlite")
                    .table("ifj_schema_history")
                    .target("22")
                    .load()
                    .migrate();
            try (var connection = source.getConnection();
                    var statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO ifj_task"
                            + " (id,name,plugin_type,plugin_config,concurrency_type,lock_key)"
                            + " VALUES"
                            + " (1,'resource','EVENT','{}','RESOURCE','shared'),(2,'single','EVENT','{}','TASK_SINGLE',NULL)");
                statement.executeUpdate(
                        "INSERT INTO ifj_task_definition"
                            + " (id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,enabled,concurrency_type)"
                            + " VALUES (11,1,1,'resource','EVENT','{}',0,0,1,'ALLOW')");
            }
            migrate(
                    Flyway.configure()
                            .dataSource(source)
                            .locations("classpath:db/migration/sqlite")
                            .table("ifj_schema_history")
                            .target("23")
                            .load());
            try (var connection = source.getConnection()) {
                assertEquals(1, scalar(connection, "PRAGMA foreign_keys"));
                assertEquals(
                        2, scalar(connection, "SELECT COUNT(*) FROM ifj_task_concurrency_policy"));
                assertEquals(
                        1,
                        scalar(connection, "SELECT COUNT(*) FROM ifj_task_concurrency_resource"));
                assertEquals(
                        1,
                        scalar(
                                connection,
                                "SELECT task_max_concurrency FROM ifj_task_concurrency_policy WHERE"
                                        + " task_id=2"));
                assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM ifj_task_definition"));
                assertEquals(
                        0, scalar(connection, "SELECT COUNT(*) FROM pragma_foreign_key_check"));
            }
            Flyway failure =
                    Flyway.configure()
                            .dataSource(source)
                            .locations("classpath:db/migration/sqlite")
                            .table("ifj_schema_history")
                            .target("24")
                            .javaMigrations(new V24__InvalidMigration())
                            .load();
            // Use a separate location to avoid replacing the published V24 SQL migration.
            failure =
                    Flyway.configure()
                            .configuration(failure.getConfiguration())
                            .locations("classpath:missing-review-migrations")
                            .ignoreMigrationPatterns("*:missing")
                            .load();
            Flyway invalid = failure;
            var failureException = assertThrows(RuntimeException.class, () -> migrate(invalid));
            Throwable integrityFailure = failureException;
            while (integrityFailure.getCause() != null)
                integrityFailure = integrityFailure.getCause();
            assertTrue(integrityFailure.getMessage().contains("violates foreign key integrity"));
            try (var connection = source.getConnection()) {
                assertEquals(1, scalar(connection, "PRAGMA foreign_keys"));
                assertEquals(
                        0,
                        scalar(
                                connection,
                                "SELECT COUNT(*) FROM ifj_schema_history WHERE version='24'"));
                assertEquals(
                        0,
                        scalar(
                                connection,
                                "SELECT COUNT(*) FROM ifj_task_concurrency_policy WHERE"
                                        + " task_id=999"));
            }
        }
    }

    private void migrate(Flyway flyway) {
        SqliteMigrationBoundary.migrate(flyway);
    }

    private HikariDataSource dataSource() {
        var config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + directory.resolve("upgrade.db").toAbsolutePath());
        config.setMaximumPoolSize(1);
        config.setConnectionInitSql("PRAGMA foreign_keys=ON");
        config.addDataSourceProperty("transaction_mode", "IMMEDIATE");
        return new HikariDataSource(config);
    }

    private long scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static final class V24__InvalidMigration extends BaseJavaMigration {
        @Override
        public MigrationVersion getVersion() {
            return MigrationVersion.fromVersion("24");
        }

        @Override
        public String getDescription() {
            return "review deliberately invalid foreign key";
        }

        @Override
        public void migrate(Context context) throws Exception {
            try (var statement = context.getConnection().createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO ifj_task_concurrency_policy(task_id,task_max_concurrency)"
                                + " VALUES(999,1)");
            }
        }
    }
}
