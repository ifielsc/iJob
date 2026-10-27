// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

class ApplicationConfigurationTest {
    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void baseConfigurationShouldDeclareSafeApplicationDefaults() throws IOException {
        PropertySource<?> source = load("application.yml");

        assertAll(
                () -> assertEquals("ijob", source.getProperty("spring.application.name")),
                () -> assertEquals("local", source.getProperty("spring.profiles.default")),
                () ->
                        assertEquals(
                                "optional:classpath:database/${IJOB_PLATFORM_DB:mysql}.yml",
                                source.getProperty("spring.config.import")),
                () -> assertEquals("${IJOB_SERVER_PORT:8080}", source.getProperty("server.port")),
                () ->
                        assertEquals(
                                "${IJOB_SCHEDULE_ENABLED:false}",
                                source.getProperty("app.schedule.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_NOTIFICATION_OUTBOX_ENABLED:true}",
                                source.getProperty("app.notification.outbox.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_NOTIFICATION_OUTBOX_RENEW_INTERVAL:10s}",
                                source.getProperty("app.notification.outbox.renew-interval")),
                () ->
                        assertEquals(
                                "${IJOB_NOTIFICATION_OUTBOX_LEASE_DURATION:30s}",
                                source.getProperty("app.notification.outbox.lease-duration")),
                () ->
                        assertEquals(
                                "${IJOB_NOTIFICATION_OUTBOX_MAX_ATTEMPTS:5}",
                                source.getProperty("app.notification.outbox.max-attempts")),
                () ->
                        assertEquals(
                                "${IJOB_CACHE_MAX_STALENESS:5s}",
                                source.getProperty("app.cache-consistency.max-staleness")),
                () ->
                        assertEquals(
                                "${IJOB_RUNTIME_MODE:STANDALONE}",
                                source.getProperty("app.runtime.mode")),
                () ->
                        assertEquals(
                                "${IJOB_PLATFORM_DB:mysql}",
                                source.getProperty("app.platform-database.vendor")),
                () ->
                        assertEquals(
                                "${IJOB_RUNTIME_INSTANCE_NAME:}",
                                source.getProperty("app.runtime.instance-name")),
                () ->
                        assertEquals(
                                "${IJOB_RUNTIME_ROLES:API,SCHEDULER,WORKER,MAINTENANCE}",
                                source.getProperty("app.runtime.roles")),
                () ->
                        assertEquals(
                                "${IJOB_RUNTIME_GROUPS:}",
                                source.getProperty("app.runtime.groups")),
                () ->
                        assertEquals(
                                "${IJOB_RUNTIME_ALLOWED_TASK_IDS:}",
                                source.getProperty("app.runtime.allowed-task-ids")),
                () ->
                        assertEquals(
                                "${IJOB_RUNTIME_ALLOWED_JOB_IDS:}",
                                source.getProperty("app.runtime.allowed-job-ids")),
                () ->
                        assertEquals(
                                "${IJOB_SCHEDULE_ZONE_ID:Asia/Shanghai}",
                                source.getProperty("app.schedule.zone-id")),
                () ->
                        assertEquals(
                                "${IJOB_SINGLETON_LOCK_NAME:ijob:standalone}",
                                source.getProperty("app.runtime.singleton-lock-name")),
                () ->
                        assertEquals(
                                "${IJOB_RUNTIME_HEARTBEAT_INTERVAL:5s}",
                                source.getProperty("app.runtime.heartbeat-interval")),
                () -> assertNull(source.getProperty("app.runtime.recovery-batch-size")),
                () ->
                        assertEquals(
                                "${IJOB_ID_SEGMENT_SIZE:1000}",
                                source.getProperty("app.runtime.id-segment-size")),
                () -> assertNull(source.getProperty("app.runtime.work-queue.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_WORK_QUEUE_SCAN_INTERVAL:1s}",
                                source.getProperty("app.runtime.work-queue.scan-interval")),
                () ->
                        assertEquals(
                                "${IJOB_WORK_QUEUE_RENEW_INTERVAL:5s}",
                                source.getProperty("app.runtime.work-queue.renew-interval")),
                () ->
                        assertEquals(
                                "${IJOB_WORK_QUEUE_LEASE_DURATION:30s}",
                                source.getProperty("app.runtime.work-queue.lease-duration")),
                () ->
                        assertEquals(
                                "${IJOB_WORK_QUEUE_MAX_ATTEMPTS:5}",
                                source.getProperty("app.runtime.work-queue.max-attempts")),
                () ->
                        assertEquals(
                                "${IJOB_RUNTIME_COMMAND_SCAN_INTERVAL:250ms}",
                                source.getProperty("app.runtime.command.scan-interval")),
                () ->
                        assertEquals(
                                "${IJOB_RUNTIME_COMMAND_SCAN_BATCH_SIZE:100}",
                                source.getProperty("app.runtime.command.scan-batch-size")),
                () ->
                        assertEquals(
                                "${IJOB_RUNTIME_COMMAND_RETRY_DELAY:250ms}",
                                source.getProperty("app.runtime.command.retry-delay")),
                () ->
                        assertEquals(
                                "${IJOB_RUNTIME_COMMAND_TIMEOUT:5m}",
                                source.getProperty("app.runtime.command.timeout")),
                () ->
                        assertEquals(
                                "${IJOB_CONCURRENCY_PERMIT_RETRY_INTERVAL:100ms}",
                                source.getProperty(
                                        "app.runtime.concurrency-permit.retry-interval")),
                () ->
                        assertEquals(
                                "${IJOB_CONCURRENCY_PERMIT_RENEW_INTERVAL:5s}",
                                source.getProperty(
                                        "app.runtime.concurrency-permit.renew-interval")),
                () ->
                        assertEquals(
                                "${IJOB_CONCURRENCY_PERMIT_LEASE_DURATION:30s}",
                                source.getProperty(
                                        "app.runtime.concurrency-permit.lease-duration")),
                () ->
                        assertEquals(
                                "${IJOB_MAINTENANCE_ENABLED:true}",
                                source.getProperty("app.runtime.maintenance.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_MAINTENANCE_LEASE_DURATION:30s}",
                                source.getProperty("app.runtime.maintenance.lease-duration")),
                () ->
                        assertEquals(
                                "${IJOB_MAINTENANCE_STALE_RUN_TIMEOUT:30s}",
                                source.getProperty("app.runtime.maintenance.stale-run-timeout")),
                () ->
                        assertEquals(
                                "${IJOB_MAINTENANCE_ORPHAN_GRACE:1m}",
                                source.getProperty("app.runtime.maintenance.orphan-grace")),
                () -> assertEquals("${IJOB_AUTH_MODE:STATIC}", source.getProperty("app.auth.mode")),
                () ->
                        assertEquals(
                                "${IJOB_AUTH_USERNAME:}", source.getProperty("app.auth.username")),
                () ->
                        assertEquals(
                                "${IJOB_AUTH_PASSWORD_HASH:}",
                                source.getProperty("app.auth.password-hash")),
                () ->
                        assertEquals(
                                "${IJOB_CONFIG_AES_KEY:}",
                                source.getProperty("app.security.aes.key")),
                () ->
                        assertEquals(
                                "${IJOB_CONFIG_AES_KEY_ID:local}",
                                source.getProperty("app.security.aes.key-id")),
                () ->
                        assertEquals(
                                "${IJOB_AUTH_TOKEN_NAME:ijob-token}",
                                source.getProperty("sa-token.token-name")),
                () -> assertEquals(Boolean.FALSE, source.getProperty("sa-token.is-read-cookie")),
                () -> assertEquals("graceful", source.getProperty("server.shutdown")),
                () ->
                        assertEquals(
                                "classpath*:mapper/**/*.xml",
                                source.getProperty("mybatis.mapper-locations")),
                () ->
                        assertEquals(
                                "${IJOB_FLYWAY_ENABLED:false}",
                                source.getProperty("spring.flyway.enabled")),
                () ->
                        assertEquals(
                                "classpath:db/release/${IJOB_PLATFORM_DB:mysql}",
                                source.getProperty("spring.flyway.locations")),
                () -> assertEquals("ifj_schema_history", source.getProperty("spring.flyway.table")),
                () ->
                        assertEquals(
                                Boolean.FALSE,
                                source.getProperty("spring.flyway.baseline-on-migrate")),
                () ->
                        assertEquals(
                                Boolean.TRUE,
                                source.getProperty("spring.flyway.validate-on-migrate")),
                () ->
                        assertEquals(
                                Boolean.TRUE, source.getProperty("spring.flyway.clean-disabled")),
                () ->
                        assertEquals(
                                "health,info,metrics",
                                source.getProperty("management.endpoints.web.exposure.include")),
                () ->
                        assertEquals(
                                Boolean.TRUE,
                                source.getProperty("management.endpoint.health.probes.enabled")),
                () ->
                        assertEquals(
                                "readinessState,runtime,singletonLock,coordination,db",
                                source.getProperty(
                                        "management.endpoint.health.group.readiness.include")),
                () ->
                        assertEquals(
                                "${IJOB_HTTP_CONNECT_TIMEOUT:5s}",
                                source.getProperty("app.http.connect-timeout")),
                () ->
                        assertEquals(
                                "${IJOB_HTTP_READ_TIMEOUT:30s}",
                                source.getProperty("app.http.read-timeout")),
                () ->
                        assertEquals(
                                "${IJOB_TRIGGER_POOL_CORE_SIZE:4}",
                                source.getProperty("app.execution.trigger.core-size")),
                () ->
                        assertEquals(
                                "${IJOB_EXECUTION_SHUTDOWN_TIMEOUT:30s}",
                                source.getProperty("app.execution.shutdown-timeout")),
                () ->
                        assertEquals(
                                "${IJOB_JOB_POOL_MAX_SIZE:20}",
                                source.getProperty("app.execution.job.max-size")),
                () ->
                        assertEquals(
                                "${IJOB_JOB_POOL_QUEUE_CAPACITY:0}",
                                source.getProperty("app.execution.job.queue-capacity")),
                () ->
                        assertEquals(
                                "${IJOB_DAG_POOL_MAX_SIZE:32}",
                                source.getProperty("app.execution.dag.max-size")),
                () ->
                        assertEquals(
                                "${IJOB_TASK_POOL_QUEUE_CAPACITY:1000}",
                                source.getProperty("app.execution.task.queue-capacity")),
                () ->
                        assertEquals(
                                "${IJOB_SCRIPT_POOL_KEEP_ALIVE:60s}",
                                source.getProperty("app.execution.script.keep-alive")),
                () ->
                        assertEquals(
                                "${IJOB_PLUGIN_EVENT_ENABLED:true}",
                                source.getProperty("app.plugin.event.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_PLUGIN_SCRIPT_ENABLED:true}",
                                source.getProperty("app.plugin.script.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_PLUGIN_SQL_ENABLED:true}",
                                source.getProperty("app.plugin.sql.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_PLUGIN_FILE_TRANSFER_ENABLED:true}",
                                source.getProperty("app.plugin.file-transfer.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_PLUGIN_API_CALL_ENABLED:true}",
                                source.getProperty("app.plugin.api-call.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_PLUGIN_PUSH_ENABLED:true}",
                                source.getProperty("app.plugin.push.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_PLUGIN_DATA_CHECK_ENABLED:true}",
                                source.getProperty("app.plugin.data-check.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_PLUGIN_SERVER_MONITOR_ENABLED:true}",
                                source.getProperty("app.plugin.server-monitor.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_PLUGIN_ETL_ENABLED:true}",
                                source.getProperty("app.plugin.etl.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_PLUGIN_BATCH_ETL_ENABLED:true}",
                                source.getProperty("app.plugin.batch-etl.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_PLUGIN_LOCAL_JAR_ENABLED:false}",
                                source.getProperty("app.plugin.local-jar.enabled")),
                () ->
                        assertEquals(
                                "${IJOB_PLUGIN_LOCAL_JAR_ROOT:}",
                                source.getProperty("app.plugin.local-jar.root-directory")));
    }

    @Test
    void privateLocalConfigurationShouldNotBeBundled() {
        assertFalse(
                new ClassPathResource("application-local.yml").exists(),
                "Private local configuration must stay outside packaged resources");
    }

    @Test
    void mysqlPlatformConfigurationShouldKeepUtcSessionInitialization() throws IOException {
        PropertySource<?> source = load("database/mysql.yml");

        assertAll(
                () ->
                        assertEquals(
                                "SET time_zone = '+00:00'",
                                source.getProperty("spring.datasource.hikari.connection-init-sql")),
                () -> assertEquals("mysql", source.getProperty("pagehelper.helper-dialect")));
    }

    @Test
    void vendorPlatformConfigurationsShouldDeclarePageHelperDialects() throws IOException {
        assertAll(
                () ->
                        assertEquals(
                                "postgresql",
                                load("database/postgresql.yml")
                                        .getProperty("pagehelper.helper-dialect")),
                () ->
                        assertEquals(
                                "sqlserver2012",
                                load("database/sqlserver.yml")
                                        .getProperty("pagehelper.helper-dialect")),
                () ->
                        assertEquals(
                                "oracle",
                                load("database/oracle.yml")
                                        .getProperty("pagehelper.helper-dialect")),
                () ->
                        assertEquals(
                                "sqlite",
                                load("database/sqlite.yml")
                                        .getProperty("pagehelper.helper-dialect")));
    }

    @Test
    void postgresqlPlatformConfigurationShouldPinUtcAndTheTargetSchema() throws IOException {
        PropertySource<?> source = load("database/postgresql.yml");

        assertAll(
                () ->
                        assertEquals(
                                "SET TIME ZONE 'UTC'",
                                source.getProperty("spring.datasource.hikari.connection-init-sql")),
                () ->
                        assertEquals(
                                "${IJOB_PLATFORM_DB_SCHEMA:public}",
                                source.getProperty("spring.datasource.hikari.schema")),
                () ->
                        assertEquals(
                                "${IJOB_PLATFORM_DB_SCHEMA:public}",
                                source.getProperty("spring.flyway.default-schema")),
                () ->
                        assertEquals(
                                "${IJOB_PLATFORM_DB_SCHEMA:public}",
                                source.getProperty("app.platform-database.schema")));
    }

    @Test
    void oraclePlatformConfigurationShouldPinUtcSchemaAndNullBinding() throws IOException {
        PropertySource<?> source = load("database/oracle.yml");

        assertAll(
                () ->
                        assertEquals(
                                "oracle.jdbc.OracleDriver",
                                source.getProperty("spring.datasource.driver-class-name")),
                () ->
                        assertEquals(
                                "ALTER SESSION SET TIME_ZONE = 'UTC'",
                                source.getProperty("spring.datasource.hikari.connection-init-sql")),
                () ->
                        assertEquals(
                                "${IJOB_PLATFORM_DB_SCHEMA:${spring.datasource.username:${IJOB_DB_USERNAME:ijob}}}",
                                source.getProperty("spring.flyway.default-schema")),
                () ->
                        assertEquals(
                                "${IJOB_PLATFORM_DB_SCHEMA:${spring.datasource.username:${IJOB_DB_USERNAME:ijob}}}",
                                source.getProperty("app.platform-database.schema")),
                () ->
                        assertEquals(
                                "NULL",
                                source.getProperty("mybatis.configuration.jdbc-type-for-null")));
    }

    @Test
    void oracleSchemaUsesFirstRunDatasourceAccountInsteadOfAnUnrelatedEnvironmentDefault()
            throws IOException {
        var environment =
                new MockEnvironment()
                        .withProperty("spring.datasource.username", "WINDOWS_APP")
                        .withProperty("IJOB_DB_USERNAME", "OTHER_ACCOUNT");
        assertOracleSchema(environment, "WINDOWS_APP");
    }

    @Test
    void explicitOracleSchemaStillOverridesDatasourceAccount() throws IOException {
        var environment =
                new MockEnvironment()
                        .withProperty("spring.datasource.username", "WINDOWS_APP")
                        .withProperty("IJOB_PLATFORM_DB_SCHEMA", "EXPLICIT_OWNER");
        assertOracleSchema(environment, "EXPLICIT_OWNER");
    }

    @Test
    void legacyOracleEnvironmentAccountRemainsSupported() throws IOException {
        assertOracleSchema(
                new MockEnvironment().withProperty("IJOB_DB_USERNAME", "LEGACY_OWNER"),
                "LEGACY_OWNER");
    }

    @Test
    void oracleUnconfiguredFallbackRemainsUnchanged() throws IOException {
        assertOracleSchema(new MockEnvironment(), "ijob");
    }

    private void assertOracleSchema(MockEnvironment environment, String expected)
            throws IOException {
        environment.getPropertySources().addLast(load("database/oracle.yml"));
        assertEquals(expected, environment.getProperty("spring.flyway.default-schema"));
        assertEquals(expected, environment.getProperty("app.platform-database.schema"));
    }

    @Test
    void sqlitePlatformConfigurationShouldPinFileSafetyAndWriterSettings() throws IOException {
        PropertySource<?> source = load("database/sqlite.yml");

        assertAll(
                () ->
                        assertEquals(
                                "org.sqlite.JDBC",
                                source.getProperty("spring.datasource.driver-class-name")),
                () ->
                        assertEquals(
                                "PRAGMA foreign_keys = ON",
                                source.getProperty("spring.datasource.hikari.connection-init-sql")),
                () ->
                        assertEquals(
                                "${IJOB_SQLITE_BUSY_TIMEOUT_MS:5000}",
                                source.getProperty(
                                        "spring.datasource.hikari.data-source-properties.busy_timeout")),
                () ->
                        assertEquals(
                                "WAL",
                                source.getProperty(
                                        "spring.datasource.hikari.data-source-properties.journal_mode")),
                () ->
                        assertEquals(
                                "IMMEDIATE",
                                source.getProperty(
                                        "spring.datasource.hikari.data-source-properties.transaction_mode")));
    }

    @Test
    void mailConfigurationShouldNotProvideAnyDeploymentSmtpAccount() throws IOException {
        PropertySource<?> source = load("application-mail.yml");
        assertAll(
                () -> assertEquals("mail", source.getProperty("spring.config.activate.on-profile")),
                () -> assertEquals(null, source.getProperty("spring.mail.host")),
                () -> assertEquals(null, source.getProperty("spring.mail.username")),
                () -> assertEquals(null, source.getProperty("spring.mail.password")),
                () -> assertEquals(Boolean.TRUE, source.getProperty("app.notify.email.enabled")));
    }

    @Test
    void baseConfigurationShouldNotExposeRemovedCompatibilityAliases() throws IOException {
        String yaml =
                new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);

        assertAll(
                () -> assertFalse(yaml.contains("IJOB_WORK_QUEUE_ENABLED")),
                () -> assertFalse(yaml.contains("IJOB_RECOVERY_BATCH_SIZE")),
                () -> assertFalse(yaml.contains("IJOB_RESOURCE_AES_KEY")),
                () -> assertFalse(yaml.contains("IJOB_AUTH_ENABLED")));
    }

    private PropertySource<?> load(String resourceName) throws IOException {
        List<PropertySource<?>> sources =
                loader.load(resourceName, new ClassPathResource(resourceName));
        assertEquals(1, sources.size());
        return sources.getFirst();
    }
}
