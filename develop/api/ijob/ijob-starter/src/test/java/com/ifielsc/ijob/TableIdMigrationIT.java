// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.infra.coordination.id.DatabaseIdSegmentProvider;
import com.ifielsc.ijob.infra.coordination.id.DatabaseSegmentIdGenerator;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mapper.coordination.IdSegmentMapper;
import com.ifielsc.ijob.infra.mapper.job.JobReferenceSnapshotMapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class TableIdMigrationIT {
    @TempDir Path directory;

    @Test
    void legacyFreshInstallShouldHaveIndependentSeedsAndNoLegacyTables() throws Exception {
        var ds = database("fresh");
        migrate(ds, "35");
        var jdbc = new JdbcTemplate(ds);
        assertEquals(
                44,
                scalar(
                        jdbc,
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name LIKE"
                                + " 'ifj_%' AND name <> 'ifj_schema_history'"));
        assertEquals(
                27,
                scalar(
                        jdbc,
                        "SELECT COUNT(*) FROM ifj_id_segment WHERE segment_key LIKE 'pk:%' AND"
                                + " next_value=1"));
        migrate(ds, "37");
        try (var c = ds.getConnection()) {
            TableIdSchemaVerifier.verify(c);
        }
        // Historical migrations remain testable but cannot be used as a release database.
        assertThrows(
                IllegalStateException.class,
                () -> new SqliteSchemaCompatibilityInspector(ds).verify());
        var factory = sessions(ds);
        var generator = generator(factory);
        assertEquals(1, generator.nextId(IdNamespace.CALENDAR));
        assertEquals(1, generator.nextId(IdNamespace.TASK));
        assertEquals(1, generator.nextId(IdNamespace.JOB));
        for (int i = 0; i < 1100; i++) generator.nextId(IdNamespace.INSTANCE_LOG);
        assertEquals(2, generator(factory).nextId(IdNamespace.CALENDAR));
        assertEquals(2001, generator(factory).nextId(IdNamespace.INSTANCE_LOG));
        assertEquals(
                3,
                scalar(
                        jdbc,
                        "SELECT next_value FROM ifj_id_segment WHERE"
                                + " segment_key='pk:ifj_calendar'"));
        jdbc.update("DELETE FROM ifj_id_segment WHERE segment_key='pk:ifj_resource'");
        assertThrows(IllegalStateException.class, () -> generator.nextId(IdNamespace.RESOURCE));
        try (var c = ds.getConnection()) {
            assertThrows(IllegalStateException.class, () -> TableIdSchemaVerifier.verify(c));
        }
        assertEquals(
                0,
                scalar(
                        jdbc,
                        "SELECT COUNT(*) FROM ifj_id_segment WHERE segment_key='pk:ifj_resource'"));
    }

    @Test
    void releaseBaselinePassesStartupInspectionAndUsesIndependentIdSeeds() throws Exception {
        var ds = database("release");
        var flyway =
                Flyway.configure()
                        .dataSource(ds)
                        .locations("classpath:db/release/sqlite")
                        .table("ifj_schema_history")
                        .cleanDisabled(true)
                        .load();
        ReleaseSchemaBoundary.beforeMigration(flyway, "sqlite");
        assertEquals(1, flyway.migrate().migrationsExecuted);
        ReleaseSchemaBoundary.beforeMigration(flyway, "sqlite");
        new SqliteSchemaCompatibilityInspector(ds).verify();
        try (var connection = ds.getConnection()) {
            TableIdSchemaVerifier.verify(connection);
        }
        var generator = generator(sessions(ds));
        assertEquals(1, generator.nextId(IdNamespace.CALENDAR));
        assertEquals(1, generator.nextId(IdNamespace.TASK));
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    void upgradeShouldPreserveRowsForeignKeysAndReservedWatermark() throws Exception {
        var ds = database("upgrade");
        migrate(ds, "33");
        var jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO ifj_calendar (id,name,workdays_mask) VALUES (77,'existing',31)");
        jdbc.update(
                "INSERT INTO ifj_instance (id,target_type,target_id,status) VALUES"
                        + " (99,'TASK',42,'SUCCESS')");
        jdbc.update(
                "INSERT INTO ifj_instance_log (id,instance_id,log_time,level,content) VALUES"
                        + " (100,99,'2026-10-08 00:00:00.000','INFO','preserved')");
        jdbc.update(
                "UPDATE ifj_id_segment SET next_value=9000 WHERE"
                        + " segment_key='platform-primary-id'");
        migrate(ds, "35");
        assertEquals(
                27,
                scalar(
                        jdbc,
                        "SELECT COUNT(*) FROM ifj_id_segment WHERE segment_key LIKE 'pk:%' AND"
                                + " next_value=9000"));
        assertEquals(77, scalar(jdbc, "SELECT id FROM ifj_calendar WHERE name='existing'"));
        assertEquals(99, scalar(jdbc, "SELECT instance_id FROM ifj_instance_log WHERE id=100"));
        assertEquals(0, scalar(jdbc, "SELECT COUNT(*) FROM pragma_foreign_key_check"));
        assertEquals(
                0,
                scalar(
                        jdbc,
                        "SELECT COUNT(*) FROM pragma_foreign_key_list('ifj_instance') WHERE"
                                + " \"table\"='ifj_job_node'"));
        jdbc.update("UPDATE ifj_instance SET update_time='2000-01-01 00:00:00.000' WHERE id=99");
        jdbc.update("UPDATE ifj_instance SET message='touch after migration' WHERE id=99");
        assertFalse(
                "2000-01-01 00:00:00.000"
                        .equals(
                                jdbc.queryForObject(
                                        "SELECT update_time FROM ifj_instance WHERE id=99",
                                        String.class)));
        assertEquals(9000, generator(sessions(ds)).nextId(IdNamespace.CALENDAR));
        try (var session = sessions(ds).openSession(false)) {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            new DatabaseIdSegmentProvider(session.getMapper(IdSegmentMapper.class))
                                    .reserve("platform-primary-id", 1));
        }
        assertThrows(
                Exception.class,
                () ->
                        jdbc.update(
                                "INSERT INTO ifj_instance_log"
                                    + " (id,instance_id,log_time,level,content) VALUES"
                                    + " (101,12345,'2026-10-08 00:00:00.000','INFO','orphan')"));
    }

    @Test
    void upgradeShouldAlsoRespectExplicitIdsAboveOldWatermark() {
        var ds = database("explicit");
        migrate(ds, "33");
        var jdbc = new JdbcTemplate(ds);
        jdbc.update(
                "INSERT INTO ifj_calendar (id,name,workdays_mask) VALUES (45000,'imported',31)");
        migrate(ds, "35");
        assertEquals(
                27,
                scalar(
                        jdbc,
                        "SELECT COUNT(*) FROM ifj_id_segment WHERE segment_key LIKE 'pk:%' AND"
                                + " next_value=45001"));
    }

    @Test
    void legacyDataMustStopDeletionBeforeAnyGraphTableIsDropped() {
        var ds = database("legacy");
        migrate(ds, "34");
        var jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO ifj_job (id,name) VALUES (1,'legacy')");
        jdbc.update(
                "INSERT INTO ifj_job_node (id,name,job_id,node_type,ref_id) VALUES (2,'legacy"
                        + " node',1,'TASK',3)");
        assertThrows(Exception.class, () -> migrate(ds, "35"));
        assertEquals(1, scalar(jdbc, "SELECT COUNT(*) FROM ifj_job_node"));
        assertEquals(
                3,
                scalar(
                        jdbc,
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN"
                                + " ('ifj_job_node','ifj_job_edge','ifj_job_node_policy')"));
        assertEquals(
                0,
                scalar(
                        jdbc,
                        "SELECT COUNT(*) FROM ifj_schema_capability WHERE"
                                + " capability_key='legacy-dag-removed'"));
    }

    @Test
    void referencesShouldReadOnlyCurrentPublishedJobNodesWithCollidingTaskIds() throws Exception {
        var ds = database("references");
        migrate(ds, "35");
        var jdbc = new JdbcTemplate(ds);
        jdbc.update(
                "INSERT INTO ifj_job (id,name) VALUES (1,'parent'),(2,'current"
                        + " child'),(3,'historical child')");
        jdbc.update(
                "INSERT INTO ifj_task (id,name,plugin_type,plugin_config) VALUES (2,'task with same"
                        + " ID','SCRIPT','{}')");
        jdbc.update(
                "INSERT INTO ifj_job_definition (id,job_id,revision,name,enabled) VALUES"
                        + " (10,1,1,'old',1),(11,1,2,'current',1)");
        jdbc.update(
                "INSERT INTO ifj_job_definition (id,job_id,revision,name,enabled) VALUES"
                        + " (12,2,1,'child',1),(13,3,1,'old child',1)");
        jdbc.update(
                "INSERT INTO ifj_task_definition"
                    + " (id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,enabled)"
                    + " VALUES (12,2,1,'task','SCRIPT','{}',0,0,1)");
        jdbc.update("UPDATE ifj_job SET current_definition_id=11,current_revision=2 WHERE id=1");
        jdbc.update(
                "INSERT INTO ifj_job_definition_node"
                    + " (id,definition_id,node_key,name,node_type,ref_id,ref_definition_id,ref_task_definition_id)"
                    + " VALUES"
                    + " (100,10,'old','old','JOB',3,13,NULL),(101,11,'current','current','JOB',2,12,NULL),(102,11,'task','task','TASK',2,NULL,12)");
        try (var session = sessions(ds).openSession()) {
            var rows = session.getMapper(JobReferenceSnapshotMapper.class).selectJobReferences();
            assertEquals(1, rows.size());
            assertEquals(1L, rows.getFirst().jobId());
            assertEquals(2L, rows.getFirst().referencedJobId());
        }
    }

    @Test
    void realConnectionsShouldReserveDisjointRangesAcrossNodes() throws Exception {
        var ds = database("concurrent");
        migrate(ds, "35");
        var factory = sessions(ds);
        var first = generator(factory);
        var second = generator(factory);
        var values = java.util.concurrent.ConcurrentHashMap.<Long>newKeySet();
        var futures = new ArrayList<Future<?>>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int thread = 0; thread < 4; thread++) {
                var g = thread % 2 == 0 ? first : second;
                futures.add(
                        executor.submit(
                                () -> {
                                    for (int i = 0; i < 50; i++)
                                        assertTrue(values.add(g.nextId(IdNamespace.CALENDAR)));
                                }));
            }
            for (var future : futures) future.get();
        }
        assertEquals(200, values.size());
        assertEquals(201, generator(factory).nextId(IdNamespace.CALENDAR));
    }

    @Test
    void missingGlobalSeedMustStopUpgradeBeforeCreatingNamespaces() {
        var ds = database("missing-global");
        migrate(ds, "33");
        var jdbc = new JdbcTemplate(ds);
        jdbc.update("DELETE FROM ifj_id_segment WHERE segment_key='platform-primary-id'");
        assertThrows(Exception.class, () -> migrate(ds, "34"));
        assertEquals(
                0,
                scalar(jdbc, "SELECT COUNT(*) FROM ifj_id_segment WHERE segment_key LIKE 'pk:%'"));
        assertEquals(
                27,
                scalar(
                        jdbc,
                        "SELECT capability_version FROM ifj_schema_capability WHERE"
                                + " capability_key='platform-schema'"));
    }

    private DriverManagerDataSource database(String name) {
        return new DriverManagerDataSource(
                "jdbc:sqlite:"
                        + directory.resolve(name + ".db").toAbsolutePath()
                        + "?foreign_keys=on&busy_timeout=10000");
    }

    private void migrate(DriverManagerDataSource ds, String version) {
        SqliteMigrationBoundary.migrate(
                Flyway.configure()
                        .dataSource(ds)
                        .locations("classpath:db/migration/sqlite")
                        .table("ifj_schema_history")
                        .target(version)
                        .cleanDisabled(true)
                        .load());
    }

    private long scalar(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private SqlSessionFactory sessions(DriverManagerDataSource ds) throws Exception {
        var c = new Configuration(new Environment("table-id", new JdbcTransactionFactory(), ds));
        c.setDatabaseId("sqlite");
        c.setMapUnderscoreToCamelCase(true);
        for (String resource :
                new String[] {
                    "mapper/coordination/IdSegmentMapper.xml",
                    "mapper/job/JobReferenceSnapshotMapper.xml"
                }) {
            try (var input = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(input, c, resource, c.getSqlFragments()).parse();
            }
        }
        return new SqlSessionFactoryBuilder().build(c);
    }

    private DatabaseSegmentIdGenerator generator(SqlSessionFactory factory) {
        return new DatabaseSegmentIdGenerator(
                (key, size) -> {
                    try (var session = factory.openSession(false)) {
                        var range =
                                new DatabaseIdSegmentProvider(
                                                session.getMapper(IdSegmentMapper.class))
                                        .reserve(key, size);
                        session.commit();
                        return range;
                    }
                },
                new AppProperties());
    }
}
