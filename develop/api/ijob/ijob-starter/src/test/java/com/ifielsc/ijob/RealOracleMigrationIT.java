// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeMode;
import com.ifielsc.ijob.framework.database.PlatformDatabaseResolver;
import com.ifielsc.ijob.framework.database.lock.OracleSingletonLockStrategy;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifielsc.ijob.infra.mapper.coordination.ClusterStateMapper;

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

class RealOracleMigrationIT {
    private static final String BCRYPT_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5M6P5M6P5M6P5M6P5M6P5M6P5M6P5M6";

    @Test
    void isolatedOracleElevenGShouldMigrateAndValidateIdempotently() throws Exception {
        Target target = Target.primary();
        verifyTarget(target);

        Flyway flyway = flyway(target);
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
                        "SELECT MAX(TO_NUMBER(\"version\")) FROM \"ifj_schema_history\" WHERE"
                                + " \"success\" = 1"));
        assertEquals(
                48,
                scalar(
                        target,
                        "SELECT COUNT(*) FROM user_tables WHERE table_name LIKE 'IFJ_%' AND"
                                + " table_name <> 'IFJ_SCHEMA_HISTORY'"));
        assertEquals(
                48,
                scalar(
                        target,
                        "SELECT COUNT(*) FROM user_constraints WHERE constraint_type = 'P' AND"
                            + " table_name LIKE 'IFJ_%' AND table_name <> 'IFJ_SCHEMA_HISTORY'"));
        assertEquals(
                43,
                scalar(
                        target,
                        "SELECT COUNT(*) FROM user_constraints WHERE constraint_type = 'R' AND"
                                + " table_name LIKE 'IFJ_%'"));
        assertEquals(
                14,
                scalar(
                        target,
                        "SELECT COUNT(*) FROM user_triggers WHERE trigger_name LIKE 'TRG_IFJ_%'"));
        assertEquals(0, scalar(target, "SELECT COUNT(*) FROM user_sequences"));
        assertEquals(
                0,
                scalar(
                        target,
                        "SELECT COUNT(*) FROM user_objects WHERE LENGTHB(object_name) > 30"));
        assertEquals(
                0,
                scalar(
                        target,
                        "SELECT COUNT(*) FROM user_constraints WHERE LENGTHB(constraint_name) >"
                                + " 30"));
        assertEquals(
                0,
                scalar(
                        target,
                        "SELECT COUNT(*) FROM user_tab_columns WHERE LENGTHB(column_name) > 30"));
    }

    @Test
    void resolverLockAndSchemaInspectorShouldOperateOnOracleElevenG() {
        Target target = Target.primary();
        flyway(target).migrate();
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(target.url(), target.username(), target.password());
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.STANDALONE);
        properties.getPlatformDatabase().setVendor(PlatformDatabaseVendor.ORACLE);
        properties.getPlatformDatabase().setSchema(target.username());

        PlatformDatabaseResolver.ResolvedDatabase database =
                new PlatformDatabaseResolver(properties).verifyMigrationTarget(dataSource);
        assertEquals("oracle", database.databaseId());
        assertTrue(database.majorVersion() >= 11);

        OracleSingletonLockStrategy first = new OracleSingletonLockStrategy(dataSource);
        OracleSingletonLockStrategy contender = new OracleSingletonLockStrategy(dataSource);
        String lockName = "ijob-it-" + UUID.randomUUID();
        first.acquire(lockName);
        try {
            assertTrue(first.isHeld(lockName, 2));
            assertThrows(IllegalStateException.class, () -> contender.acquire(lockName));
            assertDoesNotThrow(() -> new OracleSchemaCompatibilityInspector(dataSource).verify());
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
        OracleSingletonLockStrategy owner = new OracleSingletonLockStrategy(dataSource);
        OracleSingletonLockStrategy replacement = new OracleSingletonLockStrategy(dataSource);
        String lockName = "ijob-it-session-loss-" + UUID.randomUUID();

        owner.acquire(lockName);
        var field = OracleSingletonLockStrategy.class.getDeclaredField("lockConnection");
        field.setAccessible(true);
        ((Connection) field.get(owner)).close();

        assertFalse(owner.isHeld(lockName, 2));
        assertDoesNotThrow(() -> replacement.acquire(lockName));
        replacement.release(lockName);
        owner.release(lockName);
    }

    @Test
    void clusterRuntimeShouldStartWithOracleMapperSelection() {
        Target target = Target.cluster();
        flyway(target).migrate();

        try (ConfigurableApplicationContext first =
                        start(target, RuntimeMode.CLUSTER, "oracle-cluster-a");
                ConfigurableApplicationContext second =
                        start(target, RuntimeMode.CLUSTER, "oracle-cluster-b")) {
            assertTrue(first.isActive());
            assertTrue(second.isActive());
            assertEquals(
                    "oracle",
                    first.getBean(SqlSessionFactory.class).getConfiguration().getDatabaseId());
            validateAllMappedStatements(first, target);
            verifyClusterStateRoundTrip(first);
            assertEquals(
                    2,
                    scalar(
                            target,
                            "SELECT COUNT(*) FROM ifj_app_run WHERE instance_name IN"
                                    + " ('oracle-cluster-a','oracle-cluster-b') AND status ="
                                    + " 'RUNNING'"));
        }
    }

    @Test
    void standaloneRuntimeShouldBeExclusiveAndRestartAfterShutdown() {
        Target target = Target.primary();
        flyway(target).migrate();

        try (ConfigurableApplicationContext owner =
                start(target, RuntimeMode.STANDALONE, "oracle-standalone-a")) {
            assertTrue(owner.isActive());
            assertThrows(
                    RuntimeException.class,
                    () -> start(target, RuntimeMode.STANDALONE, "oracle-standalone-b"));
        }
        try (ConfigurableApplicationContext restarted =
                start(target, RuntimeMode.STANDALONE, "oracle-standalone-c")) {
            assertTrue(restarted.isActive());
        }
    }

    @Test
    void skipLockedQueueClaimShouldNotReturnTheSameRowToTwoSessions() throws Exception {
        Target target = Target.primary();
        flyway(target).migrate();
        long firstId = 8_600_000_000_000_000L + Math.floorMod(System.nanoTime(), 10_000_000L);
        long secondId = firstId + 1;
        String insertTrigger =
                "INSERT INTO ifj_trigger"
                    + " (id,target_type,target_id,trigger_type,idempotency_key,status,trigger_time)"
                    + " VALUES (?, 'TASK', ?, 'MANUAL', ?, 'WAITING',"
                    + " CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)))";
        String insertWork =
                "INSERT INTO ifj_trigger_work_item"
                        + " (trigger_id,status,available_at,attempt_count,max_attempts)"
                        + " VALUES (?, 'READY',"
                        + " CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)), 0, 5)";
        String claim =
                "SELECT trigger_id FROM ifj_trigger_work_item WHERE status = 'READY'"
                        + " AND available_at <= CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))"
                        + " AND ROWNUM <= 1 FOR UPDATE SKIP LOCKED";
        try (Connection setup =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            for (long id : List.of(firstId, secondId)) {
                try (java.sql.PreparedStatement statement = setup.prepareStatement(insertTrigger)) {
                    statement.setLong(1, id);
                    statement.setLong(2, id);
                    statement.setString(3, "oracle-queue-it-" + id);
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
            Long firstClaim = queryLongOrNull(first, claim);
            Long secondClaim = queryLongOrNull(second, claim);
            assertTrue(firstClaim != null);
            assertTrue(firstClaim == firstId || firstClaim == secondId);
            if (secondClaim != null) {
                assertTrue(secondClaim == firstId || secondClaim == secondId);
                assertFalse(firstClaim.equals(secondClaim));
            }
            first.rollback();
            assertTrue(queryLongOrNull(second, claim) != null);
            second.rollback();
        } finally {
            execute(
                    target,
                    "DELETE FROM ifj_trigger_work_item WHERE trigger_id IN ("
                            + firstId
                            + ","
                            + secondId
                            + ")");
            execute(
                    target,
                    "DELETE FROM ifj_trigger WHERE id IN (" + firstId + "," + secondId + ")");
        }
    }

    private void verifyClusterStateRoundTrip(ConfigurableApplicationContext context) {
        ClusterStateMapper mapper = context.getBean(ClusterStateMapper.class);
        String key = "IJob-It:MixedCase:" + UUID.randomUUID();
        String largeUnicodeValue = "数据库兼容性-" + "长文本".repeat(20_000);
        try {
            mapper.upsert(key, largeUnicodeValue, 60);
            assertEquals(largeUnicodeValue, mapper.selectValue(key));
            assertTrue(mapper.selectTimeoutSeconds(key) >= 0);
            assertTrue(mapper.searchKeys("ijob-it:", "mixedcase", 0, 10, true).contains(key));
            assertEquals(1, mapper.updateTimeout(key, -1));
            assertEquals(-1L, mapper.selectTimeoutSeconds(key));
            assertEquals(1, mapper.updateValue(key, ""));
            assertNull(mapper.selectValue(key));
            assertEquals(1, mapper.updateValue(key, largeUnicodeValue + "-更新"));
            assertEquals(largeUnicodeValue + "-更新", mapper.selectValue(key));
        } finally {
            mapper.delete(key);
        }
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
                        .filter(name -> name.startsWith("com.ifielsc.ijob.infra.mapper."))
                        .sorted()
                        .toList();
        CurrentSchemaTestSupport.assertMapperInventory(configuration, statementIds);
        Map<String, Object> parameters = planningParameters();

        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            for (String statementId : statementIds) {
                CurrentSchemaTestSupport.selectPlanningType(parameters, statementId);
                MappedStatement statement = configuration.getMappedStatement(statementId);
                BoundSql boundSql;
                try {
                    boundSql = statement.getBoundSql(parameters);
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("Failed to bind " + statementId, exception);
                }
                if (statementId.endsWith("EtlCheckpointMapper.insertIfAbsent")
                        || statementId.endsWith("NotifyRateLimitMapper.insertIfAbsent")
                        || statementId.endsWith("ClusterStateMapper.upsert")
                        || statementId.endsWith("ClusterStateMapper.updateValue")
                        || statementId.endsWith("ClusterStateMapper.insertIfAbsent")) {
                    try (java.sql.PreparedStatement actual =
                            connection.prepareStatement(boundSql.getSql())) {
                        new DefaultParameterHandler(statement, parameters, boundSql)
                                .setParameters(actual);
                        actual.execute();
                        connection.rollback();
                        continue;
                    } catch (Exception exception) {
                        throw new IllegalStateException(
                                "Oracle rejected " + statementId, exception);
                    }
                }
                try (java.sql.PreparedStatement explain =
                        connection.prepareStatement("EXPLAIN PLAN FOR " + boundSql.getSql())) {
                    new DefaultParameterHandler(statement, parameters, boundSql)
                            .setParameters(explain);
                    explain.execute();
                } catch (Exception exception) {
                    throw new IllegalStateException("Oracle rejected " + statementId, exception);
                }
            }
            connection.rollback();
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
        values.put("stateKey", "ijob-it:planning");
        values.put("stateValue", "planning-value");
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

    private ConfigurableApplicationContext start(
            Target target, RuntimeMode mode, String instanceName) {
        return new SpringApplicationBuilder(IJobApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .run(
                        "--spring.config.import=optional:classpath:database/oracle.yml",
                        "--spring.datasource.url=" + target.url(),
                        "--spring.datasource.username=" + target.username(),
                        "--spring.datasource.password=" + target.password(),
                        "--spring.datasource.hikari.minimum-idle=0",
                        "--spring.datasource.hikari.maximum-pool-size=8",
                        "--spring.flyway.enabled=true",
                        "--spring.flyway.locations=classpath:db/release/oracle",
                        "--spring.flyway.default-schema=" + target.username(),
                        "--app.platform-database.vendor=ORACLE",
                        "--app.platform-database.schema=" + target.username(),
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

    private Flyway flyway(Target target) {
        return Flyway.configure()
                .dataSource(target.url(), target.username(), target.password())
                .locations("classpath:db/release/oracle")
                .schemas(target.username())
                .defaultSchema(target.username())
                .table("ifj_schema_history")
                .cleanDisabled(true)
                .load();
    }

    private void verifyTarget(Target target) throws Exception {
        if (!target.username().matches("IJOB_IT_ORA_(PRIMARY|CLUSTER)(_[A-Z0-9]{6})?")) {
            throw new IllegalStateException("An isolated Oracle integration user is required");
        }
        try (Connection connection =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            assertEquals("Oracle", connection.getMetaData().getDatabaseProductName());
            assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 11);
            assertEquals(target.username(), connection.getSchema());
        }
    }

    private int scalar(Target target, String sql) {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Long queryLongOrNull(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getLong(1) : null;
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

    private record Target(String url, String username, String password) {
        private static Target primary() {
            return new Target(
                    required("IJOB_ORACLE_IT_URL"),
                    required("IJOB_ORACLE_IT_USERNAME").toUpperCase(),
                    required("IJOB_ORACLE_IT_PASSWORD"));
        }

        private static Target cluster() {
            return new Target(
                    required("IJOB_ORACLE_IT_URL"),
                    required("IJOB_ORACLE_CLUSTER_IT_USERNAME").toUpperCase(),
                    required("IJOB_ORACLE_IT_PASSWORD"));
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Required Oracle integration setting is missing: " + name);
            }
            return value;
        }
    }
}
