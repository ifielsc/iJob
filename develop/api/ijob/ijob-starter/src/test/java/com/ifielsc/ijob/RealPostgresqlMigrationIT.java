// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeMode;
import com.ifielsc.ijob.framework.database.PlatformDatabaseResolver;
import com.ifielsc.ijob.framework.database.lock.PostgreSqlSingletonLockStrategy;
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

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class RealPostgresqlMigrationIT {
    private static final String BCRYPT_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5M6P5M6P5M6P5M6P5M6P5M6P5M6P5M6";

    @Test
    void isolatedPostgresqlShouldMigrateAndValidateIdempotently() throws Exception {
        PostgresqlTarget target = PostgresqlTarget.primary();
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
                scalarLong(
                        target,
                        "SELECT MAX(version::integer) FROM ifj_schema_history WHERE success"));
        assertEquals(
                48,
                scalarLong(
                        target,
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = current_schema() "
                                + "AND table_name LIKE 'ifj_%' "
                                + "AND table_name <> 'ifj_schema_history'"));
        assertEquals(
                9,
                scalarLong(
                        target,
                        "SELECT COUNT(*) FROM ifj_schema_capability WHERE enabled = TRUE AND"
                            + " ((capability_key = 'platform-schema' AND capability_version >= 38)"
                            + " OR (capability_key = 'event-wait-operator-relation' AND"
                            + " capability_version >= 1) OR (capability_key ="
                            + " 'stable-id-segment-seed' AND capability_version >= 1) OR"
                            + " (capability_key = 'execution-group-routing' AND capability_version"
                            + " >= 1) OR (capability_key = 'conditional-dag' AND capability_version"
                            + " >= 1) OR (capability_key = 'execution-target-routing' AND"
                            + " capability_version >= 1) OR (capability_key ="
                            + " 'composite-task-concurrency' AND capability_version >= 1) OR"
                            + " (capability_key = 'schedule-misfire-policy' AND capability_version"
                            + " >= 1) OR (capability_key = 'runtime-history-v2' AND"
                            + " capability_version >= 1))"));
        assertEquals(
                1,
                scalarLong(
                        target,
                        "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() "
                                + "AND tablename = 'ifj_runtime_command' "
                                + "AND indexname = 'idx_ifj_runtime_command_expiration'"));
        assertEquals("public", scalarString(target, "SELECT current_schema()"));
        assertEquals(
                48,
                scalarLong(
                        target,
                        "SELECT COUNT(*) FROM information_schema.table_constraints "
                                + "WHERE table_schema = current_schema() "
                                + "AND table_name LIKE 'ifj_%' "
                                + "AND table_name <> 'ifj_schema_history' "
                                + "AND constraint_type = 'PRIMARY KEY'"));
        assertEquals(
                30,
                scalarLong(
                        target,
                        "SELECT COUNT(*) FROM information_schema.table_constraints "
                                + "WHERE table_schema = current_schema() "
                                + "AND table_name LIKE 'ifj_%' "
                                + "AND table_name <> 'ifj_schema_history' "
                                + "AND constraint_type = 'UNIQUE'"));
        assertEquals(
                43,
                scalarLong(
                        target,
                        "SELECT COUNT(*) FROM information_schema.table_constraints "
                                + "WHERE table_schema = current_schema() "
                                + "AND constraint_type = 'FOREIGN KEY'"));
        assertEquals(
                85,
                scalarLong(
                        target,
                        "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() "
                                + "AND (indexname LIKE 'idx_ifj_%' "
                                + "OR indexname LIKE 'fk_ifj_%')"));
        assertEquals(
                0,
                scalarLong(
                        target,
                        "SELECT COUNT(*) FROM information_schema.columns "
                                + "WHERE table_schema = current_schema() "
                                + "AND table_name LIKE 'ifj_%' "
                                + "AND data_type = 'timestamp with time zone'"));
        assertEquals(
                0,
                scalarLong(
                        target,
                        "SELECT COUNT(*) FROM pg_sequences WHERE schemaname = current_schema()"));
    }

    @Test
    void postgresqlResolverLockAndSchemaStrategiesShouldOperateOnTheRealServer() {
        PostgresqlTarget target = PostgresqlTarget.primary();
        flyway(target).migrate();
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.STANDALONE);
        properties.getPlatformDatabase().setVendor(PlatformDatabaseVendor.POSTGRESQL);
        properties.getPlatformDatabase().setSchema("public");
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(target.url(), target.username(), target.password());

        PlatformDatabaseResolver.ResolvedDatabase database =
                new PlatformDatabaseResolver(properties).verifyMigrationTarget(dataSource);
        assertEquals("postgresql", database.databaseId());
        assertTrue(database.majorVersion() >= 14);

        PostgreSqlSingletonLockStrategy first = new PostgreSqlSingletonLockStrategy(dataSource);
        PostgreSqlSingletonLockStrategy contender = new PostgreSqlSingletonLockStrategy(dataSource);
        String lockName = "ijob-it-" + UUID.randomUUID();
        first.acquire(lockName);
        try {
            assertTrue(first.isHeld(lockName, 2));
            assertThrows(IllegalStateException.class, () -> contender.acquire(lockName));
            assertDoesNotThrow(
                    () -> new PostgreSqlSchemaCompatibilityInspector(dataSource).verify());
        } finally {
            first.release(lockName);
        }
        assertFalse(first.isHeld(lockName, 2));
        assertDoesNotThrow(() -> contender.acquire(lockName));
        contender.release(lockName);
    }

    @Test
    void standaloneShouldBeExclusiveAndRestartAfterOwnerShutdown() {
        PostgresqlTarget target = PostgresqlTarget.primary();
        flyway(target).migrate();

        try (ConfigurableApplicationContext owner =
                start(target, RuntimeMode.STANDALONE, "pg-standalone-a")) {
            assertTrue(owner.isActive());
            assertThrows(
                    RuntimeException.class,
                    () -> start(target, RuntimeMode.STANDALONE, "pg-standalone-b"));
        }
        try (ConfigurableApplicationContext restarted =
                start(target, RuntimeMode.STANDALONE, "pg-standalone-c")) {
            assertTrue(restarted.isActive());
        }
    }

    @Test
    void twoClusterNodesShouldStartAgainstOnePostgresqlDatabase() {
        PostgresqlTarget target = PostgresqlTarget.cluster();
        flyway(target).migrate();

        try (ConfigurableApplicationContext first =
                        start(target, RuntimeMode.CLUSTER, "pg-cluster-a");
                ConfigurableApplicationContext second =
                        start(target, RuntimeMode.CLUSTER, "pg-cluster-b")) {
            assertTrue(first.isActive());
            assertTrue(second.isActive());
            validateAllMappedStatements(first, target);
            verifyClusterStateRoundTrip(first);
            assertEquals(
                    2,
                    scalarLong(
                            target,
                            "SELECT COUNT(*) FROM ifj_app_run WHERE instance_name IN "
                                    + "('pg-cluster-a', 'pg-cluster-b') AND status = 'RUNNING'"));
        }
    }

    private void verifyClusterStateRoundTrip(ConfigurableApplicationContext context) {
        ClusterStateMapper mapper = context.getBean(ClusterStateMapper.class);
        String key = "IJob-It:MixedCase:" + UUID.randomUUID();
        String largeUnicodeValue = "数据库兼容性-🙂-" + "长文本".repeat(32_768);
        try {
            assertEquals(1, mapper.upsert(key, largeUnicodeValue, 60));
            assertEquals(largeUnicodeValue, mapper.selectValue(key));
            assertTrue(mapper.selectTimeoutSeconds(key) >= 0);
            assertTrue(mapper.searchKeys("ijob-it:", "mixedcase", 0, 10, true).contains(key));
            assertEquals(1, mapper.updateTimeout(key, -1));
            assertEquals(-1L, mapper.selectTimeoutSeconds(key));
            assertEquals(1, mapper.updateValue(key, largeUnicodeValue + "-更新"));
            assertEquals(largeUnicodeValue + "-更新", mapper.selectValue(key));
        } finally {
            mapper.delete(key);
        }
    }

    private void validateAllMappedStatements(
            ConfigurableApplicationContext context, PostgresqlTarget target) {
        validateAllMappedStatements(
                context.getBean(SqlSessionFactory.class).getConfiguration(),
                new DriverManagerDataSource(target.url(), target.username(), target.password()));
    }

    void validateAllMappedStatements(
            org.apache.ibatis.session.Configuration configuration, javax.sql.DataSource source) {
        assertEquals("postgresql", configuration.getDatabaseId());
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
                try (PreparedStatement explain =
                        connection.prepareStatement("EXPLAIN " + boundSql.getSql())) {
                    new DefaultParameterHandler(statement, parameters, boundSql)
                            .setParameters(explain);
                    explain.execute();
                    // EXPLAIN parses, binds and plans without executing the mapped DML.
                } catch (Exception exception) {
                    throw new IllegalStateException(
                            "PostgreSQL rejected " + statementId, exception);
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
            PostgresqlTarget target, RuntimeMode mode, String instanceName) {
        return new SpringApplicationBuilder(IJobApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .run(
                        "--spring.config.import=optional:classpath:database/postgresql.yml",
                        "--spring.datasource.driver-class-name=org.postgresql.Driver",
                        "--spring.datasource.url=" + target.url(),
                        "--spring.datasource.username=" + target.username(),
                        "--spring.datasource.password=" + target.password(),
                        "--spring.datasource.hikari.minimum-idle=0",
                        "--spring.datasource.hikari.maximum-pool-size=8",
                        "--spring.flyway.enabled=true",
                        "--spring.flyway.locations=classpath:db/release/postgresql",
                        "--app.platform-database.vendor=POSTGRESQL",
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

    private Flyway flyway(PostgresqlTarget target) {
        return Flyway.configure()
                .dataSource(target.url(), target.username(), target.password())
                .locations("classpath:db/release/postgresql")
                .table("ifj_schema_history")
                .cleanDisabled(true)
                .load();
    }

    private void verifyTarget(PostgresqlTarget target) throws Exception {
        assertEquals(target.database(), integrationDatabaseName(target.url()));
        try (Connection connection =
                DriverManager.getConnection(target.url(), target.username(), target.password())) {
            assertEquals(target.database(), connection.getCatalog());
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 14);
        }
    }

    private long scalarLong(PostgresqlTarget target, String sql) {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String scalarString(PostgresqlTarget target, String sql) {
        try (Connection connection =
                        DriverManager.getConnection(
                                target.url(), target.username(), target.password());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String integrationDatabaseName(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("PostgreSQL integration JDBC URL is invalid");
        }
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            String path = uri.getRawPath();
            if (uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getRawFragment() != null
                    || path == null
                    || !path.matches("/ijob_it_pg_[A-Za-z0-9_]+")) {
                throw new IllegalStateException("An isolated ijob_it_pg_ database is required");
            }
            return path.substring(1);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "PostgreSQL integration JDBC URL is invalid", exception);
        }
    }

    private record PostgresqlTarget(String url, String username, String password, String database) {
        private static PostgresqlTarget primary() {
            return fromDatabase(required("IJOB_POSTGRES_IT_DATABASE"));
        }

        private static PostgresqlTarget cluster() {
            return fromDatabase(required("IJOB_POSTGRES_CLUSTER_IT_DATABASE"));
        }

        private static PostgresqlTarget fromDatabase(String database) {
            String host = required("IJOB_POSTGRES_IT_HOST");
            String port = required("IJOB_POSTGRES_IT_PORT");
            String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            integrationDatabaseName(url);
            return new PostgresqlTarget(
                    url,
                    required("IJOB_POSTGRES_IT_USERNAME"),
                    optional("IJOB_POSTGRES_IT_PASSWORD"),
                    database);
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Required PostgreSQL integration setting is missing: " + name);
            }
            return value;
        }

        private static String optional(String name) {
            String value = System.getenv(name);
            return value == null ? "" : value;
        }
    }
}
