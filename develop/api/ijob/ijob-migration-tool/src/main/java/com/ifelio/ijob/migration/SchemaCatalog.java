package com.ifelio.ijob.migration;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchemaCatalog {

    static final int LOGICAL_SCHEMA_VERSION = 27;
    static final String FORMAT_VERSION = "ijob-offline-v1";

    static final List<String> TABLES =
            List.of(
                    "ifj_app_run",
                    "ifj_auth_user",
                    "ifj_calendar",
                    "ifj_calendar_exception",
                    "ifj_cluster_state",
                    "ifj_concurrency_permit",
                    "ifj_concurrency_resource",
                    "ifj_dag_gateway_decision",
                    "ifj_etl_checkpoint",
                    "ifj_event",
                    "ifj_event_binding",
                    "ifj_event_wait",
                    "ifj_event_wait_operator",
                    "ifj_id_segment",
                    "ifj_instance",
                    "ifj_instance_log",
                    "ifj_job",
                    "ifj_job_definition",
                    "ifj_job_definition_edge",
                    "ifj_job_definition_group",
                    "ifj_job_definition_node",
                    "ifj_job_definition_node_policy",
                    "ifj_job_edge",
                    "ifj_job_node",
                    "ifj_job_node_policy",
                    "ifj_maintenance_lease",
                    "ifj_notification_outbox",
                    "ifj_notify_binding",
                    "ifj_notify_config",
                    "ifj_notify_send_log",
                    "ifj_operation_audit",
                    "ifj_plugin_concurrency_policy",
                    "ifj_resource",
                    "ifj_runtime_command",
                    "ifj_runtime_guard",
                    "ifj_schedule",
                    "ifj_schedule_misfire_audit",
                    "ifj_schema_capability",
                    "ifj_task",
                    "ifj_task_concurrency_policy",
                    "ifj_task_concurrency_resource",
                    "ifj_task_definition",
                    "ifj_task_definition_group",
                    "ifj_trigger",
                    "ifj_trigger_work_item");

    static final Set<String> SEEDED_TABLES =
            Set.of("ifj_id_segment", "ifj_schema_capability", "ifj_runtime_guard");

    static final Set<String> TIMESTAMP_COLUMNS =
            Set.of(
                    "accept_after",
                    "arrived_at",
                    "available_at",
                    "create_time",
                    "delivered_at",
                    "end_time",
                    "expires_at",
                    "fire_claimed_at",
                    "first_due_time",
                    "heartbeat_at",
                    "last_acquired_at",
                    "last_completed_at",
                    "last_fire_time",
                    "last_due_time",
                    "lease_deadline",
                    "log_time",
                    "next_fire_time",
                    "occurred_at",
                    "observed_at",
                    "processed_at",
                    "permit_wait_deadline",
                    "resolved_at",
                    "retired_time",
                    "start_time",
                    "started_at",
                    "stopped_at",
                    "timeout_at",
                    "trigger_time",
                    "update_time");

    static final Map<String, Set<String>> BOOLEAN_COLUMNS =
            Map.ofEntries(
                    Map.entry("ifj_auth_user", Set.of("enabled", "must_change_password")),
                    Map.entry("ifj_concurrency_resource", Set.of("enabled")),
                    Map.entry("ifj_event_binding", Set.of("enabled")),
                    Map.entry("ifj_job", Set.of("enabled", "retired")),
                    Map.entry("ifj_job_definition", Set.of("enabled")),
                    Map.entry("ifj_job_definition_edge", Set.of("default_route")),
                    Map.entry("ifj_maintenance_lease", Set.of("last_success")),
                    Map.entry("ifj_notification_outbox", Set.of("config_enabled")),
                    Map.entry("ifj_notify_config", Set.of("enabled")),
                    Map.entry("ifj_resource", Set.of("enabled")),
                    Map.entry("ifj_schedule", Set.of("enabled")),
                    Map.entry("ifj_schedule_misfire_audit", Set.of("count_capped")),
                    Map.entry("ifj_schema_capability", Set.of("enabled")),
                    Map.entry("ifj_task", Set.of("enabled", "retired")),
                    Map.entry("ifj_task_definition", Set.of("enabled")));

    private SchemaCatalog() {}

    static boolean isBoolean(String table, String column) {
        return BOOLEAN_COLUMNS.getOrDefault(table, Set.of()).contains(column);
    }

    static boolean isTimestamp(String column) {
        return TIMESTAMP_COLUMNS.contains(column);
    }

    static boolean isDate(String table, String column) {
        return "ifj_calendar_exception".equals(table) && "date".equals(column);
    }

    static String logicalColumn(String product, String table, String physicalColumn) {
        if (!isOracle(product)) {
            return physicalColumn;
        }
        if ("ifj_calendar_exception".equals(table) && "calendar_date".equals(physicalColumn)) {
            return "date";
        }
        if ("ifj_instance_log".equals(table) && "log_level".equals(physicalColumn)) {
            return "level";
        }
        return physicalColumn;
    }

    static String physicalColumn(String product, String table, String logicalColumn) {
        if (!isOracle(product)) {
            return logicalColumn;
        }
        if ("ifj_calendar_exception".equals(table) && "date".equals(logicalColumn)) {
            return "calendar_date";
        }
        if ("ifj_instance_log".equals(table) && "level".equals(logicalColumn)) {
            return "log_level";
        }
        return logicalColumn;
    }

    private static boolean isOracle(String product) {
        return product != null && product.toLowerCase(java.util.Locale.ROOT).contains("oracle");
    }
}
