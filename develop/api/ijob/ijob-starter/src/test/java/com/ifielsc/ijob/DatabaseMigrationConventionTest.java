// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.infra.entity.calendar.Calendar;
import com.ifielsc.ijob.infra.entity.calendar.CalendarException;
import com.ifielsc.ijob.infra.entity.event.EventBinding;
import com.ifielsc.ijob.infra.entity.job.Job;
import com.ifielsc.ijob.infra.entity.notify.NotifyBinding;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;
import com.ifielsc.ijob.infra.entity.notify.NotifySendLog;
import com.ifielsc.ijob.infra.entity.resource.Resource;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.entity.runtime.InstanceLog;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;
import com.ifielsc.ijob.infra.entity.schedule.Schedule;
import com.ifielsc.ijob.infra.entity.task.Task;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class DatabaseMigrationConventionTest {
    private static final String BASELINE_MIGRATION = "db/migration/mysql/V1__baseline.sql";
    private static final String NOTIFY_SEND_LOG_MIGRATION =
            "db/migration/mysql/V2__notify_send_log.sql";
    private static final String JOB_DEFINITION_MIGRATION =
            "db/migration/mysql/V3__job_definition_revision.sql";
    private static final String TASK_DEFINITION_MIGRATION =
            "db/migration/mysql/V4__task_definition_revision.sql";
    private static final String RUNTIME_STATE_MIGRATION =
            "db/migration/mysql/V5__runtime_state_constraints.sql";
    private static final String RUNTIME_VARIABLE_MIGRATION =
            "db/migration/mysql/V6__runtime_variable_snapshots.sql";
    private static final String ETL_CHECKPOINT_MIGRATION =
            "db/migration/mysql/V7__etl_checkpoint.sql";
    private static final String EVENT_RUNTIME_MIGRATION =
            "db/migration/mysql/V8__event_runtime.sql";
    private static final String STANDALONE_RECOVERY_MIGRATION =
            "db/migration/mysql/V9__standalone_runtime_recovery.sql";
    private static final String RUNTIME_ERROR_METADATA_MIGRATION =
            "db/migration/mysql/V10__runtime_error_metadata.sql";
    private static final String COORDINATION_FOUNDATION_MIGRATION =
            "db/migration/mysql/V11__coordination_foundation.sql";
    private static final String PERSISTENT_WORK_QUEUE_MIGRATION =
            "db/migration/mysql/V12__persistent_trigger_work_queue.sql";
    private static final String ROOT_EXECUTION_FENCING_MIGRATION =
            "db/migration/mysql/V13__root_execution_fencing.sql";
    private static final String RUNTIME_COMMAND_PERMIT_MIGRATION =
            "db/migration/mysql/V14__runtime_commands_and_concurrency_permits.sql";
    private static final String ENTRY_NOTIFICATION_CACHE_MIGRATION =
            "db/migration/mysql/V15__entry_notification_and_cache_consistency.sql";
    private static final String MAINTENANCE_RECOVERY_MIGRATION =
            "db/migration/mysql/V16__maintenance_leader_and_runtime_deadline.sql";
    private static final String CLUSTER_SHARED_STATE_MIGRATION =
            "db/migration/mysql/V17__cluster_shared_state.sql";
    private static final String RUNTIME_COORDINATION_HARDENING_MIGRATION =
            "db/migration/mysql/V18__runtime_coordination_hardening.sql";
    private static final String PLATFORM_PORTABILITY_FOUNDATION_MIGRATION =
            "db/migration/mysql/V19__platform_portability_foundation.sql";
    private static final String EXECUTION_GROUP_ROUTING_MIGRATION =
            "db/migration/mysql/V20__execution_group_routing.sql";
    private static final String CONDITIONAL_DAG_MIGRATION =
            "db/migration/mysql/V21__conditional_dag.sql";
    private static final String EXECUTION_TARGET_ROUTING_MIGRATION =
            "db/migration/mysql/V22__execution_target_routing.sql";
    private static final String COMPOSITE_TASK_CONCURRENCY_MIGRATION =
            "db/migration/mysql/V23__composite_task_concurrency.sql";
    private static final String SCHEDULE_MISFIRE_MIGRATION =
            "db/migration/mysql/V24__schedule_misfire_and_queue_expiry.sql";
    private static final String RUNTIME_HISTORY_MIGRATION =
            "db/migration/mysql/V25__runtime_history_and_permit_wait.sql";
    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?i)CREATE\\s+TABLE\\s+([a-z0-9_]+)");
    private static final Pattern VALID_TABLE_NAME = Pattern.compile("ifj_[a-z0-9_]+");
    private static final Pattern TABLE_BODY =
            Pattern.compile("CREATE TABLE ([a-z0-9_]+) \\((.*?)\\) ENGINE", Pattern.DOTALL);
    private static final Pattern COLUMN = Pattern.compile("(?m)^    ([a-z][a-z0-9_]*)\\s+");
    private static final Pattern MAPPER_TABLE =
            Pattern.compile("(?i)\\b(?:FROM|INTO|UPDATE|JOIN)\\s+(ifj_[a-z0-9_]+)");
    private static final Pattern ALTER_TABLE =
            Pattern.compile("(?i)ALTER\\s+TABLE\\s+([a-z0-9_]+)");
    private static final Pattern ALTER_STATEMENT =
            Pattern.compile("(?is)ALTER\\s+TABLE\\s+([a-z0-9_]+)\\s+(.*?);");
    private static final Pattern ALTER_COLUMN =
            Pattern.compile("(?im)^\\s*ADD\\s+COLUMN\\s+([a-z][a-z0-9_]*)\\s+");
    private static final Pattern COLUMN_DEFINITION =
            Pattern.compile("(?m)^    ([a-z][a-z0-9_]*\\s+[^,\\r\\n]+),?$");
    private static final Set<String> EXPECTED_TABLES =
            Set.of(
                    "ifj_calendar",
                    "ifj_calendar_exception",
                    "ifj_concurrency_policy",
                    "ifj_event_binding",
                    "ifj_instance",
                    "ifj_instance_log",
                    "ifj_job",
                    "ifj_job_edge",
                    "ifj_job_node",
                    "ifj_job_node_policy",
                    "ifj_notify_binding",
                    "ifj_notify_config",
                    "ifj_resource",
                    "ifj_schedule",
                    "ifj_task",
                    "ifj_trigger");
    private static final Map<String, Class<?>> TABLE_ENTITIES =
            Map.ofEntries(
                    Map.entry("ifj_calendar", Calendar.class),
                    Map.entry("ifj_calendar_exception", CalendarException.class),
                    Map.entry("ifj_event_binding", EventBinding.class),
                    Map.entry("ifj_instance", Instance.class),
                    Map.entry("ifj_instance_log", InstanceLog.class),
                    Map.entry("ifj_job", Job.class),
                    Map.entry("ifj_notify_binding", NotifyBinding.class),
                    Map.entry("ifj_notify_config", NotifyConfig.class),
                    Map.entry("ifj_resource", Resource.class),
                    Map.entry("ifj_schedule", Schedule.class),
                    Map.entry("ifj_task", Task.class),
                    Map.entry("ifj_trigger", Trigger.class));
    private static final Map<String, Set<String>> POST_BASELINE_COLUMNS =
            Map.of(
                    "ifj_trigger",
                    Set.of(
                            "business_date",
                            "business_time",
                            "business_zone_id",
                            "definition_id",
                            "task_definition_id",
                            "variables",
                            "event_id",
                            "event_binding_id",
                            "idempotency_key",
                            "error_code",
                            "message_key",
                            "message_args"),
                    "ifj_instance",
                    Set.of(
                            "definition_id",
                            "definition_node_id",
                            "task_definition_id",
                            "input_variables",
                            "output_variables",
                            "error_code",
                            "message_key",
                            "message_args",
                            "execution_fence",
                            "timeout_at",
                            "wait_reason",
                            "permit_wait_deadline"),
                    "ifj_schedule",
                    Set.of(
                            "parameter_form",
                            "revision",
                            "variables",
                            "fire_claim_token",
                            "fire_claimed_at",
                            "misfire_policy",
                            "misfire_grace_sec",
                            "max_catchup_count",
                            "max_queue_delay_sec"),
                    "ifj_task",
                    Set.of(
                            "current_task_definition_id",
                            "current_revision",
                            "retired",
                            "retired_time"));
    private static final Map<String, Set<String>> RETIRED_BASELINE_COLUMNS =
            Map.of(
                    "ifj_job", Set.of("concurrency_type", "lock_key", "max_concurrency"),
                    "ifj_task", Set.of("concurrency_type", "lock_key", "max_concurrency"));
    private static final Set<String> NOTIFY_SEND_LOG_COLUMNS =
            Set.of(
                    "id",
                    "source_type",
                    "correlation_id",
                    "operator_id",
                    "target_type",
                    "target_id",
                    "instance_id",
                    "notify_binding_id",
                    "notify_config_id",
                    "notify_type",
                    "result",
                    "failure_code",
                    "failure_summary",
                    "duration_ms",
                    "create_time",
                    "update_time");
    private static final Map<String, Set<String>> JOB_DEFINITION_COLUMNS =
            Map.of(
                    "ifj_job_definition",
                    Set.of(
                            "id",
                            "job_id",
                            "revision",
                            "name",
                            "timeout",
                            "enabled",
                            "description",
                            "create_time"),
                    "ifj_job_definition_node",
                    Set.of(
                            "id",
                            "definition_id",
                            "node_key",
                            "name",
                            "node_type",
                            "ref_id",
                            "create_time"),
                    "ifj_job_definition_node_policy",
                    Set.of(
                            "id",
                            "definition_id",
                            "node_id",
                            "fail_strategy",
                            "execute_condition",
                            "create_time"),
                    "ifj_job_definition_edge",
                    Set.of("id", "definition_id", "from_node_id", "to_node_id", "create_time"));
    private static final Set<String> JOB_DEFINITION_TABLES = JOB_DEFINITION_COLUMNS.keySet();
    private static final Set<String> JOB_DEFINITION_ALTER_TABLES =
            Set.of("ifj_job", "ifj_trigger", "ifj_instance");
    private static final List<String> TASK_DEFINITION_ALTER_TABLES =
            List.of("ifj_job", "ifj_task", "ifj_trigger", "ifj_instance");
    private static final List<String> TASK_DEFINITION_COLUMN_DEFINITIONS =
            List.of(
                    "id bigint not null",
                    "task_id bigint not null",
                    "revision bigint not null",
                    "name varchar(128) not null",
                    "plugin_type varchar(32) not null",
                    "plugin_config longtext not null",
                    "retry_times int not null",
                    "retry_interval int not null",
                    "timeout int null",
                    "enabled tinyint(1) not null",
                    "description varchar(500) null",
                    "concurrency_type varchar(32) not null",
                    "lock_key varchar(255) null",
                    "max_concurrency int null",
                    "create_time datetime(3) not null default " + "current_timestamp(3)");

    @Test
    void mysqlBaselineShouldCreateOnlyExpectedPrefixedTables() throws IOException {
        String sql = readBaselineMigration();
        Matcher matcher = CREATE_TABLE.matcher(sql);
        Set<String> actualTables = new LinkedHashSet<>();
        while (matcher.find()) {
            String tableName = matcher.group(1);
            assertTrue(VALID_TABLE_NAME.matcher(tableName).matches());
            actualTables.add(tableName);
        }

        assertEquals(EXPECTED_TABLES, actualTables);
    }

    @Test
    void mysqlBaselineColumnsShouldMatchCurrentInfraEntities() throws IOException {
        Matcher tableMatcher = TABLE_BODY.matcher(readBaselineMigration());
        Map<String, Set<String>> tableColumns = new java.util.HashMap<>();
        while (tableMatcher.find()) {
            Matcher columnMatcher = COLUMN.matcher(tableMatcher.group(2));
            Set<String> columns = new LinkedHashSet<>();
            while (columnMatcher.find()) {
                columns.add(columnMatcher.group(1));
            }
            tableColumns.put(tableMatcher.group(1), columns);
        }

        TABLE_ENTITIES.forEach(
                (table, entity) -> {
                    Set<String> baselineEntityColumns = entityColumns(entity);
                    baselineEntityColumns.removeAll(
                            POST_BASELINE_COLUMNS.getOrDefault(table, Set.of()));
                    Set<String> actualColumns = new LinkedHashSet<>(tableColumns.get(table));
                    actualColumns.removeAll(RETIRED_BASELINE_COLUMNS.getOrDefault(table, Set.of()));
                    assertEquals(
                            baselineEntityColumns,
                            actualColumns,
                            () -> "Column mismatch for " + table);
                });
    }

    @Test
    void runtimeEntitiesShouldMatchAllRuntimeSnapshotMigrations() throws IOException {
        Map<String, Set<String>> baselineColumns = tableColumns(readBaselineMigration());
        String v3 = readResource(JOB_DEFINITION_MIGRATION);
        String v4 = readResource(TASK_DEFINITION_MIGRATION);
        String v6 = readResource(RUNTIME_VARIABLE_MIGRATION);
        String v8 = readResource(EVENT_RUNTIME_MIGRATION);
        String v10 = readResource(RUNTIME_ERROR_METADATA_MIGRATION);
        String v12 = readResource(PERSISTENT_WORK_QUEUE_MIGRATION);
        String v13 = readResource(ROOT_EXECUTION_FENCING_MIGRATION);
        String v15 = readResource(ENTRY_NOTIFICATION_CACHE_MIGRATION);
        String v16 = readResource(MAINTENANCE_RECOVERY_MIGRATION);
        String v24 = readResource(SCHEDULE_MISFIRE_MIGRATION);
        String v25 = readResource(RUNTIME_HISTORY_MIGRATION);
        String v26 = readResource("db/migration/mysql/V26__runtime_mutation_guards.sql");

        String v36 = readResource("db/migration/mysql/V36__parameter_templates.sql");
        String v37 = readResource("db/migration/mysql/V37__trigger_business_time.sql");
        Map.of(
                        "ifj_schedule", Schedule.class,
                        "ifj_trigger", Trigger.class,
                        "ifj_instance", Instance.class)
                .forEach(
                        (table, entity) -> {
                            Set<String> versionedColumns =
                                    new LinkedHashSet<>(baselineColumns.get(table));
                            versionedColumns.addAll(alterColumns(v3, table));
                            versionedColumns.addAll(alterColumns(v4, table));
                            versionedColumns.addAll(alterColumns(v6, table));
                            versionedColumns.addAll(alterColumns(v8, table));
                            versionedColumns.addAll(alterColumns(v10, table));
                            versionedColumns.addAll(alterColumns(v12, table));
                            versionedColumns.addAll(alterColumns(v13, table));
                            versionedColumns.addAll(alterColumns(v15, table));
                            versionedColumns.addAll(alterColumns(v16, table));
                            versionedColumns.addAll(alterColumns(v24, table));
                            versionedColumns.addAll(alterColumns(v25, table));
                            versionedColumns.addAll(alterColumns(v26, table));
                            versionedColumns.addAll(alterColumns(v36, table));
                            versionedColumns.addAll(alterColumns(v37, table));
                            assertEquals(
                                    entityColumns(entity),
                                    versionedColumns,
                                    () -> "Versioned column mismatch for " + table);
                        });
    }

    @Test
    void mysqlBaselineShouldNotContainUnsafeDatabaseOperations() throws IOException {
        String sql = readBaselineMigration().toLowerCase();

        assertFalse(sql.contains("auto_increment"));
        assertFalse(sql.contains("drop table"));
        assertFalse(sql.contains("truncate table"));
        assertFalse(sql.contains("delete from"));
        assertFalse(sql.contains("insert into"));
        assertFalse(sql.contains("flyway_schema_history"));
        assertFalse(sql.contains("jdbc:"));
    }

    @Test
    void mapperXmlTablesShouldExistInMysqlBaseline() throws IOException {
        Set<String> mapperTables = new LinkedHashSet<>();
        for (String mapperResource :
                List.of("mapper/task/TaskMapper.xml", "mapper/schedule/ScheduleMapper.xml")) {
            String xml = readResource(mapperResource);
            assertFalse(xml.contains("${"));
            Matcher matcher = MAPPER_TABLE.matcher(xml);
            while (matcher.find()) {
                mapperTables.add(matcher.group(1));
            }
        }

        assertEquals(
                Set.of("ifj_task", "ifj_task_definition_group", "ifj_schedule", "ifj_job"),
                mapperTables);
        Set<String> versionedTables = new LinkedHashSet<>(EXPECTED_TABLES);
        Matcher v20Tables = CREATE_TABLE.matcher(readResource(EXECUTION_GROUP_ROUTING_MIGRATION));
        while (v20Tables.find()) {
            versionedTables.add(v20Tables.group(1));
        }
        assertTrue(versionedTables.containsAll(mapperTables));
    }

    @Test
    void mysqlNotifySendLogMigrationShouldBeSchemaOnlyAndSafe() throws IOException {
        String sql = readResource(NOTIFY_SEND_LOG_MIGRATION);
        String normalized = sql.replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        Matcher createMatcher = CREATE_TABLE.matcher(sql);
        Set<String> tables = new LinkedHashSet<>();
        while (createMatcher.find()) {
            String table = createMatcher.group(1);
            assertTrue(VALID_TABLE_NAME.matcher(table).matches());
            tables.add(table);
        }

        assertEquals(Set.of("ifj_notify_send_log"), tables);
        assertEquals(NOTIFY_SEND_LOG_COLUMNS, tableColumns(sql).get("ifj_notify_send_log"));
        assertEquals(
                entityColumns(NotifySendLog.class), tableColumns(sql).get("ifj_notify_send_log"));
        assertTrue(normalized.contains("constraint pk_ifj_notify_send_log primary key (id)"));
        assertTrue(
                normalized.contains(
                        "index idx_ifj_notify_send_log_create_time " + "(create_time, id)"));
        assertTrue(
                normalized.contains(
                        "index idx_ifj_notify_send_log_config_time "
                                + "(notify_config_id, create_time)"));
        assertTrue(
                normalized.contains(
                        "index idx_ifj_notify_send_log_target_time "
                                + "(target_type, target_id, create_time)"));
        assertTrue(
                normalized.contains(
                        "index idx_ifj_notify_send_log_instance_time "
                                + "(instance_id, create_time)"));
        assertTrue(
                normalized.contains(
                        "index idx_ifj_notify_send_log_result_time " + "(result, create_time)"));
        assertTrue(normalized.contains("notify_config_id bigint not null"));
        assertTrue(normalized.contains("failure_summary varchar(500) null"));
        assertTrue(normalized.contains("duration_ms bigint null"));
        assertTrue(normalized.contains("engine = innodb default charset = utf8mb4"));

        assertFalse(normalized.contains("foreign key"));
        assertFalse(normalized.contains(" references "));
        assertFalse(normalized.contains("auto_increment"));
        assertFalse(normalized.contains("alter table"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("truncate table"));
        assertFalse(normalized.contains("delete from"));
        assertFalse(normalized.contains("insert into"));
        assertFalse(normalized.contains("flyway_schema_history"));
        assertFalse(normalized.contains("jdbc:"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void mysqlJobEdgeConstraintsShouldProtectPathAndEndpoints() throws IOException {
        String normalized =
                readBaselineMigration().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);

        assertTrue(
                normalized.contains(
                        "constraint uk_ifj_job_edge_path unique "
                                + "(job_id, from_node_id, to_node_id)"));
        assertTrue(
                normalized.contains(
                        "constraint fk_ifj_job_edge_job foreign key (job_id) "
                                + "references ifj_job (id) on delete cascade"));
        assertTrue(
                normalized.contains(
                        "constraint fk_ifj_job_edge_from_node foreign key "
                                + "(from_node_id) references ifj_job_node (id) "
                                + "on delete cascade"));
        assertTrue(
                normalized.contains(
                        "constraint fk_ifj_job_edge_to_node foreign key "
                                + "(to_node_id) references ifj_job_node (id) "
                                + "on delete cascade"));
    }

    @Test
    void mysqlJobNodePolicyConstraintsShouldProtectOnePolicyPerNode() throws IOException {
        String normalized =
                readBaselineMigration().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);

        assertTrue(
                normalized.contains(
                        "constraint uk_ifj_job_node_policy_node_id " + "unique (node_id)"));
        assertTrue(
                normalized.contains(
                        "constraint fk_ifj_job_node_policy_node "
                                + "foreign key (node_id) references "
                                + "ifj_job_node (id) on delete cascade"));
    }

    @Test
    void mysqlMigrationInventoryAndFrozenHistoryShouldMatch() throws IOException {
        assertEquals(
                "d6e70231ac4a24fc09b862352516ce77465a8aff68e38c7c5aa83ff1983c34f2",
                sha256(readResource(BASELINE_MIGRATION)));
        assertEquals(
                "fe6f189dd391c324707adadfa3f523ba0bce3f19c4253fa8642764a552c77e48",
                sha256(readResource(NOTIFY_SEND_LOG_MIGRATION)));
        assertEquals(
                "095dc6499a147ed1f7b4a9f31e62cb84ba29da283ce4d0612c642346fe8b7b9e",
                sha256(readResource(JOB_DEFINITION_MIGRATION)));
        assertEquals(
                "547536af4d3c63d9b85d0160ff304ffde2fae772e6b0887e6d1878709c86dd56",
                sha256(readResource(TASK_DEFINITION_MIGRATION)));
        assertEquals(
                "fbdeeb7dd495fab15b17e09252f312a0665c492ca45d4320e6c395e1eb3abbb0",
                sha256(readResource(RUNTIME_STATE_MIGRATION)));

        var resolver = new PathMatchingResourcePatternResolver();
        Set<String> migrationNames = new LinkedHashSet<>();
        for (var resource : resolver.getResources("classpath*:db/migration/mysql/V*.sql")) {
            migrationNames.add(resource.getFilename());
        }
        assertEquals(
                Set.of(
                        "V1__baseline.sql",
                        "V2__notify_send_log.sql",
                        "V3__job_definition_revision.sql",
                        "V4__task_definition_revision.sql",
                        "V5__runtime_state_constraints.sql",
                        "V6__runtime_variable_snapshots.sql",
                        "V7__etl_checkpoint.sql",
                        "V8__event_runtime.sql",
                        "V9__standalone_runtime_recovery.sql",
                        "V10__runtime_error_metadata.sql",
                        "V11__coordination_foundation.sql",
                        "V12__persistent_trigger_work_queue.sql",
                        "V13__root_execution_fencing.sql",
                        "V14__runtime_commands_and_concurrency_permits.sql",
                        "V15__entry_notification_and_cache_consistency.sql",
                        "V16__maintenance_leader_and_runtime_deadline.sql",
                        "V17__cluster_shared_state.sql",
                        "V18__runtime_coordination_hardening.sql",
                        "V19__platform_portability_foundation.sql",
                        "V20__execution_group_routing.sql",
                        "V21__conditional_dag.sql",
                        "V22__execution_target_routing.sql",
                        "V23__composite_task_concurrency.sql",
                        "V24__schedule_misfire_and_queue_expiry.sql",
                        "V25__runtime_history_and_permit_wait.sql",
                        "V26__runtime_mutation_guards.sql",
                        "V27__security_foundation.sql",
                        "V28__job_canvas_layout.sql",
                        "V29__concurrency_resource_revision.sql",
                        "V30__etl_file_incremental.sql",
                        "V31__gateway_snapshot_constraint.sql",
                        "V32__mail_channels.sql",
                        "V33__nullable_instance_node_uniqueness.sql",
                        "V34__table_primary_id_namespaces.sql",
                        "V35__remove_legacy_job_graph.sql",
                        "V36__parameter_templates.sql",
                        "V37__trigger_business_time.sql",
                        "V38__notification_channels.sql"),
                migrationNames);

        int createTableCount = 0;
        for (String migration :
                List.of(
                        BASELINE_MIGRATION,
                        NOTIFY_SEND_LOG_MIGRATION,
                        JOB_DEFINITION_MIGRATION,
                        TASK_DEFINITION_MIGRATION,
                        RUNTIME_STATE_MIGRATION,
                        RUNTIME_VARIABLE_MIGRATION,
                        ETL_CHECKPOINT_MIGRATION,
                        EVENT_RUNTIME_MIGRATION,
                        STANDALONE_RECOVERY_MIGRATION,
                        RUNTIME_ERROR_METADATA_MIGRATION,
                        COORDINATION_FOUNDATION_MIGRATION,
                        PERSISTENT_WORK_QUEUE_MIGRATION,
                        ROOT_EXECUTION_FENCING_MIGRATION,
                        RUNTIME_COMMAND_PERMIT_MIGRATION,
                        ENTRY_NOTIFICATION_CACHE_MIGRATION,
                        MAINTENANCE_RECOVERY_MIGRATION,
                        CLUSTER_SHARED_STATE_MIGRATION,
                        RUNTIME_COORDINATION_HARDENING_MIGRATION,
                        PLATFORM_PORTABILITY_FOUNDATION_MIGRATION,
                        EXECUTION_GROUP_ROUTING_MIGRATION,
                        CONDITIONAL_DAG_MIGRATION,
                        EXECUTION_TARGET_ROUTING_MIGRATION,
                        COMPOSITE_TASK_CONCURRENCY_MIGRATION)) {
            Matcher matcher = CREATE_TABLE.matcher(readResource(migration));
            while (matcher.find()) {
                createTableCount++;
            }
        }
        assertEquals(42, createTableCount);
    }

    @Test
    void securityFoundationContainsExactlyTwoTablesAndNoSeededCredentials() throws IOException {
        String sql = readResource("db/migration/mysql/V27__security_foundation.sql");
        Map<String, Set<String>> columns = tableColumns(sql);
        assertEquals(Set.of("ifj_auth_user", "ifj_operation_audit"), columns.keySet());
        assertEquals(
                entityColumns(com.ifielsc.ijob.infra.entity.auth.AuthUser.class),
                columns.get("ifj_auth_user"));
        assertEquals(
                entityColumns(com.ifielsc.ijob.infra.entity.auth.OperationAudit.class),
                columns.get("ifj_operation_audit"));
        String normalized = normalizeSql(sql);
        assertTrue(normalized.contains("unique (login_id)"));
        assertTrue(normalized.contains("unique (username)"));
        assertTrue(normalized.contains("'auth-accounts'"));
        assertTrue(normalized.contains("capability_version = 27"));
        assertFalse(normalized.contains("insert into ifj_auth_user"));
        assertFalse(normalized.contains("drop table"));
    }

    @Test
    void mysqlConditionalDagShouldFreezeGatewayAndDecisionInvariants() throws IOException {
        String sql = readResource(CONDITIONAL_DAG_MIGRATION);
        String normalized = normalizeSql(sql);

        assertTrue(
                normalized.contains(
                        "add column node_kind varchar(16) not null default 'execution'"));
        assertTrue(normalized.contains("gateway_mode = 'exclusive'"));
        assertTrue(normalized.contains("add column route_key varchar(64) null"));
        assertTrue(normalized.contains("add column default_route boolean not null default false"));
        assertTrue(normalized.contains("create table ifj_dag_gateway_decision"));
        assertTrue(
                normalized.contains("unique (job_instance_id, definition_node_id, evaluation_no)"));
        assertTrue(normalized.contains("execution_fence bigint not null"));
        assertTrue(normalized.contains("capability_version = 21"));
        assertTrue(normalized.contains("'conditional-dag', 1, true"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("truncate table"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void mysqlExecutionGroupRoutingShouldBeAdditiveAndGuarded() throws IOException {
        String sql = readResource(EXECUTION_GROUP_ROUTING_MIGRATION);
        String normalized = sql.replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);

        assertTrue(normalized.contains("create table ifj_task_definition_group"));
        assertTrue(normalized.contains("create table ifj_job_definition_group"));
        assertTrue(normalized.contains("primary key (definition_id, group_code)"));
        assertTrue(normalized.contains("references ifj_task_definition (id) on delete cascade"));
        assertTrue(normalized.contains("references ifj_job_definition (id) on delete cascade"));
        assertTrue(
                normalized.contains("add column worker_groups varchar(1100) not null default ''"));
        assertTrue(
                normalized.contains("add column runtime_protocol_version int not null default 1"));
        assertTrue(normalized.contains("capability_version = 20"));
        assertTrue(normalized.contains("'execution-group-routing', 1, true"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("truncate table"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void mysqlExecutionTargetRoutingShouldBeAdditiveAndGuarded() throws IOException {
        String sql = readResource(EXECUTION_TARGET_ROUTING_MIGRATION);
        String normalized = normalizeSql(sql);

        assertTrue(
                normalized.contains(
                        "add column worker_task_ids varchar(2100) not null default ''"));
        assertTrue(
                normalized.contains("add column worker_job_ids varchar(2100) not null default ''"));
        assertTrue(normalized.contains("capability_version = 22"));
        assertTrue(normalized.contains("'execution-target-routing', 1, true"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("truncate table"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void everyPlatformShouldPublishTheV22ExecutionTargetRoutingMigration() throws IOException {
        for (String vendor : List.of("mysql", "postgresql", "sqlserver", "oracle", "sqlite")) {
            String path = "db/migration/" + vendor + "/V22__execution_target_routing.sql";
            String sql = readResource(path);
            String normalized = normalizeSql(sql);

            assertTrue(normalized.contains("worker_task_ids"), path);
            assertTrue(normalized.contains("worker_job_ids"), path);
            assertTrue(normalized.contains("capability_version = 22"), path);
            assertTrue(normalized.contains("'execution-target-routing', 1"), path);
            assertFalse(normalized.contains("drop table"), path);
            assertFalse(normalized.contains("truncate table"), path);
            assertFalse(sql.contains("${"), path);
        }
    }

    @Test
    void everyPlatformShouldReplaceLegacyConcurrencyWithV23CompositePolicies() throws IOException {
        for (String vendor : List.of("mysql", "postgresql", "sqlserver", "oracle", "sqlite")) {
            String path = "db/migration/" + vendor + "/V23__composite_task_concurrency.sql";
            String normalized = normalizeSql(readResource(path));

            assertTrue(normalized.contains("create table"), path);
            assertTrue(normalized.contains("ifj_plugin_concurrency_policy"), path);
            assertTrue(normalized.contains("ifj_concurrency_resource"), path);
            assertTrue(normalized.contains("ifj_task_concurrency_policy"), path);
            assertTrue(normalized.contains("ifj_task_concurrency_resource"), path);
            assertTrue(
                    normalized.contains("drop table ifj_concurrency_policy")
                            || normalized.contains("drop table dbo.ifj_concurrency_policy"),
                    path);
            assertTrue(
                    normalized.contains("drop column concurrency_type")
                            || normalized.contains(
                                    "drop (concurrency_type, lock_key, max_concurrency)")
                            || normalized.contains("create table ifj_task_v23"),
                    path);
            assertTrue(
                    normalized.contains("capability_version=23")
                            || normalized.contains("capability_version = 23"),
                    path);
            assertTrue(
                    normalized.contains("'composite-task-concurrency', 1")
                            || normalized.contains("'composite-task-concurrency',1"),
                    path);
            assertFalse(readResource(path).contains("${"), path);
        }
    }

    @Test
    void mysqlPlatformPortabilityFoundationShouldBeAdditiveAndGuarded() throws IOException {
        String sql = readResource(PLATFORM_PORTABILITY_FOUNDATION_MIGRATION);
        String normalized = sql.replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);

        assertTrue(normalized.contains("json_valid(allowed_operator_ids)"));
        assertTrue(normalized.contains("json_type(allowed_operator_ids) <> 'array'"));
        assertTrue(normalized.contains("json_length(allowed_operator_ids) = 0"));
        assertTrue(normalized.contains("join json_table("));
        assertTrue(normalized.contains("create table ifj_event_wait_operator"));
        assertTrue(
                normalized.contains(
                        "operator_id varchar(128) character set utf8mb4 "
                                + "collate utf8mb4_bin not null"));
        assertTrue(normalized.contains("primary key (wait_id, operator_id)"));
        assertTrue(normalized.contains("references ifj_event_wait (id) on delete cascade"));
        assertTrue(
                normalized.contains(
                        "index idx_ifj_wait_operator_lookup " + "(operator_id, wait_id)"));
        assertTrue(normalized.contains("insert into ifj_event_wait_operator"));
        assertTrue(normalized.contains("select distinct wait_record.id"));
        assertTrue(normalized.contains("create table ifj_schema_capability"));
        assertTrue(normalized.contains("'platform-schema', 19, 1"));
        assertTrue(normalized.contains("'event-wait-operator-relation', 1, 1"));
        assertTrue(normalized.contains("'stable-id-segment-seed', 1, 1"));
        assertEquals(25, normalized.split("select max\\(id\\) from", -1).length - 1);
        assertTrue(normalized.contains("on duplicate key update next_value = greatest("));

        assertFalse(normalized.contains("auto_increment"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("truncate table"));
        assertFalse(normalized.contains("delete from"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void runtimeCoordinationHardeningMigrationShouldAddCommandExpiryIndex() throws IOException {
        String normalized = normalizeSql(readResource(RUNTIME_COORDINATION_HARDENING_MIGRATION));

        assertTrue(normalized.contains("alter table ifj_runtime_command"));
        assertTrue(normalized.contains("add index idx_ifj_runtime_command_expiration"));
        assertTrue(normalized.contains("(status, expires_at, request_id)"));
    }

    @Test
    void clusterSharedStateMigrationShouldBeAdditiveAndExpiryIndexed() throws IOException {
        String normalized = normalizeSql(readResource(CLUSTER_SHARED_STATE_MIGRATION));

        assertTrue(normalized.contains("create table ifj_cluster_state"));
        assertTrue(normalized.contains("runtime_mode in ('development', 'standalone', 'cluster')"));
        assertTrue(normalized.contains("primary key (state_key)"));
        assertTrue(normalized.contains("state_value longtext not null"));
        assertTrue(normalized.contains("idx_ifj_cluster_state_expiry (expires_at, state_key)"));
        assertFalse(normalized.contains("auto_increment"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("truncate table"));
        assertFalse(normalized.contains("delete from"));
    }

    @Test
    void maintenanceRecoveryMigrationShouldFreezeLeaseAndDeadlineInvariants() throws IOException {
        String normalized = normalizeSql(readResource(MAINTENANCE_RECOVERY_MIGRATION));

        assertTrue(normalized.contains("add column timeout_at datetime(3) null"));
        assertTrue(normalized.contains("idx_ifj_instance_root_timeout"));
        assertTrue(normalized.contains("create table ifj_maintenance_lease"));
        assertTrue(normalized.contains("unique (lease_token)"));
        assertTrue(normalized.contains("fence_token bigint not null default 0"));
        assertTrue(normalized.contains("chk_ifj_maintenance_lease_holder"));
        assertTrue(normalized.contains("current_timestamp(3)"));
        assertFalse(normalized.contains("auto_increment"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("truncate table"));
    }

    @Test
    void entryNotificationCacheMigrationShouldFreezeCoordinationInvariants() throws IOException {
        String normalized = normalizeSql(readResource(ENTRY_NOTIFICATION_CACHE_MIGRATION));

        assertTrue(normalized.contains("add column fire_claim_token varchar(36) null"));
        assertTrue(normalized.contains("unique (fire_claim_token)"));
        assertTrue(normalized.contains("create table ifj_notification_outbox"));
        assertTrue(normalized.contains("unique (deduplication_key)"));
        assertTrue(normalized.contains("unique (claim_token)"));
        assertTrue(normalized.contains("config_snapshot longtext null"));
        assertTrue(normalized.contains("chk_ifj_notification_outbox_config_snapshot"));
        assertTrue(
                normalized.contains(
                        "status in ('ready', 'claimed', 'retry_wait', 'delivered', 'dead')"));
        assertTrue(normalized.contains("lease_deadline datetime(3) null"));
        assertTrue(normalized.contains("idx_ifj_notification_outbox_lease"));
        assertFalse(normalized.contains("auto_increment"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("truncate table"));
    }

    @Test
    void standaloneRecoveryMigrationShouldBeAdditiveAndIndexed() throws IOException {
        String normalized = normalizeSql(readResource(STANDALONE_RECOVERY_MIGRATION));

        assertTrue(normalized.contains("create table ifj_app_run"));
        assertTrue(
                normalized.contains(
                        "check (status in ( 'starting', 'running', 'draining', "
                                + "'stopped', 'crashed'))"));
        assertTrue(normalized.contains("idx_ifj_trigger_status_update (status, update_time, id)"));
        assertTrue(normalized.contains("idx_ifj_instance_status_update (status, update_time, id)"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("delete from"));
    }

    @Test
    void coordinationFoundationMigrationShouldBeAdditiveAndPortableAtItsBoundary()
            throws IOException {
        String normalized = normalizeSql(readResource(COORDINATION_FOUNDATION_MIGRATION));

        assertTrue(normalized.contains("create table ifj_id_segment"));
        assertTrue(normalized.contains("primary key (segment_key)"));
        assertTrue(normalized.contains("check (next_value > 0)"));
        assertTrue(normalized.contains("check (segment_size between 1 and 100000)"));
        assertTrue(normalized.contains("add column instance_name varchar(128) null"));
        assertTrue(
                normalized.contains(
                        "add column runtime_mode varchar(16) not null default 'standalone'"));
        assertTrue(
                normalized.contains(
                        "add column roles varchar(64) not null default "
                                + "'api,scheduler,worker'"));
        assertTrue(
                normalized.contains(
                        "idx_ifj_app_run_instance_heartbeat "
                                + "(instance_name, status, heartbeat_at, run_id)"));
        assertFalse(normalized.contains("auto_increment"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("delete from"));
        assertFalse(normalized.contains("insert into"));
        assertFalse(normalized.contains("json"));
    }

    @Test
    void persistentWorkQueueMigrationShouldFreezeIdempotencyAndLeaseInvariants()
            throws IOException {
        String normalized = normalizeSql(readResource(PERSISTENT_WORK_QUEUE_MIGRATION));

        assertTrue(normalized.contains("add column idempotency_key varchar(191) null"));
        assertTrue(
                normalized.contains(
                        "add constraint uk_ifj_trigger_idempotency_key unique (idempotency_key)"));
        assertTrue(normalized.contains("create table ifj_trigger_work_item"));
        assertTrue(normalized.contains("primary key (trigger_id)"));
        assertTrue(normalized.contains("unique (claim_token)"));
        assertTrue(
                normalized.contains(
                        "status in ('ready', 'claimed', 'retry_wait', 'completed', 'dead')"));
        assertTrue(normalized.contains("foreign key (trigger_id) references ifj_trigger (id)"));
        assertTrue(normalized.contains("utc_timestamp(3)"));
        assertFalse(normalized.contains("auto_increment"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("json"));
    }

    @Test
    void rootExecutionFencingMigrationShouldFreezeOwnershipInvariants() throws IOException {
        String normalized = normalizeSql(readResource(ROOT_EXECUTION_FENCING_MIGRATION));

        assertTrue(normalized.contains("add column root_instance_id bigint null"));
        assertTrue(normalized.contains("add column fence_token bigint not null default 0"));
        assertTrue(normalized.contains("add column execution_fence bigint not null default 0"));
        assertTrue(normalized.contains("'suspended'"));
        assertTrue(normalized.contains("unique (root_instance_id)"));
        assertTrue(
                normalized.contains("foreign key (root_instance_id) references ifj_instance (id)"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("truncate table"));
    }

    @Test
    void runtimeCommandPermitMigrationShouldFreezeCoordinationInvariants() throws IOException {
        String normalized = normalizeSql(readResource(RUNTIME_COMMAND_PERMIT_MIGRATION));

        assertTrue(normalized.contains("create table ifj_runtime_command"));
        assertTrue(normalized.contains("primary key (request_id)"));
        assertTrue(normalized.contains("unique (processing_token)"));
        assertTrue(normalized.contains("command_type in ('stop', 'pause', 'resume', 'retry')"));
        assertTrue(normalized.contains("create table ifj_concurrency_permit"));
        assertTrue(normalized.contains("primary key (scope_key, slot_no)"));
        assertTrue(normalized.contains("unique (permit_token)"));
        assertTrue(normalized.contains("unique (owner_instance_id)"));
        assertTrue(normalized.contains("permit_fence bigint not null default 0"));
        assertTrue(normalized.contains("lease_deadline datetime(3) null"));
        assertFalse(normalized.contains("auto_increment"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("truncate table"));
    }

    @Test
    void runtimeErrorMetadataMigrationShouldBeAdditiveAndIndexed() throws IOException {
        String normalized = normalizeSql(readResource(RUNTIME_ERROR_METADATA_MIGRATION));

        assertEquals(2, countOccurrences(normalized, "add column error_code varchar(64) null"));
        assertEquals(2, countOccurrences(normalized, "add column message_key varchar(128) null"));
        assertEquals(2, countOccurrences(normalized, "add column message_args json null"));
        assertTrue(
                normalized.contains(
                        "idx_ifj_trigger_error_update " + "(error_code, update_time, id)"));
        assertTrue(
                normalized.contains(
                        "idx_ifj_instance_error_update " + "(error_code, update_time, id)"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("delete from"));
    }

    @Test
    void mysqlEtlCheckpointMigrationShouldBeSchemaOnlyAndVersioned() throws IOException {
        String normalized = normalizeSql(readResource(ETL_CHECKPOINT_MIGRATION));

        assertTrue(normalized.contains("create table ifj_etl_checkpoint"));
        assertTrue(normalized.contains("primary key (task_id, checkpoint_key)"));
        assertTrue(normalized.contains("version bigint not null default 0"));
        assertFalse(normalized.contains("drop table"));
        assertFalse(normalized.contains("delete from"));
        assertFalse(normalized.contains("truncate table"));
    }

    @Test
    void mysqlRuntimeStateMigrationShouldConstrainPersistedValues() throws IOException {
        String normalized = normalizeSql(readResource(RUNTIME_STATE_MIGRATION));

        assertTrue(
                normalized.contains(
                        "alter table ifj_trigger add constraint "
                                + "chk_ifj_trigger_status check"));
        assertTrue(
                normalized.contains(
                        "alter table ifj_instance add constraint "
                                + "chk_ifj_instance_status check"));
        assertTrue(normalized.contains("'timed_out'"));
        assertTrue(
                normalized.contains(
                        "chk_ifj_instance_progress check " + "(progress between 0 and 100)"));
        assertTrue(normalized.contains("chk_ifj_instance_retry_count check (retry_count >= 0)"));
        assertFalse(CREATE_TABLE.matcher(normalized).find());
    }

    @Test
    void mysqlJobDefinitionMigrationShouldCreateExactTablesAndColumns() throws IOException {
        String sql = readResource(JOB_DEFINITION_MIGRATION);
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = CREATE_TABLE.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1);
            assertTrue(VALID_TABLE_NAME.matcher(table).matches());
            tables.add(table);
        }

        assertEquals(JOB_DEFINITION_TABLES, tables);
        assertEquals(JOB_DEFINITION_COLUMNS, tableColumns(sql));
        assertEquals(
                4,
                countOccurrences(
                        normalizeSql(sql),
                        "engine = innodb default charset = utf8mb4 "
                                + "collate = utf8mb4_0900_ai_ci"));
        assertEquals(
                4,
                countOccurrences(
                        normalizeSql(sql),
                        "create_time datetime(3) not null default " + "current_timestamp(3)"));
    }

    @Test
    void mysqlJobDefinitionMigrationShouldFreezeConstraintsAndIndexes() throws IOException {
        String normalized = normalizeSql(readResource(JOB_DEFINITION_MIGRATION));

        for (String expected :
                List.of(
                        "constraint pk_ifj_job_definition primary key (id)",
                        "constraint uk_ifj_job_definition_job_revision unique "
                                + "(job_id, revision)",
                        "constraint ck_ifj_job_definition_revision check " + "(revision > 0)",
                        "constraint fk_ifj_job_definition_job foreign key "
                                + "(job_id) references ifj_job (id) "
                                + "on delete restrict",
                        "index idx_ifj_job_definition_job_time " + "(job_id, create_time, id)",
                        "constraint pk_ifj_job_definition_node primary key (id)",
                        "constraint uk_ifj_job_definition_node_key unique "
                                + "(definition_id, node_key)",
                        "constraint fk_ifj_job_definition_node_definition "
                                + "foreign key (definition_id) references "
                                + "ifj_job_definition (id) on delete restrict",
                        "index idx_ifj_job_definition_node_ref " + "(node_type, ref_id)",
                        "constraint pk_ifj_job_definition_node_policy " + "primary key (id)",
                        "constraint uk_ifj_job_definition_node_policy_node " + "unique (node_id)",
                        "constraint fk_ifj_job_definition_node_policy_definition "
                                + "foreign key (definition_id) references "
                                + "ifj_job_definition (id) on delete restrict",
                        "constraint fk_ifj_job_definition_node_policy_node "
                                + "foreign key (node_id) references "
                                + "ifj_job_definition_node (id) on delete restrict",
                        "index idx_ifj_job_definition_node_policy_definition " + "(definition_id)",
                        "constraint pk_ifj_job_definition_edge primary key (id)",
                        "constraint uk_ifj_job_definition_edge_path unique "
                                + "(definition_id, from_node_id, to_node_id)",
                        "constraint fk_ifj_job_definition_edge_definition "
                                + "foreign key (definition_id) references "
                                + "ifj_job_definition (id) on delete restrict",
                        "constraint fk_ifj_job_definition_edge_from_node "
                                + "foreign key (from_node_id) references "
                                + "ifj_job_definition_node (id) on delete restrict",
                        "constraint fk_ifj_job_definition_edge_to_node "
                                + "foreign key (to_node_id) references "
                                + "ifj_job_definition_node (id) on delete restrict",
                        "index idx_ifj_job_definition_edge_to_node (to_node_id)")) {
            assertTrue(
                    normalized.contains(expected),
                    () -> "Missing V3 constraint or index: " + expected);
        }
    }

    @Test
    void mysqlJobDefinitionMigrationShouldUseOnlyFrozenAdditiveAlters() throws IOException {
        String sql = readResource(JOB_DEFINITION_MIGRATION);
        String normalized = normalizeSql(sql);
        Set<String> alteredTables = new LinkedHashSet<>();
        Matcher matcher = ALTER_TABLE.matcher(sql);
        while (matcher.find()) {
            alteredTables.add(matcher.group(1));
        }

        assertEquals(JOB_DEFINITION_ALTER_TABLES, alteredTables);
        assertEquals(3, countOccurrences(normalized, "alter table "));
        assertEquals(13, countOccurrences(normalized, " add "));
        for (String expected :
                List.of(
                        "alter table ifj_job add column current_definition_id "
                                + "bigint null, add column current_revision bigint null",
                        "constraint ck_ifj_job_current_pointer_pair check "
                                + "((current_definition_id is null and "
                                + "current_revision is null) or "
                                + "(current_definition_id is not null and "
                                + "current_revision is not null))",
                        "index idx_ifj_job_current_definition_id " + "(current_definition_id)",
                        "alter table ifj_trigger add column definition_id " + "bigint null",
                        "constraint fk_ifj_trigger_definition foreign key "
                                + "(definition_id) references ifj_job_definition (id) "
                                + "on delete restrict",
                        "index idx_ifj_trigger_definition_status " + "(definition_id, status)",
                        "alter table ifj_instance add column definition_id "
                                + "bigint null, add column definition_node_id bigint null",
                        "constraint fk_ifj_instance_definition foreign key "
                                + "(definition_id) references ifj_job_definition (id) "
                                + "on delete restrict",
                        "constraint fk_ifj_instance_definition_node foreign key "
                                + "(definition_node_id) references "
                                + "ifj_job_definition_node (id) on delete restrict",
                        "constraint uk_ifj_instance_parent_definition_node unique "
                                + "(parent_instance_id, definition_node_id)",
                        "index idx_ifj_instance_definition_status " + "(definition_id, status)")) {
            assertTrue(
                    normalized.contains(expected), () -> "Missing V3 ALTER fragment: " + expected);
        }

        assertFalse(normalized.contains("foreign key (current_definition_id)"));
        assertFalse(normalized.contains("uk_ifj_instance_root_trigger"));
    }

    @Test
    void mysqlJobDefinitionMigrationShouldBeSchemaOnlyAndSafe() throws IOException {
        String sql = readResource(JOB_DEFINITION_MIGRATION);
        String normalized = normalizeSql(sql);

        assertEquals(10, countOccurrences(normalized, " foreign key "));
        assertEquals(10, countOccurrences(normalized, " on delete restrict"));
        assertFalse(normalized.contains(" on delete cascade"));
        assertFalse(normalized.contains(" on delete set null"));
        assertFalse(normalized.contains("auto_increment"));
        assertFalse(normalized.contains("drop "));
        assertFalse(normalized.contains("truncate "));
        assertFalse(normalized.contains("rename "));
        assertFalse(normalized.contains(" change "));
        assertFalse(normalized.contains(" modify "));
        assertFalse(normalized.contains("insert into"));
        assertFalse(normalized.contains("delete from"));
        assertFalse(Pattern.compile("(?m)^\\s*update\\s+").matcher(sql).find());
        assertFalse(normalized.contains("jdbc:"));
        assertFalse(normalized.contains("datasource"));
        assertFalse(normalized.contains("flyway_schema_history"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void mysqlTaskDefinitionMigrationShouldCreateExactTableAndColumns() throws IOException {
        String sql = readResource(TASK_DEFINITION_MIGRATION);
        List<String> tables = new ArrayList<>();
        Matcher matcher = CREATE_TABLE.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1);
            assertTrue(VALID_TABLE_NAME.matcher(table).matches());
            tables.add(table);
        }

        assertEquals(List.of("ifj_task_definition"), tables);
        assertEquals(
                TASK_DEFINITION_COLUMN_DEFINITIONS,
                tableColumnDefinitions(sql, "ifj_task_definition"));
        assertEquals(
                1,
                countOccurrences(
                        normalizeSql(sql),
                        "engine = innodb default charset = utf8mb4 "
                                + "collate = utf8mb4_0900_ai_ci"));
        assertEquals(
                1,
                countOccurrences(
                        normalizeSql(sql),
                        "create_time datetime(3) not null default " + "current_timestamp(3)"));
        assertFalse(normalizeSql(sql).contains("update_time"));
    }

    @Test
    void mysqlTaskDefinitionMigrationShouldFreezeConstraintsAndIndexes() throws IOException {
        String normalized = normalizeSql(readResource(TASK_DEFINITION_MIGRATION));

        for (String expected :
                List.of(
                        "constraint pk_ifj_task_definition primary key (id)",
                        "constraint uk_ifj_task_definition_task_revision unique "
                                + "(task_id, revision)",
                        "constraint ck_ifj_task_definition_revision check " + "(revision > 0)",
                        "constraint fk_ifj_task_definition_task foreign key "
                                + "(task_id) references ifj_task (id) "
                                + "on delete restrict",
                        "index idx_ifj_task_definition_task_time " + "(task_id, create_time, id)",
                        "constraint ck_ifj_job_retired_state check "
                                + "((retired = 0 and retired_time is null) or "
                                + "(retired = 1 and retired_time is not null))",
                        "constraint ck_ifj_task_current_pointer_pair check "
                                + "((current_task_definition_id is null and "
                                + "current_revision is null) or "
                                + "(current_task_definition_id is not null and "
                                + "current_revision is not null))",
                        "constraint ck_ifj_task_current_revision_positive check "
                                + "(current_revision is null or current_revision > 0)",
                        "constraint ck_ifj_task_retired_state check "
                                + "((retired = 0 and retired_time is null) or "
                                + "(retired = 1 and retired_time is not null))",
                        "index idx_ifj_task_current_definition_id "
                                + "(current_task_definition_id)",
                        "constraint fk_ifj_trigger_task_definition foreign key "
                                + "(task_definition_id) references "
                                + "ifj_task_definition (id) on delete restrict",
                        "index idx_ifj_trigger_task_definition_status "
                                + "(task_definition_id, status)",
                        "constraint fk_ifj_instance_task_definition foreign key "
                                + "(task_definition_id) references "
                                + "ifj_task_definition (id) on delete restrict",
                        "index idx_ifj_instance_task_definition_status "
                                + "(task_definition_id, status)")) {
            assertTrue(
                    normalized.contains(expected),
                    () -> "Missing V4 constraint or index: " + expected);
        }

        assertEquals(5, countOccurrences(normalized, " check "));
        assertEquals(3, countOccurrences(normalized, " foreign key "));
        assertEquals(3, countOccurrences(normalized, " on delete restrict"));
        assertEquals(4, countOccurrences(normalized, " index "));
        assertFalse(normalized.contains("foreign key (current_task_definition_id)"));
    }

    @Test
    void mysqlTaskDefinitionMigrationShouldUseOnlyFrozenAdditiveAlters() throws IOException {
        String sql = readResource(TASK_DEFINITION_MIGRATION);
        String normalized = normalizeSql(sql);
        List<String> alteredTables = new ArrayList<>();
        Matcher tableMatcher = ALTER_TABLE.matcher(sql);
        while (tableMatcher.find()) {
            alteredTables.add(tableMatcher.group(1));
        }

        assertEquals(TASK_DEFINITION_ALTER_TABLES, alteredTables);
        assertEquals(4, countOccurrences(normalized, "alter table "));
        assertEquals(17, countOccurrences(normalized, " add "));

        int addClauseCount = 0;
        Matcher statementMatcher = ALTER_STATEMENT.matcher(sql);
        while (statementMatcher.find()) {
            for (String clause : splitTopLevelClauses(statementMatcher.group(2))) {
                String normalizedClause = normalizeSql(clause).trim();
                assertTrue(
                        normalizedClause.startsWith("add column ")
                                || normalizedClause.startsWith("add constraint ")
                                || normalizedClause.startsWith("add index "),
                        () -> "Non-ADD V4 ALTER clause: " + normalizedClause);
                addClauseCount++;
            }
        }
        assertEquals(17, addClauseCount);
    }

    @Test
    void mysqlTaskDefinitionMigrationShouldBeSchemaOnlyAndSafe() throws IOException {
        String sql = readResource(TASK_DEFINITION_MIGRATION);
        String normalized = normalizeSql(sql);

        assertFalse(normalized.contains("auto_increment"));
        assertFalse(normalized.contains(" drop "));
        assertFalse(normalized.contains("rename "));
        assertFalse(normalized.contains(" change "));
        assertFalse(normalized.contains(" modify "));
        assertFalse(normalized.contains(" first"));
        assertFalse(normalized.contains(" after "));
        assertFalse(normalized.contains(" on delete cascade"));
        assertFalse(normalized.contains(" on delete set null"));
        assertFalse(
                Pattern.compile(
                                "(?im)^\\s*(?:insert|update|delete|merge|replace|"
                                        + "call|truncate)\\b")
                        .matcher(sql)
                        .find());
        assertFalse(normalized.contains("jdbc:"));
        assertFalse(normalized.contains("spring.datasource"));
        assertFalse(normalized.contains("mysql://"));
        assertFalse(normalized.contains("localhost"));
        assertFalse(normalized.contains("127.0.0.1"));
        assertFalse(normalized.contains("flyway_schema_history"));
        assertFalse(normalized.contains("ifj_schema_history"));
        assertFalse(sql.contains("${"));
        assertFalse(sql.contains("#{"));
    }

    private String readBaselineMigration() throws IOException {
        return readResource(BASELINE_MIGRATION);
    }

    private String readResource(String resourceName) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourceName);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Set<String> entityColumns(Class<?> entityType) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> type = entityType;
                type != null && type != Object.class;
                type = type.getSuperclass()) {
            hierarchy.addFirst(type);
        }

        Set<String> columns = new LinkedHashSet<>();
        for (Class<?> type : hierarchy) {
            for (Field field : type.getDeclaredFields()) {
                // ScheduleMapper projects this label from the task/job join; it is not persisted.
                if (type == Schedule.class && field.getName().equals("targetName")) continue;
                if (!field.isSynthetic()
                        && !java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
                    columns.add(toSnakeCase(field.getName()));
                }
            }
        }
        return columns;
    }

    private Map<String, Set<String>> tableColumns(String sql) {
        Matcher tableMatcher = TABLE_BODY.matcher(sql);
        Map<String, Set<String>> result = new java.util.HashMap<>();
        while (tableMatcher.find()) {
            Matcher columnMatcher = COLUMN.matcher(tableMatcher.group(2));
            Set<String> columns = new LinkedHashSet<>();
            while (columnMatcher.find()) {
                columns.add(columnMatcher.group(1));
            }
            result.put(tableMatcher.group(1), columns);
        }
        return result;
    }

    private Set<String> alterColumns(String sql, String tableName) {
        Set<String> columns = new LinkedHashSet<>();
        Matcher statementMatcher = ALTER_STATEMENT.matcher(sql);
        while (statementMatcher.find()) {
            if (!tableName.equals(statementMatcher.group(1))) {
                continue;
            }
            Matcher columnMatcher = ALTER_COLUMN.matcher(statementMatcher.group(2));
            while (columnMatcher.find()) {
                columns.add(columnMatcher.group(1));
            }
        }
        return columns;
    }

    private List<String> tableColumnDefinitions(String sql, String tableName) {
        Matcher tableMatcher = TABLE_BODY.matcher(sql);
        while (tableMatcher.find()) {
            if (!tableName.equals(tableMatcher.group(1))) {
                continue;
            }
            List<String> definitions = new ArrayList<>();
            Matcher columnMatcher = COLUMN_DEFINITION.matcher(tableMatcher.group(2));
            while (columnMatcher.find()) {
                definitions.add(normalizeSql(columnMatcher.group(1)));
            }
            return definitions;
        }
        throw new IllegalArgumentException("Missing table: " + tableName);
    }

    private List<String> splitTopLevelClauses(String value) {
        List<String> clauses = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (current == ',' && depth == 0) {
                clauses.add(value.substring(start, index));
                start = index + 1;
            }
        }
        if (depth != 0) {
            throw new IllegalArgumentException("Unbalanced V4 ALTER clause");
        }
        clauses.add(value.substring(start));
        return clauses;
    }

    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private int countOccurrences(String value, String fragment) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
    }

    private String sha256(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
