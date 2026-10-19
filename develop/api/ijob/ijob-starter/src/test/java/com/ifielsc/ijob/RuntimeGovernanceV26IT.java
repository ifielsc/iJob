package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.enums.EventSourceType;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.enums.RuntimeCommandType;
import com.ifielsc.ijob.contract.runtime.InstancePageQuery;
import com.ifielsc.ijob.core.event.EventBindingService;
import com.ifielsc.ijob.core.event.EventPublishCommand;
import com.ifielsc.ijob.core.event.EventReceiptService;
import com.ifielsc.ijob.core.event.EventWaitResolutionService;
import com.ifielsc.ijob.core.retention.RuntimeDataRetentionService;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionResolver;
import com.ifielsc.ijob.core.task.retirement.TaskRetirementService;
import com.ifielsc.ijob.core.trigger.TriggerCommandDispatcher;
import com.ifielsc.ijob.core.trigger.TriggerRuntimeService;
import com.ifielsc.ijob.core.trigger.TriggerService;
import com.ifielsc.ijob.infra.coordination.RuntimeMutationGuard;
import com.ifielsc.ijob.infra.coordination.command.DatabaseRuntimeCommandQueue;
import com.ifielsc.ijob.infra.coordination.work.DatabaseTriggerWorkQueue;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifielsc.ijob.infra.coordination.work.TriggerWorkClaim;
import com.ifielsc.ijob.infra.mapper.coordination.ConcurrencyPermitMapper;
import com.ifielsc.ijob.infra.mapper.coordination.RuntimeCommandMapper;
import com.ifielsc.ijob.infra.mapper.coordination.RuntimeGuardMapper;
import com.ifielsc.ijob.infra.mapper.coordination.TriggerWorkItemMapper;
import com.ifielsc.ijob.infra.mapper.event.EventBindingMapper;
import com.ifielsc.ijob.infra.mapper.runtime.DagGatewayDecisionMapper;
import com.ifielsc.ijob.infra.mapper.runtime.EventRecordMapper;
import com.ifielsc.ijob.infra.mapper.runtime.InstanceLogMapper;
import com.ifielsc.ijob.infra.mapper.runtime.InstanceMapper;
import com.ifielsc.ijob.infra.mapper.runtime.RuntimeDataRetentionMapper;
import com.ifielsc.ijob.infra.mapper.runtime.TriggerMapper;
import com.ifielsc.ijob.infra.mapper.schedule.ScheduleMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskDefinitionResourceReferenceMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskMapper;
import com.ifielsc.ijob.infra.mapper.task.TaskPublicationMapper;
import com.ifielsc.ijob.infra.mybatis.AutoIdInterceptor;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Opt-in integration suite. Creates a NEW isolated target; never drops or reuses a database. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuntimeGovernanceV26IT {
    private String vendor;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private SqlSessionTemplate sessions;
    private TransactionTemplate transaction;
    private RuntimeMutationGuard guard;
    private final AtomicLong ids = new AtomicLong(900000);
    private final java.util.concurrent.atomic.AtomicBoolean failNextConnection =
            new java.util.concurrent.atomic.AtomicBoolean();

    @BeforeAll
    void provisionAndUpgrade() throws Exception {
        vendor = System.getenv("IJOB_AUDIT_VENDOR");
        Assumptions.assumeTrue(vendor != null, "Use tools/run-runtime-v26-audit.ps1");
        String run = System.getenv("IJOB_AUDIT_RUN_ID");
        if (run == null || !run.matches("[0-9]{12}"))
            throw new IllegalArgumentException("Invalid run ID");
        String name = "ijob_it_a26_" + run;
        String adminPassword = System.getenv("IJOB_AUDIT_ADMIN_PASSWORD");
        String user;
        String password;
        String url;
        if (vendor.equals("sqlite")) {
            url =
                    "jdbc:sqlite:"
                            + Files.createTempDirectory("ijob-v26-" + run).resolve("audit.db")
                            // Match database/sqlite.yml: SQLite serializes writers at BEGIN,
                            // rather than emulating row-level SELECT FOR UPDATE.
                            + "?foreign_keys=on&busy_timeout=10000&journal_mode=WAL&transaction_mode=IMMEDIATE";
            user = "";
            password = "";
        } else if (vendor.equals("oracle")) {
            url = "jdbc:oracle:thin:@//127.0.0.1:1521/ORCL";
            user = name.toUpperCase(java.util.Locale.ROOT);
            password = System.getenv("IJOB_AUDIT_TEST_PASSWORD");
            try (Connection admin =
                            DriverManager.getConnection(url, "sys as sysdba", adminPassword);
                    Statement statement = admin.createStatement()) {
                // Do not propagate SQL text containing the temporary user's credential.
                try {
                    statement.executeUpdate(
                            "CREATE USER "
                                    + user
                                    + " IDENTIFIED BY \""
                                    + password.replace("\"", "\"\"")
                                    + "\" DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS");
                } catch (SQLException exception) {
                    throw new IllegalStateException(
                            "Isolated Oracle user creation failed; SQL code "
                                    + exception.getErrorCode());
                }
                statement.executeUpdate(
                        "GRANT CREATE SESSION, CREATE TABLE, CREATE TRIGGER, CREATE VIEW TO "
                                + user);
            }
        } else {
            String adminUrl;
            switch (vendor) {
                case "mysql" -> {
                    user = "root";
                    adminUrl =
                            "jdbc:mysql://127.0.0.1:3306/mysql?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
                    url = adminUrl.replace("/mysql?", "/" + name + "?");
                }
                case "postgresql" -> {
                    user = "postgres";
                    adminUrl = "jdbc:postgresql://127.0.0.1:5432/postgres";
                    url = "jdbc:postgresql://127.0.0.1:5432/" + name;
                }
                case "sqlserver" -> {
                    user = "sa";
                    adminUrl =
                            "jdbc:sqlserver://127.0.0.1:1433;databaseName=master;encrypt=true;trustServerCertificate=true";
                    url = adminUrl.replace("databaseName=master", "databaseName=" + name);
                }
                default -> throw new IllegalArgumentException("Unsupported vendor");
            }
            password = adminPassword;
            try (Connection admin = DriverManager.getConnection(adminUrl, user, password);
                    Statement statement = admin.createStatement()) {
                statement.executeUpdate(
                        "CREATE DATABASE "
                                + name
                                + (vendor.equals("mysql") ? " CHARACTER SET utf8mb4" : ""));
            }
        }
        dataSource =
                new DriverManagerDataSource(url, user, password) {
                    @Override
                    public Connection getConnection() throws SQLException {
                        if (failNextConnection.getAndSet(false))
                            throw new SQLException("Injected transient connection outage", "08006");
                        return super.getConnection();
                    }
                };
        jdbc = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            // Deliberately omit URL, username and properties from evidence.
            System.out.printf(
                    "DATABASE_EVIDENCE vendor=%s product=%s version=%s driver=%s driverVersion=%s"
                            + " isolation=%d%n",
                    vendor,
                    metadata.getDatabaseProductName(),
                    metadata.getDatabaseProductVersion().replaceAll("[\\r\\n]+", " "),
                    metadata.getDriverName(),
                    metadata.getDriverVersion(),
                    connection.getTransactionIsolation());
        }
        // Verify the target before any migration or fixture writes.
        if (!vendor.equals("sqlite")) {
            String actual =
                    jdbc.queryForObject(
                            switch (vendor) {
                                case "mysql" -> "SELECT DATABASE()";
                                case "postgresql" -> "SELECT current_database()";
                                case "sqlserver" -> "SELECT DB_NAME()";
                                default -> "SELECT USER FROM dual";
                            },
                            String.class);
            assertEquals(name.toLowerCase(), actual.toLowerCase());
        }
        flyway("25").migrate();
        // A pre-existing row must gain a usable revision during upgrade.
        jdbc.update(
                "INSERT INTO ifj_task (id,name,plugin_type,plugin_config) VALUES"
                        + " (1,'audit','EVENT','{}')");
        jdbc.update(
                "INSERT INTO ifj_schedule"
                    + " (id,target_type,target_id,schedule_type,interval_sec,next_fire_time) VALUES"
                    + " (1,'TASK',1,'INTERVAL',60,?)",
                time(LocalDateTime.of(2020, 1, 1, 0, 0)));
        assertEquals(vendor.equals("sqlite") ? 12 : 13, flyway(null).migrate().migrationsExecuted);
        flyway(null).validate();
        if (vendor.equals("mysql")) {
            assertEquals(
                    "InnoDB",
                    jdbc.queryForObject(
                            "SELECT ENGINE FROM information_schema.tables WHERE"
                                    + " table_schema=DATABASE() AND table_name='ifj_runtime_guard'",
                            String.class));
        }
        assertEquals(0, flyway(null).migrate().migrationsExecuted);
        assertEquals(
                38,
                jdbc.queryForObject(
                        "SELECT capability_version FROM ifj_schema_capability WHERE"
                                + " capability_key='platform-schema'",
                        Integer.class));
        assertEquals(
                0L,
                jdbc.queryForObject("SELECT revision FROM ifj_schedule WHERE id=1", Long.class));
        switch (vendor) {
            case "mysql" -> new MySqlSchemaCompatibilityInspector(dataSource).verify();
            case "postgresql" -> new PostgreSqlSchemaCompatibilityInspector(dataSource).verify();
            case "sqlserver" -> new SqlServerSchemaCompatibilityInspector(dataSource).verify();
            case "oracle" -> new OracleSchemaCompatibilityInspector(dataSource).verify();
            default -> new SqliteSchemaCompatibilityInspector(dataSource).verify();
        }
        Configuration configuration = new Configuration();
        configuration.setDatabaseId(vendor);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        if (vendor.equals("oracle"))
            configuration.setJdbcTypeForNull(org.apache.ibatis.type.JdbcType.NULL);
        if (vendor.equals("sqlite"))
            configuration
                    .getTypeHandlerRegistry()
                    .register(
                            LocalDateTime.class,
                            new com.ifielsc.ijob.framework.database.typehandler
                                    .SqliteLocalDateTimeTypeHandler());
        configuration.addInterceptor(new AutoIdInterceptor(namespace -> ids.incrementAndGet()));
        var pagination = new com.github.pagehelper.PageInterceptor();
        var paginationProperties = new java.util.Properties();
        paginationProperties.setProperty(
                "helperDialect", vendor.equals("sqlserver") ? "sqlserver2012" : vendor);
        pagination.setProperties(paginationProperties);
        configuration.addInterceptor(pagination);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/**/*.xml"));
        sessions = new SqlSessionTemplate(factory.getObject());
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        guard = new RuntimeMutationGuard(sessions.getMapper(RuntimeGuardMapper.class));
        System.out.println("V26 isolated target verified: " + vendor + " / " + name);
    }

    private Flyway flyway(String target) {
        var configuration =
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration/" + vendor)
                        .table("ifj_schema_history")
                        .cleanDisabled(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    @Test
    void resourceDeletionMustCheckLateHistoricalReferencesWithRealPagination() {
        long resource = ids.incrementAndGet();
        long task = publishedTask(true);
        jdbc.update(
                "INSERT INTO ifj_resource (id,code,name,type,config) VALUES"
                        + " (?,?,'audit','DB','{}')",
                resource,
                "resource-" + resource);
        long start = ids.addAndGet(2000);
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (int index = 0; index < 1001; index++) {
            rows.add(
                    new Object[] {
                        start + index, task, index + 2, index == 1000 ? "ref-" + resource : "none"
                    });
        }
        ids.addAndGet(1100);
        jdbc.batchUpdate(
                "INSERT INTO ifj_task_definition"
                    + " (id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,enabled)"
                    + " VALUES (?,?,?,'audit','SQL',?,0,0,?)",
                rows.stream()
                        .map(row -> new Object[] {row[0], row[1], row[2], row[3], true})
                        .toList());
        var resourceMapper = mapper(com.ifielsc.ijob.infra.mapper.resource.ResourceMapper.class);
        var validator = resourceReferenceValidator(resource, () -> {});
        var validation =
                new com.ifielsc.ijob.core.task.definition
                        .TaskPluginConfigPublicationValidationService(
                        java.util.List.of(validator), resourceMapper, null, null);
        var service =
                new com.ifielsc.ijob.core.resource.ResourceService(
                        resourceMapper,
                        null,
                        mapper(TaskDefinitionResourceReferenceMapper.class),
                        validation,
                        event -> {});
        var failure =
                assertThrows(
                        BusinessException.class,
                        () ->
                                tx(
                                        () -> {
                                            service.delete(resource);
                                            return null;
                                        }));
        assertEquals(com.ifielsc.ijob.common.response.ResultCode.CONFLICT, failure.getCode());
        assertNotNull(resourceMapper.selectById(resource));
        assertNull(com.github.pagehelper.PageHelper.getLocalPage());
        // Remove only this test's synthetic history; immutable production history is untouched.
        jdbc.update("DELETE FROM ifj_task_definition WHERE id>=? AND id<=?", start, start + 1000);
        tx(
                () -> {
                    service.delete(resource);
                    return null;
                });
        assertNull(resourceMapper.selectById(resource));
    }

    @Test
    void resourcePublicationAndDeletionKeepTheSameRowLockProtocol() throws Exception {
        long resource = ids.incrementAndGet();
        long task = publishedTask(true);
        jdbc.update(
                "INSERT INTO ifj_resource (id,code,name,type,config) VALUES"
                        + " (?,?,'audit','DB','{}')",
                resource,
                "resource-" + resource);
        var resources = mapper(com.ifielsc.ijob.infra.mapper.resource.ResourceMapper.class);
        var validation =
                new com.ifielsc.ijob.core.task.definition
                        .TaskPluginConfigPublicationValidationService(
                        java.util.List.of(resourceReferenceValidator(resource, () -> {})),
                        resources,
                        null,
                        null);
        var service =
                new com.ifielsc.ijob.core.resource.ResourceService(
                        resources,
                        null,
                        mapper(TaskDefinitionResourceReferenceMapper.class),
                        validation,
                        event -> {});
        long definition = ids.incrementAndGet();
        race(
                () -> {
                    // Actual publication validation obtains resource row locks in this transaction.
                    validation.validate(PluginType.SQL, "ref-" + resource, null);
                    jdbc.update(
                            "INSERT INTO ifj_task_definition"
                                + " (id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,enabled)"
                                + " VALUES (?,?,2,'audit','SQL',?,0,0,?)",
                            definition,
                            task,
                            "ref-" + resource,
                            true);
                    return null;
                },
                () -> {
                    assertEquals(
                            com.ifielsc.ijob.common.response.ResultCode.CONFLICT,
                            assertThrows(BusinessException.class, () -> service.delete(resource))
                                    .getCode());
                    return null;
                });
        assertNotNull(resources.selectById(resource));
        long unused = ids.incrementAndGet();
        jdbc.update(
                "INSERT INTO ifj_resource (id,code,name,type,config) VALUES"
                        + " (?,?,'audit','DB','{}')",
                unused,
                "resource-" + unused);
        var newValidation =
                new com.ifielsc.ijob.core.task.definition
                        .TaskPluginConfigPublicationValidationService(
                        java.util.List.of(resourceReferenceValidator(unused, () -> {})),
                        resources,
                        null,
                        null);
        race(
                () -> {
                    service.delete(unused);
                    return null;
                },
                () -> {
                    assertEquals(
                            com.ifielsc.ijob.common.response.ResultCode.NOT_FOUND,
                            assertThrows(
                                            BusinessException.class,
                                            () ->
                                                    newValidation.validate(
                                                            PluginType.SQL, "ref-" + unused, null))
                                    .getCode());
                    return null;
                });
        assertNull(resources.selectById(unused));
        long validatedOnly = ids.incrementAndGet();
        jdbc.update(
                "INSERT INTO ifj_resource (id,code,name,type,config) VALUES"
                        + " (?,?,'audit','DB','{}')",
                validatedOnly,
                "resource-" + validatedOnly);
        var validationOnly =
                new com.ifielsc.ijob.core.task.definition
                        .TaskPluginConfigPublicationValidationService(
                        java.util.List.of(resourceReferenceValidator(validatedOnly, () -> {})),
                        resources,
                        null,
                        null);
        // Validation must hold the resource lock before any definition INSERT. Otherwise a
        // later write/read lock on the definition table can accidentally make the race pass.
        race(
                () -> {
                    validationOnly.validate(PluginType.SQL, "ref-" + validatedOnly, null);
                    return null;
                },
                () -> {
                    service.delete(validatedOnly);
                    return null;
                });
        assertNull(resources.selectById(validatedOnly));
    }

    private com.ifielsc.ijob.core.task.definition.TaskPluginConfigPublicationValidator
            resourceReferenceValidator(long resource, Runnable onParse) {
        return new com.ifielsc.ijob.core.task.definition.TaskPluginConfigPublicationValidator() {
            public java.util.Set<PluginType> supportTypes() {
                return java.util.Set.of(PluginType.SQL);
            }

            public void validate(
                    PluginType type,
                    String config,
                    com.ifielsc.ijob.core.task.definition.TaskPluginConfigPublicationContext
                            context) {}

            public java.util.Set<Long> referencedResourceIds(PluginType type, String config) {
                onParse.run();
                return config.equals("ref-" + resource)
                        ? java.util.Set.of(resource)
                        : java.util.Set.of();
            }
        };
    }

    @Test
    void boundedTreeAndLobProjectionMustWorkWithRealVendorPagination() {
        long root = ids.addAndGet(3000);
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (int index = 0; index < 2001; index++) rows.add(new Object[] {root + index, root});
        ids.addAndGet(2100);
        jdbc.batchUpdate(
                "INSERT INTO ifj_instance (id,root_instance_id,target_type,target_id,status) VALUES"
                        + " (?,?,'JOB',1,'FAILED')",
                rows);
        jdbc.update(
                "UPDATE ifj_instance SET input_variables=?,output_variables=? WHERE id=?",
                "x".repeat(4097),
                "{\"small\":true,\"password\":\"synthetic-private\"}",
                root);
        var service =
                new com.ifielsc.ijob.core.instance.RuntimeQueryService(
                        sessions.getMapper(TriggerMapper.class),
                                sessions.getMapper(InstanceMapper.class),
                        sessions.getMapper(InstanceLogMapper.class),
                                sessions.getMapper(DagGatewayDecisionMapper.class));
        var summary = service.getRunTree(root, false);
        assertEquals(2000, summary.getNodes().size());
        assertTrue(summary.isTruncated());
        var preview = service.getRunTree(root, true);
        assertEquals(250, preview.getNodes().size());
        assertTrue(preview.isTruncated());
        var first = preview.getNodes().getFirst();
        assertTrue(first.isVariablesTruncated());
        assertNull(first.getInputVariables());
        assertEquals("{\"small\":true,\"password\":\"[REDACTED]\"}", first.getOutputVariables());
        assertTrue(first.isVariablesRedacted());
        assertNull(com.github.pagehelper.PageHelper.getLocalPage());
    }

    @Test
    void instanceDtoAndSummaryMustExcludeVariablesButRetainTimeout() {
        long id = ids.incrementAndGet();
        LocalDateTime timeout = LocalDateTime.of(2026, 9, 19, 12, 0);
        jdbc.update(
                "INSERT INTO ifj_instance"
                    + " (id,root_instance_id,target_type,target_id,status,timeout_at,input_variables,output_variables)"
                    + " VALUES (?,?,'TASK',1,'FAILED',?,?,?)",
                id,
                id,
                time(timeout),
                "{\"private\":1}",
                "{\"private\":2}");
        var mapper = sessions.getMapper(InstanceMapper.class);
        var query = new InstancePageQuery();
        query.setRootInstanceId(id);
        var dto = mapper.selectDtoList(query).getFirst();
        assertEquals(timeout, dto.getTimeoutAt());
        assertNull(dto.getInputVariables());
        assertNull(dto.getOutputVariables());
        assertNull(mapper.selectSummaryById(id).getInputVariables());
        assertNull(mapper.selectSummaryTreeByRootId(id).getFirst().getOutputVariables());
        assertNotNull(mapper.selectTreeByRootId(id).getFirst().getInputVariables());
    }

    @Test
    void scheduleScannerAndEditorsMustNotOverwriteEachOther() {
        var mapper = sessions.getMapper(ScheduleMapper.class);
        var stale = mapper.selectById(1L);
        var winner = mapper.selectById(1L);
        winner.setIntervalSec(90);
        assertEquals(1, mapper.updateById(winner));
        stale.setEnabled(false);
        assertEquals(0, mapper.updateEnabledById(stale));
        var beforeClaim = mapper.selectById(1L);
        assertEquals(1, mapper.claimDueForTrigger(1L, "audit-claim"));
        beforeClaim.setIntervalSec(120);
        assertEquals(0, mapper.updateById(beforeClaim));
        var claimed = mapper.selectById(1L);
        assertEquals(90, claimed.getIntervalSec());
        assertEquals("audit-claim", claimed.getFireClaimToken());
        var expected = claimed.getNextFireTime();
        claimed.setNextFireTime(LocalDateTime.of(2030, 1, 1, 0, 0));
        claimed.setLastFireTime(LocalDateTime.of(2026, 9, 19, 0, 0));
        assertEquals(1, mapper.updateFireTimeIfClaimed(claimed, expected, "audit-claim"));
        assertEquals(3L, mapper.selectById(1L).getRevision());
        assertNull(mapper.selectById(1L).getFireClaimToken());
    }

    @Test
    void concurrencyGuardMustBlockOtherConnectionAndPublishNewEpoch() throws Exception {
        assertSerialized(RuntimeMutationGuard.CONCURRENCY);
    }

    @Test
    void lifecycleGuardMustBlockRetryUntilCleanupTransactionEnds() throws Exception {
        assertSerialized(RuntimeMutationGuard.LIFECYCLE);
    }

    private void assertSerialized(String key) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch entered = new CountDownLatch(1);
            Future<Long> first =
                    pool.submit(
                            () ->
                                    tx(
                                            () -> {
                                                long epoch = guard.lock(key);
                                                locked.countDown();
                                                await(release);
                                                return epoch;
                                            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            Future<Long> second =
                    pool.submit(
                            () -> {
                                entered.countDown();
                                return tx(() -> guard.lock(key));
                            });
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            assertEquals(first.get(15, TimeUnit.SECONDS) + 1, second.get(15, TimeUnit.SECONDS));
        }
    }

    @Test
    void identityAdmissionLockMustActuallyBlockRetirement() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch entered = new CountDownLatch(1);
            Future<?> first =
                    pool.submit(
                            () ->
                                    tx(
                                            () -> {
                                                assertNotNull(
                                                        sessions.getMapper(
                                                                        TaskPublicationMapper.class)
                                                                .selectIdentityForUpdate(1L));
                                                locked.countDown();
                                                await(release);
                                                return null;
                                            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            Future<Integer> second =
                    pool.submit(
                            () -> {
                                entered.countDown();
                                return tx(
                                        () -> {
                                            return jdbc.update(
                                                    "UPDATE ifj_task SET"
                                                            + " retired=?,retired_time=? WHERE"
                                                            + " id=1",
                                                    true,
                                                    time(LocalDateTime.now()));
                                        });
                            });
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            first.get(15, TimeUnit.SECONDS);
            assertEquals(1, second.get(15, TimeUnit.SECONDS));
        }
    }

    @Test
    void distributedGrowthOldSnapshotAndStaleFenceMustRemainBounded() {
        var properties = new AppProperties();
        properties.getRuntime().setMode(AppProperties.RuntimeMode.STANDALONE);
        var fences = new ExecutionFenceCoordinator(properties);
        var permits =
                new com.ifielsc.ijob.infra.coordination.permit.DatabaseConcurrencyPermitService(
                        mapper(ConcurrencyPermitMapper.class),
                        mapper(InstanceMapper.class),
                        fences,
                        properties);
        long first = claimedRoot(fences);
        long second = claimedRoot(fences);
        long third = claimedRoot(fences);
        String scope = "audit-growth-" + first;
        var old = tx(() -> permits.tryAcquire(first, scope, 1));
        var grown = tx(() -> permits.tryAcquire(second, scope, 2));
        assertNotNull(old);
        assertNotNull(grown);
        assertNull(tx(() -> permits.tryAcquire(third, scope, 1)));
        assertNull(tx(() -> permits.tryAcquire(third, scope, 2)));
        assertTrue(tx(() -> permits.release(old)));
        var resumed = tx(() -> permits.tryAcquire(third, scope, 1));
        assertNotNull(resumed);
        assertFalse(tx(() -> permits.release(old)));
        assertEquals(2, mapper(ConcurrencyPermitMapper.class).countActiveByScope(scope));
        jdbc.update(
                "UPDATE ifj_trigger_work_item SET fence_token=fence_token+1 WHERE trigger_id=?",
                third);
        assertFalse(tx(() -> permits.renew(resumed)));
        assertTrue(tx(() -> permits.release(resumed)));
        assertNull(tx(() -> permits.tryAcquire(third, scope, 2)));
        assertTrue(tx(() -> permits.release(grown)));
        assertEquals(0, mapper(ConcurrencyPermitMapper.class).countActiveByScope(scope));
    }

    @Test
    void connectionRecoveryAndWorkerTakeoverMustRejectTheOldOwner() {
        var properties = new AppProperties();
        properties.getRuntime().setMode(AppProperties.RuntimeMode.STANDALONE);
        var queue = new DatabaseTriggerWorkQueue(mapper(TriggerWorkItemMapper.class), properties);
        long task = publishedTask(true);
        long trigger = waitingTrigger(task).getId();
        tx(
                () -> {
                    queue.enqueue(trigger);
                    return null;
                });
        failNextConnection.set(true);
        assertThrows(
                org.springframework.transaction.CannotCreateTransactionException.class,
                () -> tx(() -> queue.claim(trigger, "worker-a")));
        var first = tx(() -> queue.claim(trigger, "worker-a"));
        assertNotNull(first);
        jdbc.update(
                "UPDATE ifj_trigger_work_item SET lease_deadline=? WHERE trigger_id=?",
                time(LocalDateTime.of(2020, 1, 1, 0, 0)),
                trigger);
        var takeover = tx(() -> queue.claim(trigger, "worker-b"));
        assertNotNull(takeover);
        assertTrue(takeover.fenceToken() > first.fenceToken());
        assertNotEquals(first.claimToken(), takeover.claimToken());
        assertFalse(tx(() -> queue.renew(first)));
        assertFalse(tx(() -> queue.complete(first)));
        assertTrue(tx(() -> queue.renew(takeover)));
        assertTrue(tx(() -> queue.complete(takeover)));
    }

    @Test
    void reviewResourcePageFiltersAndContextAggregationUseSelectedIds() {
        long first = ids.incrementAndGet(),
                second = ids.incrementAndGet(),
                excluded = ids.incrementAndGet();
        for (Object[] row :
                java.util.List.of(
                        new Object[] {first, "review_b", true},
                        new Object[] {second, "review_a", true},
                        new Object[] {excluded, "reviewXc", true},
                        new Object[] {ids.incrementAndGet(), "review_disabled", false})) {
            jdbc.update(
                    "INSERT INTO ifj_concurrency_resource(id,code,name,max_concurrency,enabled)"
                            + " VALUES(?,?,'page fixture',1,?)",
                    row);
        }
        var query = new com.ifielsc.ijob.contract.concurrency.ConcurrencyResourcePageQuery();
        query.setKeyword("%review!_%");
        query.setEnabled(true);
        var resources =
                mapper(com.ifielsc.ijob.infra.mapper.concurrency.ConcurrencyResourceMapper.class)
                        .selectPage(query);
        assertEquals(
                java.util.List.of(second, first),
                resources.stream()
                        .map(com.ifielsc.ijob.infra.entity.concurrency.ConcurrencyResource::getId)
                        .toList());
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_task_concurrency_policy WHERE task_id=1",
                        Integer.class)
                == 0)
            jdbc.update(
                    "INSERT INTO ifj_task_concurrency_policy(task_id,task_max_concurrency)"
                            + " VALUES(1,10)");
        jdbc.update(
                "INSERT INTO ifj_task_concurrency_resource(task_id,resource_id) VALUES(1,?)",
                first);
        claimedRoot(new ExecutionFenceCoordinator(new AppProperties()));
        var contexts =
                mapper(com.ifielsc.ijob.infra.mapper.concurrency.ConcurrencyContextMapper.class)
                        .selectResourceContexts(java.util.List.of(first, second));
        assertEquals(2, contexts.size());
        var bound =
                contexts.stream()
                        .filter(value -> value.getId().equals(first))
                        .findFirst()
                        .orElseThrow();
        assertEquals(1, bound.getReferenceCount());
        assertTrue(bound.getActiveInstanceCount() >= 1);
        var unbound =
                contexts.stream()
                        .filter(value -> value.getId().equals(second))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, unbound.getReferenceCount());
        assertEquals(0, unbound.getActiveInstanceCount());
        assertTrue(contexts.stream().noneMatch(value -> value.getId().equals(excluded)));
    }

    @Test
    void reviewAllCurrentMapperStatementsPlanOnServer() {
        switch (vendor) {
            case "oracle" ->
                    new RealOracleMigrationIT()
                            .validateAllMappedStatements(sessions.getConfiguration(), dataSource);
            case "postgresql" ->
                    new RealPostgresqlMigrationIT()
                            .validateAllMappedStatements(sessions.getConfiguration(), dataSource);
            case "sqlserver" ->
                    new RealSqlServerMigrationIT()
                            .validateAllMappedStatements(sessions.getConfiguration(), dataSource);
            default ->
                    Assumptions.assumeTrue(
                            false,
                            "SQLite full planning covered by RealSqliteMigrationIT; MySQL by"
                                    + " execution tests");
        }
    }

    @Test
    void reviewCurrentSchemaAndMapperInventory() {
        var configuration = sessions.getConfiguration();
        var statements =
                configuration.getMappedStatementNames().stream()
                        .filter(name -> name.startsWith("com.ifielsc.ijob.infra.mapper."))
                        .sorted()
                        .toList();
        CurrentSchemaTestSupport.assertMapperInventory(configuration, statements);
        String tableCount =
                switch (vendor) {
                    case "oracle" ->
                            "SELECT COUNT(*) FROM user_tables WHERE table_name LIKE 'IFJ_%' AND"
                                    + " table_name <> 'IFJ_SCHEMA_HISTORY'";
                    case "sqlserver" ->
                            "SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo') AND"
                                    + " name LIKE 'ifj[_]%' AND name <> 'ifj_schema_history'";
                    case "postgresql" ->
                            "SELECT COUNT(*) FROM information_schema.tables WHERE"
                                + " table_schema=current_schema() AND table_name LIKE 'ifj_%' AND"
                                + " table_name <> 'ifj_schema_history'";
                    case "mysql" ->
                            "SELECT COUNT(*) FROM information_schema.tables WHERE"
                                    + " table_schema=DATABASE() AND table_name LIKE 'ifj_%' AND"
                                    + " table_name <> 'ifj_schema_history'";
                    default ->
                            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name LIKE"
                                    + " 'ifj_%' AND name <> 'ifj_schema_history'";
                };
        assertEquals(47, jdbc.queryForObject(tableCount, Integer.class));
        var counts = new java.util.LinkedHashMap<String, Integer>();
        if (vendor.equals("postgresql")) {
            counts.put(
                    "unique",
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM information_schema.table_constraints WHERE"
                                + " table_schema=current_schema() AND table_name LIKE 'ifj_%' AND"
                                + " table_name <> 'ifj_schema_history' AND"
                                + " constraint_type='UNIQUE'",
                            Integer.class));
            counts.put(
                    "foreignKeys",
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM information_schema.table_constraints WHERE"
                                    + " table_schema=current_schema() AND constraint_type='FOREIGN"
                                    + " KEY'",
                            Integer.class));
            counts.put(
                    "indexes",
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM pg_indexes WHERE schemaname=current_schema() AND"
                                    + " (indexname LIKE 'idx_ifj_%' OR indexname LIKE 'fk_ifj_%')",
                            Integer.class));
        } else if (vendor.equals("oracle")) {
            counts.put(
                    "foreignKeys",
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM user_constraints WHERE constraint_type='R' AND"
                                    + " table_name LIKE 'IFJ_%'",
                            Integer.class));
            counts.put(
                    "triggers",
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM user_triggers WHERE trigger_name LIKE"
                                    + " 'TRG_IFJ_%'",
                            Integer.class));
        } else if (vendor.equals("sqlserver")) {
            counts.put(
                    "foreignKeys",
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM sys.foreign_keys WHERE schema_id=SCHEMA_ID('dbo')"
                                + " AND parent_object_id IN (SELECT object_id FROM sys.tables WHERE"
                                + " name LIKE 'ifj[_]%')",
                            Integer.class));
            counts.put(
                    "triggers",
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM sys.triggers WHERE parent_class=1 AND name LIKE"
                                    + " 'trg_ifj[_]%[_]touch'",
                            Integer.class));
        }
        System.out.println(
                "V38 schema inventory: "
                        + vendor
                        + " / statements="
                        + statements.size()
                        + " / "
                        + counts);
    }

    @Test
    void reviewStartClearsPermitWaitDiagnostics() {
        long id = failedTree();
        jdbc.update(
                "UPDATE ifj_instance SET"
                    + " status='WAITING',wait_reason='PERMIT',message='waiting',error_code='IJOB-CONCURRENCY-0001',message_key='waiting',message_args='{}',permit_wait_deadline=?"
                    + " WHERE id=?",
                time(LocalDateTime.of(2030, 1, 1, 0, 0)),
                id);
        assertEquals(
                1,
                tx(
                        () ->
                                mapper(InstanceMapper.class)
                                        .markRunningIfWaiting(
                                                id,
                                                LocalDateTime.now(),
                                                com.ifielsc.ijob.infra.coordination.work
                                                        .ExecutionFencePermit.unfenced())));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_instance WHERE id=? AND status='RUNNING' AND"
                            + " message IS NULL AND error_code IS NULL AND message_key IS NULL AND"
                            + " message_args IS NULL AND wait_reason IS NULL AND"
                            + " permit_wait_deadline IS NULL AND start_time IS NOT NULL",
                        Integer.class,
                        id));
    }

    @Test
    void reviewFirstCheckpointRejectsStaleClaimAndAcceptsCurrentSuspendedAndUnfenced() {
        var fences = new ExecutionFenceCoordinator(new AppProperties());
        long id = claimedRoot(fences);
        String token =
                jdbc.queryForObject(
                        "SELECT claim_token FROM ifj_trigger_work_item WHERE trigger_id=?",
                        String.class,
                        id);
        var checkpoint = new com.ifielsc.ijob.infra.entity.runtime.EtlCheckpoint();
        checkpoint.setTaskId(1L);
        checkpoint.setLastInstanceId(id);
        checkpoint.setCheckpointKey("review-" + id);
        checkpoint.setSourceFingerprint("a".repeat(64));
        checkpoint.setWatermarkType("LONG");
        checkpoint.setWatermarkValue("1");
        var mapper = mapper(com.ifielsc.ijob.infra.mapper.runtime.EtlCheckpointMapper.class);
        var claimed =
                new com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit(
                        com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit.Mode.CLAIMED,
                        token,
                        1L);
        var stale =
                new com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit(
                        com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit.Mode.CLAIMED,
                        token,
                        2L);
        assertEquals(0, tx(() -> mapper.insertIfAbsent(checkpoint, stale)));
        jdbc.update(
                "UPDATE ifj_trigger_work_item SET lease_deadline=? WHERE trigger_id=?",
                time(LocalDateTime.of(2020, 1, 1, 0, 0)),
                id);
        assertEquals(0, tx(() -> mapper.insertIfAbsent(checkpoint, claimed)));
        assertNull(mapper.select(1L, checkpoint.getCheckpointKey()));
        jdbc.update(
                "UPDATE ifj_trigger_work_item SET lease_deadline=? WHERE trigger_id=?",
                time(LocalDateTime.now().plusDays(1)),
                id);
        assertEquals(1, tx(() -> mapper.insertIfAbsent(checkpoint, claimed)));
        checkpoint.setCheckpointKey("review-suspended-" + id);
        jdbc.update(
                "UPDATE ifj_trigger_work_item SET"
                    + " status='SUSPENDED',lease_owner=NULL,claim_token=NULL,lease_deadline=NULL"
                    + " WHERE trigger_id=?",
                id);
        var suspended =
                new com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit(
                        com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit.Mode
                                .SUSPENDED,
                        null,
                        1L);
        assertEquals(1, tx(() -> mapper.insertIfAbsent(checkpoint, suspended)));
        checkpoint.setCheckpointKey("review-unfenced-" + id);
        assertEquals(
                1,
                tx(
                        () ->
                                mapper.insertIfAbsent(
                                        checkpoint,
                                        com.ifielsc.ijob.infra.coordination.work
                                                .ExecutionFencePermit.unfenced())));
    }

    @Test
    void reviewRetentionDeletesRootAndNestedGatewayDecisions() {
        long trigger = failedTree();
        long job = ids.incrementAndGet(),
                definition = ids.incrementAndGet(),
                node = ids.incrementAndGet();
        jdbc.update("INSERT INTO ifj_job(id,name) VALUES(?,'review gateway')", job);
        jdbc.update(
                "INSERT INTO ifj_job_definition(id,job_id,revision,name,enabled)"
                        + " VALUES(?,?,1,'review gateway',?)",
                definition,
                job,
                true);
        jdbc.update(
                "INSERT INTO"
                    + " ifj_job_definition_node(id,definition_id,node_key,name,node_kind,gateway_mode)"
                    + " VALUES(?,?,'gateway','gateway','GATEWAY','EXCLUSIVE')",
                node,
                definition);
        for (Long instance :
                jdbc.queryForList(
                        "SELECT id FROM ifj_instance WHERE trigger_id=?", Long.class, trigger)) {
            jdbc.update(
                    "UPDATE ifj_instance SET target_type='JOB',target_id=?,definition_id=? WHERE"
                            + " id=?",
                    job,
                    definition,
                    instance);
            jdbc.update(
                    "INSERT INTO"
                        + " ifj_dag_gateway_decision(id,job_instance_id,definition_node_id,evaluation_no,selected_edge_ids,context_digest,evaluator_version,status,execution_fence)"
                        + " VALUES(?,?,?,1,'[]',?,1,'SUCCESS',1)",
                    ids.incrementAndGet(),
                    instance,
                    node,
                    "a".repeat(64));
        }
        var retention =
                new RuntimeDataRetentionService(mapper(RuntimeDataRetentionMapper.class), guard);
        var cutoff = LocalDateTime.of(2021, 1, 1, 0, 0);
        tx(
                () ->
                        retention.cleanupBatch(
                                new RuntimeDataRetentionService.RetentionCutoffs(
                                        cutoff, cutoff, cutoff, cutoff),
                                100));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_trigger WHERE id=?", Integer.class, trigger));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_dag_gateway_decision WHERE definition_node_id=?",
                        Integer.class,
                        node));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_job_definition_node WHERE id=?",
                        Integer.class,
                        node));
    }

    @Test
    void reviewNotificationReferenceLockBlocksConcurrentDelete() throws Exception {
        long id = ids.incrementAndGet();
        jdbc.update(
                "INSERT INTO ifj_notify_config(id,name,type,config) VALUES(?,'review"
                        + " reference','DINGTALK','{}')",
                id);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> holder =
                    executor.submit(
                            () ->
                                    tx(
                                            () -> {
                                                assertEquals(
                                                        java.util.List.of(id),
                                                        mapper(
                                                                        com.ifielsc.ijob.infra
                                                                                .mapper.notify
                                                                                .NotifyConfigMapper
                                                                                .class)
                                                                .selectIdsForUpdate(
                                                                        java.util.List.of(id)));
                                                entered.countDown();
                                                try {
                                                    if (!release.await(10, TimeUnit.SECONDS))
                                                        throw new IllegalStateException(
                                                                "lock test timed out");
                                                } catch (InterruptedException exception) {
                                                    Thread.currentThread().interrupt();
                                                    throw new IllegalStateException(exception);
                                                }
                                                return null;
                                            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<?> deletion =
                    executor.submit(
                            () ->
                                    tx(
                                            () ->
                                                    jdbc.update(
                                                            "DELETE FROM ifj_notify_config WHERE"
                                                                    + " id=?",
                                                            id)));
            try {
                assertThrows(
                        TimeoutException.class, () -> deletion.get(250, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            holder.get(10, TimeUnit.SECONDS);
            deletion.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
    }

    @Test
    void reviewPostgresConcurrentReceiptUsesConflictFreeInsert() throws Exception {
        Assumptions.assumeTrue(vendor.equals("postgresql"));
        for (boolean different : new boolean[] {false, true}) {
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            var reads = new java.util.concurrent.atomic.AtomicInteger();
            var delegate = mapper(EventRecordMapper.class);
            var synchronizedMapper =
                    (EventRecordMapper)
                            java.lang.reflect.Proxy.newProxyInstance(
                                    EventRecordMapper.class.getClassLoader(),
                                    new Class<?>[] {EventRecordMapper.class},
                                    (proxy, method, args) -> {
                                        Object result;
                                        try {
                                            result = method.invoke(delegate, args);
                                        } catch (
                                                java.lang.reflect.InvocationTargetException
                                                        exception) {
                                            throw exception.getCause();
                                        }
                                        if (method.getName().equals("selectBySourceAndIdempotency")
                                                && reads.incrementAndGet() <= 2)
                                            barrier.await(10, TimeUnit.SECONDS);
                                        return result;
                                    });
            var waits =
                    new EventWaitResolutionService(null, null, null, null, null, null) {
                        @Override
                        public int match(com.ifielsc.ijob.infra.entity.runtime.EventRecord event) {
                            return 0;
                        }
                    };
            var bindings =
                    new EventBindingService(null) {
                        @Override
                        public java.util.List<com.ifielsc.ijob.infra.entity.event.EventBinding>
                                listBindingsByEventCode(String code) {
                            return java.util.List.of();
                        }
                    };
            var service =
                    new EventReceiptService(
                            synchronizedMapper,
                            bindings,
                            null,
                            waits,
                            new com.fasterxml.jackson.databind.ObjectMapper());
            String key = "review-receipt-" + ids.incrementAndGet();
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                var first =
                        executor.submit(
                                () ->
                                        tx(
                                                () ->
                                                        service.receive(
                                                                new EventPublishCommand(
                                                                        "review.event",
                                                                        EventSourceType.API,
                                                                        "review",
                                                                        null,
                                                                        null,
                                                                        key,
                                                                        null,
                                                                        null,
                                                                        java.util.Map.of(
                                                                                "value", 1)))));
                var second =
                        executor.submit(
                                () ->
                                        tx(
                                                () ->
                                                        service.receive(
                                                                new EventPublishCommand(
                                                                        "review.event",
                                                                        EventSourceType.API,
                                                                        "review",
                                                                        null,
                                                                        null,
                                                                        key,
                                                                        null,
                                                                        null,
                                                                        java.util.Map.of(
                                                                                "value",
                                                                                different
                                                                                        ? 2
                                                                                        : 1)))));
                int created = 0, replayed = 0, conflicted = 0;
                for (var future : java.util.List.of(first, second)) {
                    try {
                        if (future.get(15, TimeUnit.SECONDS).duplicate()) replayed++;
                        else created++;
                    } catch (java.util.concurrent.ExecutionException exception) {
                        assertTrue(exception.getCause() instanceof BusinessException);
                        assertEquals(
                                com.ifielsc.ijob.common.response.ResultCode.CONFLICT,
                                ((BusinessException) exception.getCause()).getCode());
                        conflicted++;
                    }
                }
                assertEquals(1, created);
                assertEquals(different ? 0 : 1, replayed);
                assertEquals(different ? 1 : 0, conflicted);
                assertEquals(
                        1,
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM ifj_event WHERE source_key='review' AND"
                                        + " idempotency_key=?",
                                Integer.class,
                                key));
            }
        }
    }

    private long claimedRoot(ExecutionFenceCoordinator fences) {
        long id = ids.incrementAndGet();
        LocalDateTime deadline = LocalDateTime.now().plusDays(1);
        String token = java.util.UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO ifj_trigger"
                    + " (id,target_type,target_id,trigger_type,idempotency_key,status,trigger_time)"
                    + " VALUES (?,'TASK',1,'MANUAL',?,'RUNNING',?)",
                id,
                "claim-" + id,
                time(LocalDateTime.now()));
        jdbc.update(
                "INSERT INTO ifj_instance"
                    + " (id,root_instance_id,target_type,target_id,trigger_id,status,execution_fence)"
                    + " VALUES (?,?,'TASK',1,?,'RUNNING',1)",
                id,
                id,
                id);
        jdbc.update(
                "INSERT INTO ifj_trigger_work_item"
                    + " (trigger_id,root_instance_id,status,available_at,max_attempts,lease_owner,claim_token,lease_deadline,fence_token)"
                    + " VALUES (?,?,'CLAIMED',?,3,'audit',?,?,1)",
                id,
                id,
                time(LocalDateTime.now()),
                token,
                time(deadline));
        fences.activate(new TriggerWorkClaim(id, "audit", token, 1, 1, 3, deadline));
        return id;
    }

    private <T> T tx(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }

    private <T> T mapper(Class<T> type) {
        return sessions.getMapper(type);
    }

    @Test
    void eventFanoutMustCommitActiveAndSkippedSubscribersAndRetirementDisablesBinding() {
        long active = publishedTask(true);
        long disabled = publishedTask(false);
        long activeBinding = ids.incrementAndGet();
        long disabledBinding = ids.incrementAndGet();
        String eventCode = "audit." + active;
        for (long[] pair : new long[][] {{activeBinding, active}, {disabledBinding, disabled}}) {
            jdbc.update(
                    "INSERT INTO ifj_event_binding (id,target_type,target_id,event_code,enabled)"
                            + " VALUES (?,'TASK',?,?,?)",
                    pair[0],
                    pair[1],
                    eventCode,
                    true);
        }
        var resolver =
                new TaskAdmissionResolver(
                        mapper(TaskPublicationMapper.class), mapper(TaskDefinitionMapper.class));
        // A real transaction interceptor is essential: catching a poisoned inner transaction is not
        // enough.
        var proxy = new org.springframework.aop.framework.ProxyFactory(resolver);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(
                new org.springframework.transaction.interceptor.TransactionInterceptor(
                        new DataSourceTransactionManager(dataSource),
                        new org.springframework.transaction.annotation
                                .AnnotationTransactionAttributeSource()));
        var admission = (TaskAdmissionResolver) proxy.getProxy();
        var bindingService =
                new EventBindingService(
                        mapper(EventBindingMapper.class),
                        mapper(TaskMapper.class),
                        null,
                        admission,
                        null);
        var properties = new AppProperties();
        properties.getRuntime().setMode(AppProperties.RuntimeMode.STANDALONE);
        var queue = new DatabaseTriggerWorkQueue(mapper(TriggerWorkItemMapper.class), properties);
        var runtime =
                new TriggerRuntimeService(
                        mapper(TriggerMapper.class),
                        queue,
                        new ExecutionFenceCoordinator(properties));
        var dispatched = new java.util.ArrayList<Long>();
        var dispatcher =
                new TriggerCommandDispatcher(null) {
                    @Override
                    public void dispatchAfterCommit(Long id) {
                        dispatched.add(id);
                    }
                };
        var triggerService = new TriggerService(runtime, null, admission, null, dispatcher);
        var waits =
                new EventWaitResolutionService(null, null, null, null, null, null) {
                    @Override
                    public int match(com.ifielsc.ijob.infra.entity.runtime.EventRecord event) {
                        return 0;
                    }
                };
        var receipts =
                new EventReceiptService(
                        mapper(EventRecordMapper.class),
                        bindingService,
                        triggerService,
                        waits,
                        new com.fasterxml.jackson.databind.ObjectMapper());
        var command =
                new EventPublishCommand(
                        eventCode,
                        EventSourceType.API,
                        "audit",
                        null,
                        null,
                        "receipt-" + active,
                        null,
                        null,
                        null);
        var receipt = tx(() -> receipts.receive(command));
        assertFalse(receipt.duplicate());
        assertTrue(tx(() -> receipts.receive(command)).duplicate());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_trigger WHERE event_id=?",
                        Integer.class,
                        receipt.eventId()));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_trigger WHERE event_id=? AND status='SKIPPED'",
                        Integer.class,
                        receipt.eventId()));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_trigger_work_item w JOIN ifj_trigger t ON"
                                + " t.id=w.trigger_id WHERE t.event_id=?",
                        Integer.class,
                        receipt.eventId()));
        assertEquals(1, dispatched.size());
        var retirement =
                new TaskRetirementService(
                        mapper(TaskPublicationMapper.class),
                        mapper(TaskDefinitionMapper.class),
                        mapper(ScheduleMapper.class),
                        mapper(EventBindingMapper.class));
        tx(() -> retirement.retire(active));
        assertFalse(mapper(EventBindingMapper.class).selectById(activeBinding).getEnabled());
        var enable = new com.ifielsc.ijob.contract.event.EventBindingUpdateRequest();
        enable.setEnabled(true);
        assertThrows(
                BusinessException.class,
                () ->
                        tx(
                                () -> {
                                    bindingService.update(activeBinding, enable);
                                    return null;
                                }));
        assertFalse(mapper(EventBindingMapper.class).selectById(activeBinding).getEnabled());
    }

    private long publishedTask(boolean enabled) {
        long task = ids.incrementAndGet();
        long definition = ids.incrementAndGet();
        jdbc.update(
                "INSERT INTO ifj_task (id,name,plugin_type,plugin_config) VALUES"
                        + " (?,'audit','EVENT','{}')",
                task);
        jdbc.update(
                "INSERT INTO ifj_task_definition"
                    + " (id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,enabled)"
                    + " VALUES (?,?,1,'audit','EVENT','{}',0,0,?)",
                definition,
                task,
                enabled);
        jdbc.update(
                "UPDATE ifj_task SET current_task_definition_id=?,current_revision=1 WHERE id=?",
                definition,
                task);
        return task;
    }

    @Test
    void retryBeforeCleanupMustKeepTreeAndCleanupBeforeRetryMustReject() throws Exception {
        var retention =
                new RuntimeDataRetentionService(mapper(RuntimeDataRetentionMapper.class), guard);
        var queue =
                new DatabaseRuntimeCommandQueue(
                        mapper(RuntimeCommandMapper.class), new AppProperties(), guard);
        LocalDateTime cutoff = LocalDateTime.of(2021, 1, 1, 0, 0);
        var cutoffs =
                new RuntimeDataRetentionService.RetentionCutoffs(cutoff, cutoff, cutoff, cutoff);
        long first = failedTree();
        race(
                () ->
                        queue.submit(
                                "retry-first-" + first, first, RuntimeCommandType.RETRY, "audit"),
                () -> retention.cleanupBatch(cutoffs, 100));
        assertNotNull(mapper(InstanceMapper.class).selectById(first));
        assertEquals(
                "RUNNING",
                jdbc.queryForObject(
                        "SELECT status FROM ifj_trigger WHERE id=?", String.class, first));
        long second = failedTree();
        race(
                () -> retention.cleanupBatch(cutoffs, 100),
                () -> {
                    assertThrows(
                            BusinessException.class,
                            () ->
                                    queue.submit(
                                            "cleanup-first-" + second,
                                            second,
                                            RuntimeCommandType.RETRY,
                                            "audit"));
                    return null;
                });
        assertNull(mapper(InstanceMapper.class).selectById(second));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_trigger WHERE id=?", Integer.class, second));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_instance WHERE trigger_id=?",
                        Integer.class,
                        second));
    }

    private long failedTree() {
        long id = ids.incrementAndGet();
        Object old = time(LocalDateTime.of(2020, 1, 1, 0, 0));
        jdbc.update(
                "INSERT INTO ifj_trigger"
                    + " (id,target_type,target_id,trigger_type,idempotency_key,status,trigger_time,update_time)"
                    + " VALUES (?,'TASK',1,'MANUAL',?,'FAILED',?,?)",
                id,
                "failed-" + id,
                old,
                old);
        jdbc.update(
                "INSERT INTO ifj_instance"
                        + " (id,root_instance_id,target_type,target_id,trigger_id,status) VALUES"
                        + " (?,?,'TASK',1,?,'FAILED')",
                id,
                id,
                id);
        jdbc.update(
                "INSERT INTO ifj_trigger_work_item"
                        + " (trigger_id,root_instance_id,status,available_at,max_attempts) VALUES"
                        + " (?,?,'COMPLETED',?,3)",
                id,
                id,
                old);
        long child = ids.incrementAndGet();
        jdbc.update(
                "INSERT INTO ifj_instance"
                    + " (id,parent_instance_id,root_instance_id,target_type,target_id,trigger_id,status)"
                    + " VALUES (?,?,?,'TASK',1,?,'FAILED')",
                child,
                id,
                id,
                id);
        String levelColumn = vendor.equals("oracle") ? "log_level" : "level";
        jdbc.update(
                "INSERT INTO ifj_instance_log (id,instance_id,log_time,"
                        + levelColumn
                        + ",content) VALUES (?,?,?,'INFO','audit child')",
                ids.incrementAndGet(),
                child,
                old);
        return id;
    }

    @Test
    void policyMutationAndInstanceAdmissionMustSerializeInBothOrders() throws Exception {
        var policy =
                new com.ifielsc.ijob.core.concurrency.ConcurrencyPolicyService(
                        mapper(
                                com.ifielsc.ijob.infra.mapper.concurrency.ConcurrencyResourceMapper
                                        .class),
                        mapper(
                                com.ifielsc.ijob.infra.mapper.concurrency
                                        .PluginConcurrencyPolicyMapper.class),
                        mapper(
                                com.ifielsc.ijob.infra.mapper.concurrency
                                        .TaskConcurrencyPolicyMapper.class),
                        mapper(TaskPublicationMapper.class),
                        mapper(TaskMapper.class),
                        mapper(InstanceMapper.class),
                        mapper(ConcurrencyPermitMapper.class),
                        new com.ifielsc.ijob.core.concurrency.LockService(
                                null,
                                null,
                                new com.ifielsc.ijob.core.execute.ExecutionManager(),
                                new AppProperties(),
                                java.util.Optional.empty(),
                                new com.ifielsc.ijob.core.concurrency
                                        .ConcurrencyRequirementResolver(
                                        mapper(
                                                com.ifielsc.ijob.infra.mapper.concurrency
                                                        .PluginConcurrencyPolicyMapper.class),
                                        mapper(
                                                com.ifielsc.ijob.infra.mapper.concurrency
                                                        .TaskConcurrencyPolicyMapper.class),
                                        guard)),
                        guard,
                        mapper(
                                com.ifielsc.ijob.infra.mapper.concurrency.ConcurrencyContextMapper
                                        .class),
                        new AppProperties());
        var instances =
                new com.ifielsc.ijob.core.instance.InstanceService(
                        mapper(InstanceMapper.class), null, null, null, new AppProperties(), guard);
        var request =
                new com.ifielsc.ijob.contract.concurrency.TaskConcurrencyPolicyUpdateRequest();
        request.setExpectedVersion(0L);
        request.setTaskMaxConcurrency(2);
        request.setResourceIds(java.util.List.of());
        long firstTask = publishedTask(true);
        var firstTrigger = waitingTrigger(firstTask);
        race(
                () -> instances.create(firstTrigger),
                () -> {
                    assertThrows(
                            BusinessException.class,
                            () -> policy.putTaskPolicy(firstTask, request));
                    return null;
                });
        assertEquals(1, mapper(InstanceMapper.class).countActiveByTaskId(firstTask));
        assertNull(
                mapper(com.ifielsc.ijob.infra.mapper.concurrency.TaskConcurrencyPolicyMapper.class)
                        .selectByTaskId(firstTask));
        long secondTask = publishedTask(true);
        var secondTrigger = waitingTrigger(secondTask);
        race(
                () -> policy.putTaskPolicy(secondTask, request),
                () -> instances.create(secondTrigger));
        var resolver =
                new com.ifielsc.ijob.core.concurrency.ConcurrencyRequirementResolver(
                        mapper(
                                com.ifielsc.ijob.infra.mapper.concurrency
                                        .PluginConcurrencyPolicyMapper.class),
                        mapper(
                                com.ifielsc.ijob.infra.mapper.concurrency
                                        .TaskConcurrencyPolicyMapper.class),
                        guard);
        var requirements = tx(() -> resolver.resolve(secondTask, PluginType.EVENT));
        assertEquals(1, requirements.size());
        assertEquals(2, requirements.getFirst().maxConcurrency());
        assertTrue(requirements.getFirst().policyEpoch() > 0);
        request.setExpectedVersion(1L);
        request.setTaskMaxConcurrency(1);
        assertThrows(
                BusinessException.class, () -> tx(() -> policy.putTaskPolicy(secondTask, request)));
        var pluginRequest =
                new com.ifielsc.ijob.contract.concurrency.PluginConcurrencyPolicyUpdateRequest();
        pluginRequest.setExpectedVersion(0L);
        pluginRequest.setMaxConcurrency(1);
        assertThrows(
                BusinessException.class,
                () -> tx(() -> policy.putPluginPolicy(PluginType.EVENT, pluginRequest)));
        assertThrows(
                BusinessException.class,
                () ->
                        tx(
                                () -> {
                                    policy.deletePluginPolicy(PluginType.EVENT, 1L);
                                    return null;
                                }));
        var resourceRequest =
                new com.ifielsc.ijob.contract.concurrency.ConcurrencyResourceUpsertRequest();
        resourceRequest.setCode("audit." + secondTask);
        resourceRequest.setName("audit resource");
        resourceRequest.setEnabled(true);
        resourceRequest.setMaxConcurrency(2);
        long resource = tx(() -> policy.createResource(resourceRequest));
        resourceRequest.setExpectedVersion(policy.getResource(resource).getVersion());
        long thirdTask = publishedTask(true);
        request.setExpectedVersion(0L);
        request.setResourceIds(java.util.List.of(resource));
        tx(() -> policy.putTaskPolicy(thirdTask, request));
        var thirdTrigger = waitingTrigger(thirdTask);
        resourceRequest.setMaxConcurrency(1);
        race(
                () -> instances.create(thirdTrigger),
                () -> {
                    assertThrows(
                            BusinessException.class,
                            () -> policy.updateResource(resource, resourceRequest));
                    return null;
                });
        resourceRequest.setMaxConcurrency(3);
        tx(
                () -> {
                    policy.updateResource(resource, resourceRequest);
                    return null;
                });
        assertEquals(3, policy.getResource(resource).getMaxConcurrency());
        assertThrows(
                BusinessException.class,
                () ->
                        tx(
                                () -> {
                                    policy.deleteResource(
                                            resource, policy.getResource(resource).getVersion());
                                    return null;
                                }));
        resourceRequest.setExpectedVersion(policy.getResource(resource).getVersion());
        resourceRequest.setEnabled(false);
        assertThrows(
                BusinessException.class,
                () ->
                        tx(
                                () -> {
                                    policy.updateResource(resource, resourceRequest);
                                    return null;
                                }));
    }

    private com.ifielsc.ijob.infra.entity.runtime.Trigger waitingTrigger(long task) {
        long trigger = ids.incrementAndGet();
        long definition =
                mapper(TaskPublicationMapper.class)
                        .selectIdentityForUpdate(task)
                        .getCurrentTaskDefinitionId();
        jdbc.update(
                "INSERT INTO ifj_trigger"
                    + " (id,target_type,target_id,task_definition_id,trigger_type,idempotency_key,status,trigger_time)"
                    + " VALUES (?,'TASK',?,?,'MANUAL',?,'WAITING',?)",
                trigger,
                task,
                definition,
                "waiting-" + trigger,
                time(LocalDateTime.now()));
        return mapper(TriggerMapper.class).selectById(trigger);
    }

    private void race(Supplier<?> firstWork, Supplier<?> secondWork) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch entered = new CountDownLatch(1);
            Future<?> first =
                    pool.submit(
                            () ->
                                    tx(
                                            () -> {
                                                try {
                                                    var result = firstWork.get();
                                                    locked.countDown();
                                                    await(release);
                                                    return result;
                                                } catch (RuntimeException | Error failure) {
                                                    locked.countDown();
                                                    throw failure;
                                                }
                                            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            if (first.isDone()) first.get();
            Future<?> second =
                    pool.submit(
                            () -> {
                                // SQLite IMMEDIATE can block at transaction begin itself.
                                entered.countDown();
                                return tx(secondWork);
                            });
            AssertionError blockingFailure = null;
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            } catch (AssertionError failure) {
                blockingFailure = failure;
            } finally {
                release.countDown();
            }
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            if (blockingFailure != null) throw blockingFailure;
        }
    }

    private Object time(LocalDateTime value) {
        return vendor.equals("sqlite")
                ? value.format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
                : value;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Barrier timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
