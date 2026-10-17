package com.ifelio.ijob.infra.coordination.id;

/** Stable database identities. Keys must not change when Java classes are renamed. */
public enum IdNamespace {
    PARAMETER_TEMPLATE("ifj_parameter_template", 1),
    CALENDAR("ifj_calendar", 1),
    CALENDAR_EXCEPTION("ifj_calendar_exception", 1),
    RESOURCE("ifj_resource", 1),
    TASK("ifj_task", 1),
    JOB("ifj_job", 1),
    SCHEDULE("ifj_schedule", 1),
    EVENT_BINDING("ifj_event_binding", 1),
    NOTIFY_CONFIG("ifj_notify_config", 1),
    NOTIFY_BINDING("ifj_notify_binding", 1),
    MAIL_CHANNEL("ifj_mail_channel", 1),
    CONCURRENCY_RESOURCE("ifj_concurrency_resource", 1),
    AUTH_USER("ifj_auth_user", 1),
    TASK_DEFINITION("ifj_task_definition", 1),
    JOB_DEFINITION("ifj_job_definition", 1),
    JOB_DEFINITION_NODE("ifj_job_definition_node", 100),
    JOB_DEFINITION_EDGE("ifj_job_definition_edge", 100),
    JOB_DEFINITION_NODE_POLICY("ifj_job_definition_node_policy", 100),
    TRIGGER("ifj_trigger", 1000),
    INSTANCE("ifj_instance", 1000),
    INSTANCE_LOG("ifj_instance_log", 1000),
    EVENT("ifj_event", 1000),
    EVENT_WAIT("ifj_event_wait", 1000),
    DAG_GATEWAY_DECISION("ifj_dag_gateway_decision", 1000),
    NOTIFICATION_OUTBOX("ifj_notification_outbox", 1000),
    NOTIFY_SEND_LOG("ifj_notify_send_log", 1000),
    SCHEDULE_MISFIRE_AUDIT("ifj_schedule_misfire_audit", 1000),
    OPERATION_AUDIT("ifj_operation_audit", 1000);

    private final String tableName;
    private final int defaultSegmentSize;

    IdNamespace(String tableName, int defaultSegmentSize) {
        this.tableName = tableName;
        this.defaultSegmentSize = defaultSegmentSize;
    }

    public String tableName() {
        return tableName;
    }

    public String segmentKey() {
        return "pk:" + tableName;
    }

    public int defaultSegmentSize() {
        return defaultSegmentSize;
    }
}
