package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifelio.ijob.common.config.AppProperties.RuntimeMode;
import com.ifelio.ijob.framework.database.PlatformDatabaseResolver;
import com.ifelio.ijob.framework.database.lock.SqlServerSingletonLockStrategy;
import com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifelio.ijob.infra.mapper.coordination.ClusterStateMapper;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class RealSqlServerMigrationIT {
    private static final String BCRYPT_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5M6P5M6P5M6P5M6P5M6P5M6P5M6P5M6";

    @Test
    void isolatedSqlServerShouldMigrateAndValidateIdempotently() throws Exception {
        Target target = Target.primary();
        verifyTarget(target);

        Flyway flyway = flyway(target);
        new SqlServerPreMigrationGuard()
                .verify(
                        new org.springframework.jdbc.datasource.DriverManagerDataSource(
                                target.url(), target.username(), target.password()));
        MigrateResult first = flyway.migrate();
        flyway.validate();
        MigrateResult second = flyway.migrate();

        assertTrue(first.success);
        assertTrue(second.success);
        assertEquals(0, second.migrationsExecuted);
        assertEquals(
                1,
                scalar(
                        target,
                        "SELECT MAX(CONVERT(int, version)) FROM dbo.ifj_schema_history WHERE"
                                + " success = 1"));
        assertEquals(
                48,
                scalar(
                        target,
                        "SELECT COUNT(*) FROM sys.tables WHERE schema_id = SCHEMA_ID('dbo') AND"
                                + " name LIKE 'ifj[_]%' AND name <> 'ifj_schema_history'"));
        assertEquals(
                48,
                scalar(
                        target,
                        "SELECT COUNT(*) FROM sys.key_constraints WHERE type = 'PK' AND"
                            + " parent_object_id IN (SELECT object_id FROM sys.tables WHERE"
                            + " schema_id = SCHEMA_ID('dbo') AND name LIKE 'ifj[_]%' AND name <>"
                            + " 'ifj_schema_history')"));
        assertEquals(
                43,
                scalar(
                        target,
                        "SELECT COUNT(*) FROM sys.foreign_keys WHERE schema_id = SCHEMA_ID('dbo')"
                                + " AND parent_object_id IN (SELECT object_id FROM sys.tables WHERE"
                                + " name LIKE 'ifj[_]%')"));
        assertEquals(
                14,
                scalar(
                        target,
                        "SELECT COUNT(*) FROM sys.triggers WHERE parent_class = 1 AND name LIKE"
                                + " 'trg_ifj[_]%[_]touch'"));
        assertEquals(
                0,
                scalar(
                        target,
                        "SELECT COUNT(*) FROM sys.identity_columns WHERE object_id IN (SELECT"
                            + " object_id FROM sys.tables WHERE schema_id = SCHEMA_ID('dbo') AND"
                            + " name LIKE 'ifj[_]%' AND name <> 'ifj_schema_history')"));
        assertEquals(
                0,
                scalar(
                        target,
                        "SELECT is_read_committed_snapshot_on FROM sys.databases WHERE database_id"
                                + " = DB_ID()"));
    }

    @Test
    void resolverLockAndSchemaStrategiesShouldOperateOnTheRealServer() {
        Target target = Target.primary();
        flyway(target).migrate();
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.STANDALONE);
        properties.getPlatformDatabase().setVendor(PlatformDatabaseVendor.SQLSERVER);
        properties.getPlatformDatabase().setSchema("dbo");
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(target.url(), target.username(), target.password());

        PlatformDatabaseResolver.ResolvedDatabase database =
                new PlatformDatabaseResolver(properties).verifyMigrationTarget(dataSource);
        assertEquals("sqlserver", database.databaseId());
        assertTrue(database.majorVersion() >= 15);
        new SqlServerPreMigrationGuard().verify(dataSource);

        SqlServerSingletonLockStrategy first = new SqlServerSingletonLockStrategy(dataSource);
        SqlServerSingletonLockStrategy contender = new SqlServerSingletonLockStrategy(dataSource);
        String lockName = "ijob-it-" + UUID.randomUUID();
        first.acquire(lockName);
        try {
            assertTrue(first.isHeld(lockName, 2));
            assertThrows(IllegalStateException.class, () -> contender.acquire(lockName));
            assertDoesNotThrow(
                    () -> new SqlServerSchemaCompatibilityInspector(dataSource).verify());
        } finally {
            first.release(lockName);
        }
        assertFalse(first.isHeld(lockName, 2));
        assertDoesNotThrow(() -> contender.acquire(lockName));
        contender.release(lockName);
    }

    @Test
    void standaloneLockShouldFailClosedAndAllowTakeoverAfterSessionLoss() throws Exception {
        Target target = Target.primary();
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(target.url(), target.username(), target.password());
        SqlServerSingletonLockStrategy owner = new SqlServerSingletonLockStrategy(dataSource);
        SqlServerSingletonLockStrategy replacement = new SqlServerSingletonLockStrategy(dataSource);
        String lockName = "ijob-it-session-loss-" + UUID.randomUUID();

        owner.acquire(lockName);
        var field = SqlServerSingletonLockStrategy.class.getDeclaredField("lockConnection");
        field.setAccessible(true);
        ((Connection) field.get(owner)).close();

        assertFalse(owner.isHeld(lockName, 2));
        assertDoesNotThrow(() -> replacement.acquire(lockName));
        replacement.release(lockName);
        owner.release(lockName);
    }

    @Test
    void readPastQueueClaimShouldSkipARowLockedByAnotherNode() throws Exception {
        Target target = Target.primary();
        flyway(target).migrate();
        long firstId = 8_000_000_000_000_000L + Math.floorMod(System.nanoTime(), 100_000_000L);
        long secondId = firstId + 1;
        String insertTrigger =
                "INSERT INTO dbo.ifj_trigger"
                    + " (id,target_type,target_id,trigger_type,idempotency_key,status,trigger_time)"
                    + " VALUES (?, 'TASK', ?, 'MANUAL', ?, 'WAITING', SYSUTCDATETIME())";
        String insertWork =
                "INSERT INTO dbo.ifj_trigger_work_item "
                        + "(trigger_id,status,available_at,attempt_count,max_attempts) "
                        + "VALUES (?, 'READY', SYSUTCDATETIME(), 0, 5)";
        String claim =
                "SELECT TOP (1) trigger_id FROM dbo.ifj_trigger_work_item "
                        + "WITH (UPDLOCK, READPAST, ROWLOCK) "
                        + "WHERE status = 'READY' AND available_at <= SYSUTCDATETIME() "
                        + "ORDER BY available_at, trigger_id";

        try (Connection setup =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            for (long id : List.of(firstId, secondId)) {
                try (java.sql.PreparedStatement statement = setup.prepareStatement(insertTrigger)) {
                    statement.setLong(1, id);
                    statement.setLong(2, id);
                    statement.setString(3, "mssql-queue-it-" + id);
                    statement.executeUpdate();
                }
                try (java.sql.PreparedStatement statement = setup.prepareStatement(insertWork)) {
                    statement.setLong(1, id);
                    statement.executeUpdate();
                }
            }
        }

        try (Connection first =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                Connection second =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password())) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            long firstClaim = queryLong(first, claim);
            long secondClaim = queryLong(second, claim);
            assertTrue(firstClaim == firstId || firstClaim == secondId);
            assertTrue(secondClaim == firstId || secondClaim == secondId);
            assertFalse(firstClaim == secondClaim);
            first.rollback();
            second.rollback();
        } finally {
            execute(
                    target,
                    "DELETE FROM dbo.ifj_trigger_work_item WHERE trigger_id IN ("
                            + firstId
                            + ","
                            + secondId
                            + "); DELETE FROM dbo.ifj_trigger WHERE id IN ("
                            + firstId
                            + ","
                            + secondId
                            + ")");
        }
    }

    @Test
    void standaloneShouldBeExclusiveAndRestartAfterOwnerShutdown() {
        Target target = Target.primary();
        flyway(target).migrate();

        try (ConfigurableApplicationContext owner =
                start(target, RuntimeMode.STANDALONE, "mssql-standalone-a")) {
            assertTrue(owner.isActive());
            assertThrows(
                    RuntimeException.class,
                    () -> start(target, RuntimeMode.STANDALONE, "mssql-standalone-b"));
        }
        try (ConfigurableApplicationContext restarted =
                start(target, RuntimeMode.STANDALONE, "mssql-standalone-c")) {
            assertTrue(restarted.isActive());
        }
    }

    @Test
    void twoClusterNodesShouldStartAndShareState() {
        Target target = Target.cluster();
        flyway(target).migrate();
        execute(
                target,
                "DELETE FROM dbo.ifj_cluster_state "
                        + "WHERE state_key = 'ijob:cluster:compatibility:v1'");

        try (ConfigurableApplicationContext first =
                        start(target, RuntimeMode.CLUSTER, "mssql-cluster-a");
                ConfigurableApplicationContext second =
                        start(target, RuntimeMode.CLUSTER, "mssql-cluster-b")) {
            assertTrue(first.isActive());
            assertTrue(second.isActive());
            assertEquals(
                    "sqlserver",
                    first.getBean(SqlSessionFactory.class).getConfiguration().getDatabaseId());
            validateAllMappedStatements(first, target);
            ClusterStateMapper mapper = first.getBean(ClusterStateMapper.class);
            String key = "IJob-It:MixedCase:" + UUID.randomUUID();
            String value = "数据库兼容性-🙂-" + "长文本".repeat(16_384);
            try {
                assertEquals(1, mapper.upsert(key, value, 60));
                assertEquals(value, mapper.selectValue(key));
                assertTrue(mapper.searchKeys("ijob-it:", "mixedcase", 0, 10, true).contains(key));
            } finally {
                mapper.delete(key);
            }
        }
    }

    private ConfigurableApplicationContext start(
            Target target, RuntimeMode mode, String instanceName) {
        return new SpringApplicationBuilder(IJobApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .run(
                        "--spring.config.import=optional:classpath:database/sqlserver.yml",
                        "--spring.datasource.url=" + target.url(),
                        "--spring.datasource.username=" + target.username(),
                        "--spring.datasource.password=" + target.password(),
                        "--spring.datasource.hikari.minimum-idle=0",
                        "--spring.datasource.hikari.maximum-pool-size=8",
                        "--spring.flyway.enabled=true",
                        "--spring.flyway.locations=classpath:db/release/sqlserver",
                        "--app.platform-database.vendor=SQLSERVER",
                        "--app.platform-database.schema=dbo",
                        "--app.runtime.mode=" + mode,
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

    private void validateAllMappedStatements(
            ConfigurableApplicationContext context, Target target) {
        validateAllMappedStatements(
                context.getBean(SqlSessionFactory.class).getConfiguration(),
                new DriverManagerDataSource(target.url(), target.username(), target.password()));
    }

    void validateAllMappedStatements(
            org.apache.ibatis.session.Configuration configuration, javax.sql.DataSource source) {
        List<String> statementIds =
                configuration.getMappedStatementNames().stream()
                        .filter(name -> name.startsWith("com.ifelio.ijob.infra.mapper."))
                        .sorted()
                        .toList();
        CurrentSchemaTestSupport.assertMapperInventory(configuration, statementIds);
        Map<String, Object> parameters = planningParameters();

        try (Connection connection = source.getConnection()) {
            try (Statement mode = connection.createStatement()) {
                mode.execute("SET SHOWPLAN_XML ON");
            }
            try {
                for (String statementId : statementIds) {
                    CurrentSchemaTestSupport.selectPlanningType(parameters, statementId);
                    MappedStatement statement = configuration.getMappedStatement(statementId);
                    BoundSql boundSql;
                    try {
                        boundSql = statement.getBoundSql(parameters);
                    } catch (RuntimeException exception) {
                        throw new IllegalStateException("Failed to bind " + statementId, exception);
                    }
                    try (java.sql.PreparedStatement planned =
                            connection.prepareStatement(boundSql.getSql())) {
                        new DefaultParameterHandler(statement, parameters, boundSql)
                                .setParameters(planned);
                        planned.execute();
                    } catch (Exception exception) {
                        throw new IllegalStateException(
                                "SQL Server rejected " + statementId, exception);
                    }
                }
            } finally {
                try (Statement mode = connection.createStatement()) {
                    mode.execute("SET SHOWPLAN_XML OFF");
                }
            }
        } catch (Exception exception) {
            throw exception instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException(exception);
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
        CurrentSchemaTestSupport.addPlanningParameters(values);
        values.put("ascending", true);
        values.put("enabled", true);
        values.put("retired", false);
        values.put("success", true);
        values.put("configEnabled", true);
        values.put("name", "");
        values.put("code", "");
        values.put("groupCode", "default");
        values.put("instanceGroups", List.of("default"));
        values.put("allowedTaskIds", List.of(1L));
        values.put("allowedJobIds", List.of(1L));
        values.put("keyword", "");
        values.put("prefix", "ijob-it:");
        values.put("businessKey", "business-it");
        values.put("correlationKey", "correlation-it");
        values.put("operatorId", "operator-it");
        values.put("targetId", null);
        values.put("permit", ExecutionFencePermit.unfenced());
        values.put("user", values);
        values.put("query", values);
        values.put("schedule", values);
        values.put("checkpoint", values);
        values.put("instance", values);
        return values;
    }

    private Flyway flyway(Target target) {
        return Flyway.configure()
                .dataSource(target.url(), target.username(), target.password())
                .locations("classpath:db/release/sqlserver")
                .defaultSchema("dbo")
                .table("ifj_schema_history")
                .cleanDisabled(true)
                .load();
    }

    private void verifyTarget(Target target) throws Exception {
        integrationDatabaseName(target.url());
        try (Connection connection =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            assertEquals(target.database(), connection.getCatalog());
            assertEquals("dbo", connection.getSchema());
            assertEquals("Microsoft SQL Server", connection.getMetaData().getDatabaseProductName());
            assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 15);
        }
    }

    private long scalar(Target target, String sql) throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void execute(Target target, String sql) {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long queryLong(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    static String integrationDatabaseName(String url) {
        if (url == null || !url.toLowerCase().startsWith("jdbc:sqlserver://")) {
            throw new IllegalStateException("SQL Server integration JDBC URL is invalid");
        }
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("(?i)(?:^|;)databaseName=([^;]+)").matcher(url);
        if (!matcher.find() || !matcher.group(1).matches("ijob_it_mssql_[A-Za-z0-9_]+")) {
            throw new IllegalStateException("An isolated ijob_it_mssql_ database is required");
        }
        return matcher.group(1);
    }

    private record Target(String url, String username, String password, String database) {
        private static Target primary() {
            String database = required("IJOB_SQLSERVER_IT_DATABASE");
            String url = required("IJOB_SQLSERVER_IT_URL");
            if (!database.equals(integrationDatabaseName(url))) {
                throw new IllegalStateException(
                        "SQL Server integration URL targets the wrong database");
            }
            return new Target(
                    url,
                    required("IJOB_SQLSERVER_IT_USERNAME"),
                    optional("IJOB_SQLSERVER_IT_PASSWORD"),
                    database);
        }

        private static Target cluster() {
            String database = required("IJOB_SQLSERVER_CLUSTER_IT_DATABASE");
            String baseUrl = required("IJOB_SQLSERVER_IT_URL");
            String url = baseUrl.replace(integrationDatabaseName(baseUrl), database);
            if (!database.equals(integrationDatabaseName(url))) {
                throw new IllegalStateException(
                        "SQL Server cluster URL targets the wrong database");
            }
            return new Target(
                    url,
                    required("IJOB_SQLSERVER_IT_USERNAME"),
                    optional("IJOB_SQLSERVER_IT_PASSWORD"),
                    database);
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Required SQL Server integration setting is missing: " + name);
            }
            return value;
        }

        private static String optional(String name) {
            String value = System.getenv(name);
            return value == null ? "" : value;
        }
    }
}
