// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeMode;
import com.ifielsc.ijob.framework.database.PlatformDatabaseResolver;
import com.ifielsc.ijob.framework.database.SqlConflictClassifier;
import com.ifielsc.ijob.framework.database.lock.SqliteSingletonLockStrategy;
import com.ifielsc.ijob.framework.runtime.SingletonDatabaseLock;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifielsc.ijob.infra.mapper.coordination.TriggerWorkItemMapper;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

class RealSqliteMigrationIT {
    private static final String BCRYPT_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5M6P5M6P5M6P5M6P5M6P5M6P5M6P5M6";

    @TempDir Path temporaryDirectory;

    @Test
    void notificationFailuresShouldPreserveLeaseFencingAndStopOnlyPermanentFailures() {
        try (var context =
                start(temporaryDirectory.resolve("notification-failures.db"), "notify-it")) {
            var service =
                    context.getBean(com.ifielsc.ijob.core.notify.NotificationOutboxService.class);
            var mapper =
                    context.getBean(
                            com.ifielsc.ijob.infra.mapper.coordination.NotificationOutboxMapper
                                    .class);
            for (var failure :
                    List.of(
                            com.ifielsc.ijob.core.notify.NotifyAuditFailure.REMOTE_REJECTED,
                            com.ifielsc.ijob.core.notify.NotifyAuditFailure.DELIVERY_TIMEOUT)) {
                var item =
                        service.enqueue(
                                71L,
                                com.ifielsc.ijob.core.notify.NotifyMessage.builder()
                                        .subject(failure.name())
                                        .content("test")
                                        .build(),
                                com.ifielsc.ijob.core.notify.NotifyDispatchContext.system());
                var claimed = service.claim(item.getId(), "notify-worker");
                assertEquals(
                        0,
                        mapper.retryOrDead(
                                item.getId(),
                                "old-owner",
                                claimed.getClaimToken(),
                                0,
                                failure.name(),
                                failure.getSummary(),
                                1));
                assertTrue(
                        service.retry(
                                claimed,
                                new com.ifielsc.ijob.core.notify.NotifyDispatchResult(
                                        com.ifielsc.ijob.core.notify.NotifyDispatchStatus
                                                .DELIVERY_FAILED,
                                        com.ifielsc.ijob.contract.enums.NotifyType.DINGTALK,
                                        failure)));
                var stored = mapper.selectByDeduplicationKey(item.getDeduplicationKey());
                assertEquals(
                        failure.isRetryable() ? "RETRY_WAIT" : "DEAD", stored.getStatus().name());
                assertEquals(1, stored.getAttemptCount());
                assertEquals(failure.name(), stored.getLastFailureCode());
                assertEquals(failure.getSummary(), stored.getLastFailureSummary());
                org.junit.jupiter.api.Assertions.assertNull(stored.getClaimToken());
                assertFalse(
                        service.retry(
                                claimed,
                                com.ifielsc.ijob.core.notify.NotifyDispatchStatus.DELIVERY_FAILED));
                if (!failure.isRetryable()) {
                    assertEquals(
                            0, mapper.claim(item.getId(), "new-worker", "new-token", 30_000_000L));
                }
            }
        }
    }

    @Test
    void emptyAbsoluteFileShouldMigrateValidateAndRecoverAfterReopen() throws Exception {
        Path database = temporaryDirectory.resolve("platform.db");
        String url = jdbcUrl(database);
        Flyway flyway = flyway(url);

        MigrateResult first = flyway.migrate();
        flyway.validate();
        MigrateResult second = flyway.migrate();

        assertEquals(1, first.migrationsExecuted);
        assertEquals(0, second.migrationsExecuted);
        assertTrue(Files.isRegularFile(database));

        try (Connection connection = connect(url)) {
            assertTrue(sqliteVersionAtLeast335(connection));
            assertEquals(
                    48,
                    scalar(
                            connection,
                            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name LIKE"
                                    + " 'ifj_%' AND name <> 'ifj_schema_history'"));
            assertEquals(
                    84,
                    scalar(
                            connection,
                            "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND (name LIKE"
                                    + " 'idx_ifj_%' OR name LIKE 'fk_ifj_%')"));
            assertEquals(
                    14,
                    scalar(
                            connection,
                            "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name LIKE"
                                    + " 'trg_ifj_%'"));
            assertEquals(43, foreignKeyCount(connection));
            assertEquals("ok", text(connection, "PRAGMA integrity_check"));
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM pragma_foreign_key_check"));
            assertEquals("TEXT", columnType(connection, "ifj_calendar", "create_time"));
            assertEquals("INTEGER", columnType(connection, "ifj_event_binding", "enabled"));
            assertThrows(
                    SQLException.class,
                    () -> query(connection, "SELECT rowid FROM ifj_task LIMIT 1"));
        }

        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(sqliteProperties());
        assertEquals(
                PlatformDatabaseVendor.SQLITE,
                resolver.verifyMigrationTarget(new DriverManagerDataSource(url)).vendor());
        new SqliteSchemaCompatibilityInspector(new DriverManagerDataSource(url)).verify();

        try (Connection reopened = connect(url)) {
            assertEquals(
                    38,
                    scalar(
                            reopened,
                            "SELECT capability_version FROM ifj_schema_capability WHERE"
                                    + " capability_key='platform-schema'"));
            assertEquals(
                    1,
                    scalar(
                            reopened,
                            "SELECT COUNT(*) FROM ifj_schema_capability "
                                    + "WHERE capability_key='execution-target-routing' "
                                    + "AND capability_version=1 AND enabled=1"));
            assertEquals(
                    1,
                    scalar(
                            reopened,
                            "SELECT COUNT(*) FROM ifj_schema_capability WHERE"
                                    + " capability_key='composite-task-concurrency' AND"
                                    + " capability_version=1 AND enabled=1"));
        }
    }

    @Test
    void populatedV20GraphShouldUpgradeToV38WithoutLosingRowsOrForeignKeys() throws Exception {
        Path database = temporaryDirectory.resolve("v20-to-v27.db");
        String url = jdbcUrl(database);
        Flyway.configure()
                .dataSource(url, "", "")
                .locations("classpath:db/migration/sqlite")
                .table("ifj_schema_history")
                .target(MigrationVersion.fromVersion("20"))
                .load()
                .migrate();

        try (Connection connection = connect(url);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO ifj_task"
                        + " (id,name,plugin_type,plugin_config,concurrency_type,lock_key) VALUES"
                        + " (1,'task','EVENT','{}','RESOURCE','db-a')");
            statement.executeUpdate(
                    "INSERT INTO ifj_task_definition "
                            + "(id,task_id,revision,name,plugin_type,plugin_config,retry_times,"
                            + "retry_interval,enabled,concurrency_type) "
                            + "VALUES (11,1,1,'task','EVENT','{}',0,0,1,'ALLOW')");
            statement.executeUpdate("INSERT INTO ifj_job (id,name) VALUES (2,'job')");
            statement.executeUpdate(
                    "INSERT INTO ifj_job_definition (id,job_id,revision,name,enabled) "
                            + "VALUES (21,2,1,'job',1)");
            statement.executeUpdate(
                    "INSERT INTO ifj_job_definition_node"
                        + " (id,definition_id,node_key,name,node_type,ref_id,ref_task_definition_id)"
                        + " VALUES (31,21,'first','First','TASK',1,11),"
                        + "(32,21,'second','Second','TASK',1,11)");
            statement.executeUpdate(
                    "INSERT INTO ifj_job_definition_node_policy "
                            + "(id,definition_id,node_id,fail_strategy,execute_condition) "
                            + "VALUES (41,21,31,'STOP','ALL_SUCCESS'),"
                            + "(42,21,32,'STOP','ALL_SUCCESS')");
            statement.executeUpdate(
                    "INSERT INTO ifj_job_definition_edge "
                            + "(id,definition_id,from_node_id,to_node_id) VALUES (51,21,31,32)");
        }

        assertEquals(17, legacyFlyway(url).migrate().migrationsExecuted);
        legacyFlyway(url).validate();

        try (Connection connection = connect(url);
                Statement statement = connection.createStatement()) {
            assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM ifj_job_definition_node"));
            assertEquals(
                    2,
                    scalar(
                            connection,
                            "SELECT COUNT(*) FROM ifj_job_definition_node "
                                    + "WHERE node_kind='EXECUTION' AND gateway_mode IS NULL"));
            assertEquals(
                    1,
                    scalar(
                            connection,
                            "SELECT COUNT(*) FROM ifj_job_definition_edge WHERE default_route=0 AND"
                                    + " route_key IS NULL"));
            assertEquals(
                    2, scalar(connection, "SELECT COUNT(*) FROM ifj_job_definition_node_policy"));
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM pragma_foreign_key_check"));
            assertEquals("TEXT", columnType(connection, "ifj_app_run", "worker_task_ids"));
            assertEquals("TEXT", columnType(connection, "ifj_app_run", "worker_job_ids"));
            assertEquals(
                    1,
                    scalar(
                            connection,
                            "SELECT COUNT(*) FROM ifj_concurrency_resource WHERE id=1 AND"
                                    + " max_concurrency=1"));
            assertEquals(
                    1,
                    scalar(
                            connection,
                            "SELECT COUNT(*) FROM ifj_task_concurrency_resource WHERE task_id=1 AND"
                                    + " resource_id=1"));
            assertEquals(
                    0,
                    scalar(
                            connection,
                            "SELECT COUNT(*) FROM pragma_table_info('ifj_task') WHERE name IN"
                                    + " ('concurrency_type','lock_key','max_concurrency')"));
            assertEquals(
                    0,
                    scalar(
                            connection,
                            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND"
                                    + " name='ifj_concurrency_policy'"));
            assertFalse(
                    text(
                                    connection,
                                    "SELECT group_concat(sql,' ') FROM sqlite_master WHERE name IN"
                                            + " ('ifj_instance','ifj_job_definition_edge',"
                                            + "'ifj_job_definition_node_policy')")
                            .contains("_v21"));
            statement.executeUpdate(
                    "INSERT INTO ifj_job_definition_node "
                            + "(id,definition_id,node_key,name,node_kind,gateway_mode) "
                            + "VALUES (33,21,'route','Route','GATEWAY','EXCLUSIVE')");
        }
    }

    @Test
    void constraintsWalBusyTimeoutAndCheckpointedBackupShouldBeOperational() throws Exception {
        Path database = temporaryDirectory.resolve("operational.db");
        String url = jdbcUrl(database);
        flyway(url).migrate();

        try (Connection connection = connect(url);
                Statement statement = connection.createStatement()) {
            try (ResultSet mode = statement.executeQuery("PRAGMA journal_mode=WAL")) {
                assertEquals("wal", mode.getString(1));
            }
            statement.execute("PRAGMA busy_timeout=5000");
            assertEquals(5000, scalar(connection, "PRAGMA busy_timeout"));
            statement.executeUpdate(
                    "INSERT INTO ifj_calendar(id,name,workdays_mask) VALUES(1,'default',31)");
            assertTrue(
                    text(connection, "SELECT create_time FROM ifj_calendar WHERE id=1")
                            .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"));
            assertThrows(
                    SQLException.class,
                    () ->
                            statement.executeUpdate(
                                    "INSERT INTO"
                                        + " ifj_event_binding(id,target_type,target_id,event_code,enabled)"
                                        + " VALUES(2,'TASK',1,'event',2)"));
            assertThrows(
                    SQLException.class,
                    () ->
                            statement.executeUpdate(
                                    "INSERT INTO ifj_calendar_exception(id,calendar_id,date,type)"
                                            + " VALUES(3,999,'2026-09-13','WORKDAY')"));
            assertThrows(
                    SQLException.class,
                    () ->
                            statement.executeUpdate(
                                    "INSERT INTO ifj_calendar(id,name,workdays_mask)"
                                            + " VALUES(-1,'bad-id',31)"));
            statement.executeQuery("PRAGMA wal_checkpoint(TRUNCATE)").close();
        }
        Path backup = temporaryDirectory.resolve("platform-backup.db");
        try (Connection backupSource = connect(url);
                Statement statement = backupSource.createStatement()) {
            statement.execute("VACUUM INTO '" + backup.toString().replace("'", "''") + "'");
            try (Connection backupConnection = connect(jdbcUrl(backup))) {
                assertEquals(1, scalar(backupConnection, "SELECT COUNT(*) FROM ifj_calendar"));
                assertEquals("ok", text(backupConnection, "PRAGMA integrity_check"));
            }
        }
    }

    @Test
    void immediateWriterShouldBoundBusyWaitAndAllowRetryWhileWalReadersContinue() throws Exception {
        Path database = temporaryDirectory.resolve("contention.db");
        String url = jdbcUrl(database);
        flyway(url).migrate();
        try (Connection seed = connect(url);
                Statement statement = seed.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.executeUpdate(
                    "INSERT INTO ifj_calendar(id,name,workdays_mask) VALUES(1,'before',31)");
        }

        try (Connection writer = connect(url);
                Connection contender = connect(url);
                Statement writerStatement = writer.createStatement();
                Statement contenderStatement = contender.createStatement()) {
            contenderStatement.execute("PRAGMA busy_timeout=200");
            writerStatement.execute("BEGIN IMMEDIATE");
            writerStatement.executeUpdate("UPDATE ifj_calendar SET name='uncommitted' WHERE id=1");
            assertEquals("before", text(contender, "SELECT name FROM ifj_calendar WHERE id=1"));

            long started = System.nanoTime();
            SQLException busy =
                    assertThrows(
                            SQLException.class,
                            () -> contenderStatement.execute("BEGIN IMMEDIATE"));
            assertInstanceOf(
                    CannotAcquireLockException.class,
                    new SqlConflictClassifier(PlatformDatabaseVendor.SQLITE)
                            .translate("begin immediate", "BEGIN IMMEDIATE", busy));
            long waitedMillis =
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(waitedMillis >= 100 && waitedMillis < 2_000);

            writerStatement.execute("ROLLBACK");
            contenderStatement.execute("BEGIN IMMEDIATE");
            contenderStatement.executeUpdate("UPDATE ifj_calendar SET name='retried' WHERE id=1");
            contenderStatement.execute("COMMIT");
        }

        try (Connection connection = connect(url)) {
            assertEquals("retried", text(connection, "SELECT name FROM ifj_calendar WHERE id=1"));
        }
    }

    @Test
    void clusterModeShouldFailBeforeLockOrDatabaseFileIsCreated() {
        Path database = temporaryDirectory.resolve("must-not-exist.db");
        String url = jdbcUrl(database);
        AppProperties properties = sqliteProperties();
        properties.getRuntime().setMode(RuntimeMode.CLUSTER);
        PlatformDatabaseResolver resolver = new PlatformDatabaseResolver(properties);
        SqliteSingletonLockStrategy fileLock = new SqliteSingletonLockStrategy(url);
        SingletonDatabaseLock singletonLock =
                new SingletonDatabaseLock(List.of(fileLock), properties);
        FlywayMigrationStrategy strategy =
                new PlatformFlywayConfiguration()
                        .platformFlywayMigrationStrategy(resolver, properties, singletonLock);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> strategy.migrate(flyway(url)));

        assertEquals("SQLite does not support cluster runtime mode", exception.getMessage());
        assertFalse(Files.exists(database));
        assertFalse(Files.exists(Path.of(database + ".ijob.lock")));
    }

    @Test
    void standaloneApplicationShouldPlanEveryMapperAndRestartAfterShutdown() throws Exception {
        Path database = temporaryDirectory.resolve("standalone.db");
        try (ConfigurableApplicationContext context = start(database, "sqlite-a")) {
            assertTrue(context.isActive());
            try (Connection connection = context.getBean(DataSource.class).getConnection()) {
                assertEquals(1, scalar(connection, "PRAGMA foreign_keys"));
                assertEquals("wal", text(connection, "PRAGMA journal_mode"));
                assertEquals(5000, scalar(connection, "PRAGMA busy_timeout"));
                assertEquals(1, scalar(connection, "PRAGMA synchronous"));
            }
            validateAllMappedStatements(context, jdbcUrl(database));
            // Exercise the interceptor installed by starter auto-configuration, not a test plugin.
            var jdbc =
                    new org.springframework.jdbc.core.JdbcTemplate(
                            context.getBean(DataSource.class));
            for (long id = 1; id <= 3; id++) {
                jdbc.update(
                        "INSERT INTO ifj_resource (id,code,name,type,config) VALUES"
                                + " (?,?,'paging','DB','{}')",
                        id,
                        "page-" + id);
            }
            var query = new com.ifielsc.ijob.contract.resource.ResourcePageQuery();
            query.setPageNum(2);
            query.setPageSize(2);
            var page =
                    context.getBean(com.ifielsc.ijob.core.resource.ResourceService.class)
                            .queryPage(query);
            assertEquals(3, page.getTotal());
            assertEquals(2, page.getPageNum());
            assertEquals(2, page.getPageSize());
            assertEquals(2, page.getPages());
            assertEquals(1, page.getList().size());
            assertEquals(1L, page.getList().getFirst().getId());
            assertTrue(
                    context
                            .getBean(SqlSessionFactory.class)
                            .getConfiguration()
                            .getInterceptors()
                            .stream()
                            .anyMatch(com.github.pagehelper.PageInterceptor.class::isInstance));
            assertInstanceOf(
                    com.ifielsc.ijob.api.auth.SaTokenAuthSession.class,
                    context.getBean(com.ifielsc.ijob.core.auth.AuthSession.class));
        }
        try (ConfigurableApplicationContext restarted = start(database, "sqlite-b")) {
            assertTrue(restarted.isActive());
            assertEquals(
                    "sqlite",
                    restarted.getBean(SqlSessionFactory.class).getConfiguration().getDatabaseId());
        }
    }

    @Test
    void executionGroupsAndTargetIdsShouldFilterBothDispatchScanAndAtomicClaim() throws Exception {
        Path database = temporaryDirectory.resolve("group-routing.db");
        try (ConfigurableApplicationContext context = start(database, "sqlite-routing")) {
            try (Connection connection = context.getBean(DataSource.class).getConnection()) {
                seedTaskRoutingWork(connection);
                seedJobRoutingWork(connection);
            }
            TriggerWorkItemMapper mapper = context.getBean(TriggerWorkItemMapper.class);

            assertEquals(
                    List.of(1L, 2L, 4L, 5L),
                    mapper.selectDispatchableTriggerIds(List.of("gpu"), List.of(), List.of(), 10));
            assertEquals(
                    List.of(2L, 5L),
                    mapper.selectDispatchableTriggerIds(
                            List.of("gpu"), List.of(2L), List.of(50L), 10));
            assertEquals(
                    List.of(1L),
                    mapper.selectDispatchableTriggerIds(
                            List.of("gpu"), List.of(1L), List.of(), 10));
            assertEquals(
                    0,
                    mapper.claimByTriggerId(
                            1L,
                            "gpu-worker",
                            "wrong-task-target",
                            30_000_000L,
                            List.of("gpu"),
                            List.of(2L),
                            List.of()));
            assertEquals(
                    0,
                    mapper.claimByTriggerId(
                            5L,
                            "gpu-worker",
                            "job-blocked-by-task-only",
                            30_000_000L,
                            List.of("gpu"),
                            List.of(1L),
                            List.of()));
            assertEquals(
                    1,
                    mapper.claimByTriggerId(
                            1L,
                            "gpu-worker",
                            "claim-1",
                            30_000_000L,
                            List.of("gpu"),
                            List.of(),
                            List.of()));
            assertEquals(
                    1,
                    mapper.claimByTriggerId(
                            2L,
                            "gpu-worker",
                            "claim-2",
                            30_000_000L,
                            List.of("gpu"),
                            List.of(),
                            List.of()));
            assertEquals(
                    0,
                    mapper.claimByTriggerId(
                            3L,
                            "gpu-worker",
                            "claim-3",
                            30_000_000L,
                            List.of("gpu"),
                            List.of(),
                            List.of()));
            assertEquals(
                    1,
                    mapper.claimByTriggerId(
                            5L,
                            "gpu-worker",
                            "claim-5",
                            30_000_000L,
                            List.of("gpu"),
                            List.of(),
                            List.of(50L)));
            assertEquals(
                    List.of(4L),
                    mapper.selectDispatchableTriggerIds(List.of(), List.of(), List.of(), 10));
        }
    }

    private void seedTaskRoutingWork(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (int id = 1; id <= 4; id++) {
                statement.executeUpdate(
                        "INSERT INTO ifj_task "
                                + "(id,name,plugin_type,plugin_config) VALUES ("
                                + id
                                + ",'route-task-"
                                + id
                                + "','EVENT','{}')");
                statement.executeUpdate(
                        "INSERT INTO ifj_task_definition "
                                + "(id,task_id,revision,name,plugin_type,plugin_config,retry_times,"
                                + "retry_interval,enabled) VALUES ("
                                + id
                                + ","
                                + id
                                + ","
                                + id
                                + ",'route-task','EVENT','{}',0,0,1)");
                statement.executeUpdate(
                        "INSERT INTO ifj_trigger "
                                + "(id,target_type,target_id,trigger_type,idempotency_key,status,"
                                + "trigger_time,task_definition_id) VALUES ("
                                + id
                                + ",'TASK',"
                                + id
                                + ",'MANUAL','route-"
                                + id
                                + "','WAITING',strftime('%Y-%m-%d %H:%M:%f','now'),"
                                + id
                                + ")");
                statement.executeUpdate(
                        "INSERT INTO ifj_trigger_work_item "
                                + "(trigger_id,status,available_at,max_attempts) VALUES ("
                                + id
                                + ",'READY',strftime('%Y-%m-%d %H:%M:%f','now'),3)");
            }
            statement.executeUpdate(
                    "INSERT INTO ifj_task_definition_group (definition_id,group_code) "
                            + "VALUES (2,'gpu'),(3,'cpu')");
        }
    }

    private void seedJobRoutingWork(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO ifj_job (id,name) VALUES (50,'route-job')");
            statement.executeUpdate(
                    "INSERT INTO ifj_job_definition (id,job_id,revision,name,enabled) "
                            + "VALUES (5,50,1,'route-job',1)");
            statement.executeUpdate(
                    "INSERT INTO ifj_trigger "
                            + "(id,target_type,target_id,trigger_type,idempotency_key,status,"
                            + "trigger_time,definition_id) VALUES "
                            + "(5,'JOB',50,'MANUAL','route-5','WAITING',"
                            + "strftime('%Y-%m-%d %H:%M:%f','now'),5)");
            statement.executeUpdate(
                    "INSERT INTO ifj_trigger_work_item "
                            + "(trigger_id,status,available_at,max_attempts) VALUES "
                            + "(5,'READY',strftime('%Y-%m-%d %H:%M:%f','now'),3)");
        }
    }

    private Flyway flyway(String url) {
        return Flyway.configure()
                .dataSource(url, "", "")
                .locations("classpath:db/release/sqlite")
                .table("ifj_schema_history")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load();
    }

    private Flyway legacyFlyway(String url) {
        return Flyway.configure()
                .dataSource(url, "", "")
                .locations("classpath:db/migration/sqlite")
                .table("ifj_schema_history")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load();
    }

    private ConfigurableApplicationContext start(Path database, String instanceName) {
        return new SpringApplicationBuilder(IJobApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .run(
                        "--spring.config.import=optional:classpath:database/sqlite.yml",
                        "--spring.datasource.url=" + jdbcUrl(database),
                        "--spring.datasource.hikari.minimum-idle=0",
                        "--spring.flyway.enabled=true",
                        "--spring.flyway.locations=classpath:db/release/sqlite",
                        "--app.platform-database.vendor=SQLITE",
                        "--app.runtime.mode=STANDALONE",
                        "--app.runtime.instance-name=" + instanceName,
                        "--app.runtime.roles=API",
                        "--app.auth.mode=STATIC",
                        "--app.auth.username=integration",
                        "--app.auth.password-hash=" + BCRYPT_HASH,
                        "--app.schedule.enabled=false",
                        "--app.event.recovery.enabled=false",
                        "--app.retention.enabled=false",
                        "--spring.jmx.enabled=false",
                        "--logging.level.root=OFF");
    }

    private void validateAllMappedStatements(ConfigurableApplicationContext context, String url)
            throws Exception {
        var configuration = context.getBean(SqlSessionFactory.class).getConfiguration();
        assertEquals("sqlite", configuration.getDatabaseId());
        List<String> statementIds =
                configuration.getMappedStatementNames().stream()
                        .filter(name -> name.startsWith("com.ifielsc.ijob.infra.mapper."))
                        .sorted()
                        .toList();
        CurrentSchemaTestSupport.assertMapperInventory(configuration, statementIds);
        Map<String, Object> parameters = planningParameters();

        try (Connection connection = connect(url)) {
            for (String statementId : statementIds) {
                MappedStatement statement = configuration.getMappedStatement(statementId);
                CurrentSchemaTestSupport.selectPlanningType(parameters, statementId);
                BoundSql boundSql = statement.getBoundSql(parameters);
                try (PreparedStatement explain =
                        connection.prepareStatement("EXPLAIN " + boundSql.getSql())) {
                    new DefaultParameterHandler(statement, parameters, boundSql)
                            .setParameters(explain);
                    explain.execute();
                } catch (Exception exception) {
                    throw new IllegalStateException("SQLite rejected " + statementId, exception);
                }
            }
        }
    }

    private Map<String, Object> planningParameters() {
        Map<String, Object> values = new HashMap<>();
        for (String name :
                List.of(
                        "id",
                        "jobId",
                        "taskId",
                        "definitionId",
                        "nodeId",
                        "fromNodeId",
                        "toNodeId",
                        "calendarId",
                        "notifyConfigId",
                        "instanceId",
                        "rootInstanceId",
                        "parentInstanceId",
                        "triggerId",
                        "eventId",
                        "waitId",
                        "lastInstanceId",
                        "expectedDefinitionId",
                        "expectedRevision",
                        "expectedVersion",
                        "fenceToken",
                        "executionFence",
                        "permitFence",
                        "initialValue",
                        "leaseMicros",
                        "timeoutMicros",
                        "timeoutSeconds",
                        "retryDelayMicros",
                        "delayMicros",
                        "staleMicros",
                        "staleRunMicros",
                        "orphanGraceMicros")) {
            values.put(name, 1L);
        }
        for (String name :
                List.of(
                        "slotNo",
                        "progress",
                        "limit",
                        "size",
                        "start",
                        "maxAttempts",
                        "segmentSize",
                        "attemptCount")) {
            values.put(name, 1);
        }
        for (String name :
                List.of(
                        "createTime",
                        "updateTime",
                        "startTime",
                        "endTime",
                        "triggerTime",
                        "arrivedAt",
                        "timeoutAt",
                        "availableAt",
                        "cutoff",
                        "now",
                        "acceptAfter",
                        "expectedNextFireTime",
                        "lastFireTime",
                        "nextFireTime",
                        "retiredTime")) {
            values.put(name, LocalDateTime.of(2026, 1, 1, 0, 0));
        }
        for (String name :
                List.of(
                        "ids",
                        "targetIds",
                        "calendarIds",
                        "notifyConfigIds",
                        "definitionIds",
                        "triggerIds",
                        "instanceIds",
                        "jobInstanceIds")) {
            values.put(name, new ArrayList<>(List.of(1L)));
        }
        values.put("ascending", true);
        values.put("enabled", true);
        values.put("retired", false);
        values.put("success", true);
        values.put("configEnabled", true);
        values.put("name", "");
        values.put("code", "");
        values.put("keyword", "");
        values.put("prefix", "ijob-it:");
        values.put("businessKey", "business-it");
        values.put("correlationKey", "correlation-it");
        values.put("operatorId", "operator-it");
        values.put("loginIds", List.of("operator-it"));
        values.put(
                "category", com.ifielsc.ijob.contract.diagnostics.DiagnosticCategory.LEASE_EXPIRED);
        values.put("type", com.ifielsc.ijob.contract.enums.TargetType.TASK);
        values.put("groupCode", "default");
        values.put("instanceGroups", List.of("default"));
        values.put("allowedTaskIds", List.of(1L));
        values.put("allowedJobIds", List.of(1L));
        values.put("targetId", null);
        values.put("permit", ExecutionFencePermit.unfenced());
        values.put("user", values);
        values.put("query", values);
        values.put("schedule", values);
        values.put("checkpoint", values);
        values.put("instance", values);
        return values;
    }

    private Connection connect(String url) throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private String jdbcUrl(Path database) {
        return "jdbc:sqlite:" + database.toAbsolutePath().normalize();
    }

    private AppProperties sqliteProperties() {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.STANDALONE);
        properties.getPlatformDatabase().setVendor(PlatformDatabaseVendor.SQLITE);
        return properties;
    }

    private boolean sqliteVersionAtLeast335(Connection connection) throws SQLException {
        String[] parts = text(connection, "SELECT sqlite_version()").split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        return major > 3 || (major == 3 && minor >= 35);
    }

    private int foreignKeyCount(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE"
                                        + " 'ifj_%' AND name <> 'ifj_schema_history'")) {
            while (result.next()) {
                tables.add(result.getString(1));
            }
        }
        int count = 0;
        for (String table : tables) {
            count +=
                    scalar(
                            connection,
                            "SELECT COUNT(*) FROM pragma_foreign_key_list('" + table + "')");
        }
        return count;
    }

    private String columnType(Connection connection, String table, String column)
            throws SQLException {
        return text(
                connection,
                "SELECT type FROM pragma_table_info('" + table + "') WHERE name='" + column + "'");
    }

    private int scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new SQLException("Query returned no row");
            }
            return result.getInt(1);
        }
    }

    private String text(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new SQLException("Query returned no row");
            }
            return result.getString(1);
        }
    }

    private void query(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            // Query execution itself is the assertion target.
        }
    }
}
