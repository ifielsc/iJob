// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.migration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

class ReleaseMigrationTest {
    @TempDir Path directory;

    private ConnectionSettings database(String name, boolean release) {
        String url = "jdbc:sqlite:" + directory.resolve(name + ".db");
        Flyway.configure()
                .dataSource(url, "", "")
                .table("ifj_schema_history")
                .locations("classpath:db/" + (release ? "release" : "migration") + "/sqlite")
                .failOnMissingLocations(true)
                .failOnMissingLocations(true)
                .load()
                .migrate();
        return new ConnectionSettings(url, "", "", null, null);
    }

    private void sql(ConnectionSettings db, String sql) throws Exception {
        try (var connection = DriverManager.getConnection(db.url());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String scalar(ConnectionSettings db, String sql) throws Exception {
        try (var connection = DriverManager.getConnection(db.url());
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private JdbcMigrationService service(boolean replica) {
        return new JdbcMigrationService(new ObjectMapper(), true, replica);
    }

    @Test
    void preservesAllRowsAndIdHighWatermarksWhileBlockingStartup() throws Exception {
        var source = database("source", false);
        var target = database("target", true);
        sql(
                source,
                "INSERT INTO ifj_mail_channel"
                        + " (id,name,config,enabled,revision,create_time,update_time) VALUES"
                        + " (9007199254740993,'Unicode 测试','opaque-cipher',1,1,'2026-10-17"
                        + " 01:02:03.456','2026-10-17 01:02:03.456')");
        sql(
                source,
                "UPDATE ifj_id_segment SET next_value=9007199254741999 WHERE"
                        + " segment_key='pk:ifj_mail_channel'");
        sql(source, "INSERT INTO ifj_notify_rate_limit VALUES ('synthetic-digest',1792200000123)");
        var bundle = directory.resolve("bundle");
        service(false).exportBundle(source, bundle);
        service(false).importBundle(target, bundle);
        service(false).verifyBundle(target, bundle);
        assertEquals(
                "9007199254741999",
                scalar(
                        target,
                        "SELECT next_value FROM ifj_id_segment WHERE"
                                + " segment_key='pk:ifj_mail_channel'"));
        assertEquals("Unicode 测试", scalar(target, "SELECT name FROM ifj_mail_channel"));
        assertEquals(
                "1792200000123",
                scalar(target, "SELECT next_allowed_at FROM ifj_notify_rate_limit"));
        assertEquals("REVIEW_REQUIRED", scalar(target, "SELECT state FROM ifj_release_state"));
        assertThrows(
                IllegalStateException.class, () -> service(false).importBundle(target, bundle));
    }

    @Test
    void refusesRunningSourceUnlessExplicitlyAcknowledgedAsIsolatedReplica() throws Exception {
        var source = database("active", false);
        sql(
                source,
                "INSERT INTO ifj_app_run (run_id,status,started_at,heartbeat_at) VALUES"
                        + " ('copied-run','RUNNING','2026-10-17 00:00:00.000','2026-10-17"
                        + " 00:00:00.000')");
        assertThrows(
                IllegalStateException.class,
                () -> service(false).exportBundle(source, directory.resolve("refused")));
        var bundle = directory.resolve("replica");
        service(true).exportBundle(source, bundle);
        var target = database("replica-target", true);
        service(false).importBundle(target, bundle);
        assertEquals(
                "RUNNING",
                scalar(target, "SELECT status FROM ifj_app_run WHERE run_id='copied-run'"));
        assertEquals("REVIEW_REQUIRED", scalar(target, "SELECT state FROM ifj_release_state"));
    }

    @Test
    void refusesChangedHistoryAndUnexpectedPlatformTables() throws Exception {
        var source = database("history", false);
        sql(source, "UPDATE ifj_schema_history SET checksum=0 WHERE version='38'");
        assertThrows(
                IllegalStateException.class,
                () -> service(false).exportBundle(source, directory.resolve("bad-history")));
        var extra = database("extra", false);
        sql(extra, "CREATE TABLE ifj_untracked (id INTEGER PRIMARY KEY)");
        assertThrows(
                IllegalStateException.class,
                () -> service(false).exportBundle(extra, directory.resolve("bad-catalog")));
    }

    @Test
    void refusesUnsafeHighWatermark() throws Exception {
        var source = database("ids", false);
        sql(
                source,
                "INSERT INTO ifj_mail_channel"
                        + " (id,name,config,enabled,revision,create_time,update_time) VALUES"
                        + " (500,'id-test','opaque',1,1,'2026-10-17 00:00:00.000','2026-10-17"
                        + " 00:00:00.000')");
        assertThrows(
                IllegalStateException.class,
                () -> service(false).exportBundle(source, directory.resolve("bad-id")));
    }

    @Test
    void dirtyTargetFailureRollsBackGateAndPreservesData() throws Exception {
        var source = database("clean-source", false);
        var target = database("dirty", true);
        sql(target, "INSERT INTO ifj_notify_rate_limit VALUES ('existing',42)");
        var bundle = directory.resolve("dirty-bundle");
        service(false).exportBundle(source, bundle);
        assertThrows(
                IllegalStateException.class, () -> service(false).importBundle(target, bundle));
        assertEquals("42", scalar(target, "SELECT next_allowed_at FROM ifj_notify_rate_limit"));
        assertEquals("EMPTY", scalar(target, "SELECT state FROM ifj_release_state"));
    }

    @Test
    void refusesTamperedBundleAndWrongLineage() throws Exception {
        var source = database("tamper-source", false);
        var target = database("tamper-target", true);
        var bundle = directory.resolve("tamper-bundle");
        service(false).exportBundle(source, bundle);
        java.nio.file.Files.writeString(bundle.resolve("data/ifj_mail_channel.jsonl"), "[]\n");
        assertThrows(
                IllegalStateException.class, () -> service(false).importBundle(target, bundle));
        assertThrows(
                IllegalStateException.class,
                () -> service(false).exportBundle(target, directory.resolve("wrong-lineage")));
    }

    @Test
    void selfReferencesDoNotFireUpdateTriggersOrNormalizeEmptyText() throws Exception {
        var source = database("hierarchy", false);
        var target = database("hierarchy-target", true);
        sql(
                source,
                "INSERT INTO ifj_instance"
                    + " (id,root_instance_id,target_type,target_id,status,message,create_time,update_time)"
                    + " VALUES (2,2,'TASK',1,'SUCCESS',NULL,'2026-10-17 01:02:03.456','2026-10-17"
                    + " 01:02:03.456')");
        sql(
                source,
                "INSERT INTO ifj_instance"
                    + " (id,parent_instance_id,root_instance_id,target_type,target_id,status,message,create_time,update_time)"
                    + " VALUES (1,2,2,'TASK',1,'SUCCESS','','2026-10-17 01:02:03.456','2026-10-17"
                    + " 01:02:03.456')");
        sql(source, "UPDATE ifj_id_segment SET next_value=100 WHERE segment_key='pk:ifj_instance'");
        var bundle = directory.resolve("hierarchy-bundle");
        service(false).exportBundle(source, bundle);
        service(false).importBundle(target, bundle);
        assertEquals(
                "2026-10-17 01:02:03.456",
                scalar(target, "SELECT update_time FROM ifj_instance WHERE id=1"));
        assertEquals("", scalar(target, "SELECT message FROM ifj_instance WHERE id=1"));
        assertNull(scalar(target, "SELECT message FROM ifj_instance WHERE id=2"));
        assertEquals("2", scalar(target, "SELECT parent_instance_id FROM ifj_instance WHERE id=1"));
    }

    @Test
    void lateFailureRollsBackPreviouslyImportedTablesAndGate() throws Exception {
        var source = database("cycle", false);
        var target = database("cycle-target", true);
        sql(source, "INSERT INTO ifj_notify_rate_limit VALUES ('retained',42)");
        sql(
                source,
                "INSERT INTO ifj_instance (id,parent_instance_id,target_type,target_id,status)"
                        + " VALUES (1,2,'TASK',1,'SUCCESS'),(2,1,'TASK',1,'SUCCESS')");
        sql(source, "UPDATE ifj_id_segment SET next_value=100 WHERE segment_key='pk:ifj_instance'");
        var bundle = directory.resolve("cycle-bundle");
        service(false).exportBundle(source, bundle);
        assertThrows(
                IllegalStateException.class, () -> service(false).importBundle(target, bundle));
        assertEquals("0", scalar(target, "SELECT COUNT(*) FROM ifj_notify_rate_limit"));
        assertEquals("0", scalar(target, "SELECT COUNT(*) FROM ifj_instance"));
        assertEquals("29", scalar(target, "SELECT COUNT(*) FROM ifj_id_segment"));
        assertEquals("EMPTY", scalar(target, "SELECT state FROM ifj_release_state"));
    }

    @Test
    void modifiedSeedCannotBeOverwrittenEvenWhenRowCountsMatch() throws Exception {
        var source = database("seed-source", false);
        var target = database("seed-target", true);
        sql(target, "UPDATE ifj_id_segment SET next_value=2 WHERE segment_key='pk:ifj_instance'");
        var bundle = directory.resolve("seed-bundle");
        service(false).exportBundle(source, bundle);
        assertThrows(
                IllegalStateException.class, () -> service(false).importBundle(target, bundle));
        assertEquals(
                "2",
                scalar(
                        target,
                        "SELECT next_value FROM ifj_id_segment WHERE"
                                + " segment_key='pk:ifj_instance'"));
        assertEquals("EMPTY", scalar(target, "SELECT state FROM ifj_release_state"));
    }

    @Test
    void preparesOnlyFrozenV35ReplicaAndInitializesWithoutWorkers() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("v35.db");
        Flyway.configure()
                .dataSource(url, "", "")
                .table("ifj_schema_history")
                .locations("classpath:db/migration/sqlite")
                .target("35")
                .failOnMissingLocations(true)
                .failOnMissingLocations(true)
                .load()
                .migrate();
        var source = new ConnectionSettings(url, "", "", null, null);
        sql(
                source,
                "INSERT INTO ifj_instance (id,target_type,target_id,status) VALUES"
                        + " (1,'TASK',1,'SUCCESS')");
        sql(source, "UPDATE ifj_id_segment SET next_value=100 WHERE segment_key='pk:ifj_instance'");
        ReleaseSchemaPreparation.prepare(source);
        var target =
                new ConnectionSettings(
                        "jdbc:sqlite:" + directory.resolve("prepared-target.db"),
                        "",
                        "",
                        null,
                        null);
        ReleaseSchemaPreparation.initialize(target);
        var bundle = directory.resolve("prepared-bundle");
        service(false).exportBundle(source, bundle);
        service(false).importBundle(target, bundle);
        assertEquals("SUCCESS", scalar(target, "SELECT status FROM ifj_instance"));
        assertThrows(
                IllegalStateException.class, () -> ReleaseSchemaPreparation.initialize(target));
        assertThrows(Exception.class, () -> ReleaseSchemaPreparation.prepare(target));
    }
}
