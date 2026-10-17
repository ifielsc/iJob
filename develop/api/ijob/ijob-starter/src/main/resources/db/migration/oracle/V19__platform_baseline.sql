-- Oracle 11g R2+ platform baseline aligned with the V19 logical schema.
-- IDs are supplied by the application-level database segment allocator; no sequences or identity columns are used.
CREATE TABLE ifj_app_run (
    run_id VARCHAR2(64 CHAR) NOT NULL,
    instance_name VARCHAR2(128 CHAR) DEFAULT NULL,
    runtime_mode VARCHAR2(16 CHAR) DEFAULT 'STANDALONE' NOT NULL,
    roles VARCHAR2(64 CHAR) DEFAULT 'API,SCHEDULER,WORKER' NOT NULL,
    status VARCHAR2(16 CHAR) NOT NULL,
    application_version VARCHAR2(64 CHAR) DEFAULT NULL,
    host_name VARCHAR2(255 CHAR) DEFAULT NULL,
    process_id NUMBER(19) DEFAULT NULL,
    started_at TIMESTAMP(3) NOT NULL,
    heartbeat_at TIMESTAMP(3) NOT NULL,
    stopped_at TIMESTAMP(3) DEFAULT NULL,
    CONSTRAINT pk_ifj_app_run PRIMARY KEY (run_id),
    CONSTRAINT chk_ifj_app_run_runtime_mode CHECK ((runtime_mode in ('DEVELOPMENT','STANDALONE','CLUSTER'))),
    CONSTRAINT chk_ifj_app_run_status CHECK ((status in ('STARTING','RUNNING','DRAINING','STOPPED','CRASHED')))
);

CREATE TABLE ifj_calendar (
    id NUMBER(19) NOT NULL,
    name VARCHAR2(128 CHAR) NOT NULL,
    workdays_mask NUMBER(10) NOT NULL,
    description VARCHAR2(500 CHAR) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_calendar PRIMARY KEY (id),
    CONSTRAINT uk_ifj_calendar_name UNIQUE (name)
);

CREATE TABLE ifj_calendar_exception (
    id NUMBER(19) NOT NULL,
    calendar_id NUMBER(19) NOT NULL,
    calendar_date DATE NOT NULL,
    type VARCHAR2(32 CHAR) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_calendar_exception PRIMARY KEY (id),
    CONSTRAINT uk_ifj_calendar_exception_date UNIQUE (calendar_id,calendar_date)
);

CREATE TABLE ifj_cluster_state (
    state_key VARCHAR2(512 CHAR) NOT NULL,
    state_value CLOB,
    expires_at TIMESTAMP(3) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_cluster_state PRIMARY KEY (state_key)
);

CREATE TABLE ifj_concurrency_permit (
    scope_key VARCHAR2(191 CHAR) NOT NULL,
    slot_no NUMBER(10) NOT NULL,
    owner_instance_id NUMBER(19) DEFAULT NULL,
    owner_root_instance_id NUMBER(19) DEFAULT NULL,
    owner_trigger_id NUMBER(19) DEFAULT NULL,
    lease_owner VARCHAR2(128 CHAR) DEFAULT NULL,
    permit_token VARCHAR2(36 CHAR) DEFAULT NULL,
    permit_fence NUMBER(19) DEFAULT 0 NOT NULL,
    execution_fence NUMBER(19) DEFAULT NULL,
    lease_deadline TIMESTAMP(3) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_concurrency_permit PRIMARY KEY (scope_key,slot_no),
    CONSTRAINT uk_ifj_concurrency_pe_bfef379b UNIQUE (permit_token),
    CONSTRAINT uk_ifj_concurrency_pe_9dbccce8 UNIQUE (owner_instance_id),
    CONSTRAINT chk_ifj_concurrency_p_31463b57 CHECK ((permit_fence >= 0)),
    CONSTRAINT chk_ifj_concurrency_p_88b03814 CHECK ((((owner_instance_id is null) and (owner_root_instance_id is null) and (owner_trigger_id is null) and (lease_owner is null) and (permit_token is null) and (execution_fence is null) and (lease_deadline is null)) or ((owner_instance_id is not null) and (owner_root_instance_id is not null) and (owner_trigger_id is not null) and (lease_owner is not null) and (permit_token is not null) and (execution_fence is not null) and (lease_deadline is not null)))),
    CONSTRAINT chk_ifj_concurrency_p_8a32923d CHECK ((slot_no > 0))
);

CREATE TABLE ifj_concurrency_policy (
    id NUMBER(19) NOT NULL,
    target_id NUMBER(19) NOT NULL,
    target_type VARCHAR2(32 CHAR) NOT NULL,
    concurrency_type VARCHAR2(32 CHAR) NOT NULL,
    lock_key VARCHAR2(255 CHAR) DEFAULT NULL,
    max_concurrency NUMBER(10) DEFAULT NULL,
    enabled NUMBER(1) DEFAULT 1 NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_concurrency_policy PRIMARY KEY (id),
    CONSTRAINT uk_ifj_concurrency_po_9453680a UNIQUE (target_type,target_id)
);

CREATE TABLE ifj_etl_checkpoint (
    task_id NUMBER(19) NOT NULL,
    checkpoint_key VARCHAR2(128 CHAR) NOT NULL,
    source_fingerprint CHAR(64 CHAR) NOT NULL,
    watermark_type VARCHAR2(32 CHAR) NOT NULL,
    watermark_value CLOB,
    tie_breaker_value CLOB,
    last_instance_id NUMBER(19) DEFAULT NULL,
    version NUMBER(19) DEFAULT 0 NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_etl_checkpoint PRIMARY KEY (task_id,checkpoint_key),
    CONSTRAINT ck_ifj_etl_checkpoint_task CHECK ((task_id > 0)),
    CONSTRAINT ck_ifj_etl_checkpoint_version CHECK ((version >= 0))
);

CREATE TABLE ifj_event (
    id NUMBER(19) NOT NULL,
    event_code VARCHAR2(128 CHAR) NOT NULL,
    source_type VARCHAR2(32 CHAR) NOT NULL,
    source_key VARCHAR2(128 CHAR) NOT NULL,
    business_key VARCHAR2(256 CHAR) DEFAULT NULL,
    correlation_key VARCHAR2(256 CHAR) DEFAULT NULL,
    idempotency_key VARCHAR2(256 CHAR) NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    arrived_at TIMESTAMP(3) NOT NULL,
    summary VARCHAR2(500 CHAR) DEFAULT NULL,
    payload CLOB,
    payload_digest CHAR(64 CHAR) DEFAULT NULL,
    payload_bytes NUMBER(10) DEFAULT 0 NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_event PRIMARY KEY (id),
    CONSTRAINT uk_ifj_event_source_i_da1369bb UNIQUE (source_key,idempotency_key),
    CONSTRAINT chk_ifj_event_payload_bytes CHECK ((payload_bytes >= 0))
);

CREATE TABLE ifj_event_binding (
    id NUMBER(19) NOT NULL,
    target_type VARCHAR2(32 CHAR) NOT NULL,
    target_id NUMBER(19) NOT NULL,
    event_code VARCHAR2(128 CHAR) NOT NULL,
    enabled NUMBER(1) DEFAULT 1 NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_event_binding PRIMARY KEY (id),
    CONSTRAINT uk_ifj_event_binding_target UNIQUE (target_type,target_id,event_code)
);

CREATE TABLE ifj_event_wait (
    id NUMBER(19) NOT NULL,
    instance_id NUMBER(19) NOT NULL,
    root_instance_id NUMBER(19) NOT NULL,
    attempt_no NUMBER(10) NOT NULL,
    wait_type VARCHAR2(32 CHAR) NOT NULL,
    status VARCHAR2(32 CHAR) NOT NULL,
    event_code VARCHAR2(128 CHAR) DEFAULT NULL,
    business_key VARCHAR2(256 CHAR) DEFAULT NULL,
    correlation_key VARCHAR2(256 CHAR) DEFAULT NULL,
    accept_after TIMESTAMP(3) NOT NULL,
    timeout_at TIMESTAMP(3) DEFAULT NULL,
    matched_event_id NUMBER(19) DEFAULT NULL,
    allowed_operator_ids CLOB,
    decision_by VARCHAR2(128 CHAR) DEFAULT NULL,
    decision_comment VARCHAR2(500 CHAR) DEFAULT NULL,
    resolved_at TIMESTAMP(3) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_event_wait PRIMARY KEY (id),
    CONSTRAINT uk_ifj_event_wait_attempt UNIQUE (instance_id,attempt_no),
    CONSTRAINT chk_ifj_event_wait_attempt CHECK ((attempt_no >= 0)),
    CONSTRAINT chk_ifj_event_wait_status CHECK ((status in ('WAITING','MATCHED','APPROVED','REJECTED','TIMED_OUT','WITHDRAWN','STOPPED'))),
    CONSTRAINT chk_ifj_event_wait_type CHECK ((wait_type in ('EVENT','APPROVAL')))
);

CREATE TABLE ifj_event_wait_operator (
    wait_id NUMBER(19) NOT NULL,
    operator_id VARCHAR2(128 CHAR) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_event_wait_operator PRIMARY KEY (wait_id,operator_id),
    CONSTRAINT chk_ifj_wait_operator_id CHECK ((LENGTH(operator_id) > 0))
);

CREATE TABLE ifj_id_segment (
    segment_key VARCHAR2(64 CHAR) NOT NULL,
    next_value NUMBER(19) NOT NULL,
    segment_size NUMBER(10) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_id_segment PRIMARY KEY (segment_key),
    CONSTRAINT chk_ifj_id_segment_next_value CHECK ((next_value > 0)),
    CONSTRAINT chk_ifj_id_segment_size CHECK ((segment_size between 1 and 100000))
);

CREATE TABLE ifj_instance (
    id NUMBER(19) NOT NULL,
    node_id NUMBER(19) DEFAULT NULL,
    parent_instance_id NUMBER(19) DEFAULT NULL,
    root_instance_id NUMBER(19) DEFAULT NULL,
    execution_fence NUMBER(19) DEFAULT 0 NOT NULL,
    target_type VARCHAR2(32 CHAR) NOT NULL,
    target_id NUMBER(19) NOT NULL,
    trigger_type VARCHAR2(32 CHAR) DEFAULT NULL,
    trigger_id NUMBER(19) DEFAULT NULL,
    status VARCHAR2(32 CHAR) NOT NULL,
    progress NUMBER(10) DEFAULT 0 NOT NULL,
    retry_count NUMBER(10) DEFAULT 0 NOT NULL,
    start_time TIMESTAMP(3) DEFAULT NULL,
    timeout_at TIMESTAMP(3) DEFAULT NULL,
    end_time TIMESTAMP(3) DEFAULT NULL,
    message CLOB,
    error_code VARCHAR2(64 CHAR) DEFAULT NULL,
    message_key VARCHAR2(128 CHAR) DEFAULT NULL,
    message_args CLOB DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    definition_id NUMBER(19) DEFAULT NULL,
    definition_node_id NUMBER(19) DEFAULT NULL,
    task_definition_id NUMBER(19) DEFAULT NULL,
    input_variables CLOB,
    output_variables CLOB,
    CONSTRAINT pk_ifj_instance PRIMARY KEY (id),
    CONSTRAINT uk_ifj_instance_parent_node UNIQUE (parent_instance_id,node_id),
    CONSTRAINT uk_ifj_instance_paren_9c382e7e UNIQUE (parent_instance_id,definition_node_id),
    CONSTRAINT chk_ifj_instance_exec_dceae6b3 CHECK ((execution_fence >= 0)),
    CONSTRAINT chk_ifj_instance_progress CHECK ((progress between 0 and 100)),
    CONSTRAINT chk_ifj_instance_retry_count CHECK ((retry_count >= 0)),
    CONSTRAINT chk_ifj_instance_status CHECK ((status in ('WAITING','RUNNING','PAUSED','WAITING_EVENT','SUCCESS','FAILED','STOPPED','TIMED_OUT','SKIPPED')))
);

CREATE TABLE ifj_instance_log (
    id NUMBER(19) NOT NULL,
    instance_id NUMBER(19) NOT NULL,
    log_time TIMESTAMP(3) NOT NULL,
    log_level VARCHAR2(16 CHAR) NOT NULL,
    content CLOB NOT NULL,
    CONSTRAINT pk_ifj_instance_log PRIMARY KEY (id)
);

CREATE TABLE ifj_job (
    id NUMBER(19) NOT NULL,
    name VARCHAR2(128 CHAR) NOT NULL,
    timeout NUMBER(10) DEFAULT NULL,
    enabled NUMBER(1) DEFAULT 1 NOT NULL,
    description VARCHAR2(500 CHAR) DEFAULT NULL,
    concurrency_type VARCHAR2(32 CHAR) DEFAULT NULL,
    lock_key VARCHAR2(255 CHAR) DEFAULT NULL,
    max_concurrency NUMBER(10) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    current_definition_id NUMBER(19) DEFAULT NULL,
    current_revision NUMBER(19) DEFAULT NULL,
    retired NUMBER(1) DEFAULT 0 NOT NULL,
    retired_time TIMESTAMP(3) DEFAULT NULL,
    CONSTRAINT pk_ifj_job PRIMARY KEY (id),
    CONSTRAINT ck_ifj_job_current_po_57ac4b56 CHECK ((((current_definition_id is null) and (current_revision is null)) or ((current_definition_id is not null) and (current_revision is not null)))),
    CONSTRAINT ck_ifj_job_retired_state CHECK ((((retired = 0) and (retired_time is null)) or ((retired = 1) and (retired_time is not null))))
);

CREATE TABLE ifj_job_definition (
    id NUMBER(19) NOT NULL,
    job_id NUMBER(19) NOT NULL,
    revision NUMBER(19) NOT NULL,
    name VARCHAR2(128 CHAR) NOT NULL,
    timeout NUMBER(10) DEFAULT NULL,
    enabled NUMBER(1) NOT NULL,
    description VARCHAR2(500 CHAR) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_job_definition PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_9dea2afd UNIQUE (job_id,revision),
    CONSTRAINT ck_ifj_job_definition_revision CHECK ((revision > 0))
);

CREATE TABLE ifj_job_definition_edge (
    id NUMBER(19) NOT NULL,
    definition_id NUMBER(19) NOT NULL,
    from_node_id NUMBER(19) NOT NULL,
    to_node_id NUMBER(19) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_job_definition_edge PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_95c6fe98 UNIQUE (definition_id,from_node_id,to_node_id)
);

CREATE TABLE ifj_job_definition_node (
    id NUMBER(19) NOT NULL,
    definition_id NUMBER(19) NOT NULL,
    node_key VARCHAR2(64 CHAR) NOT NULL,
    name VARCHAR2(128 CHAR) NOT NULL,
    node_type VARCHAR2(32 CHAR) NOT NULL,
    ref_id NUMBER(19) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    ref_definition_id NUMBER(19) DEFAULT NULL,
    ref_task_definition_id NUMBER(19) DEFAULT NULL,
    CONSTRAINT pk_ifj_job_definition_node PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_node_key UNIQUE (definition_id,node_key),
    CONSTRAINT chk_ifj_job_definitio_0419c6da CHECK ((((node_type = 'JOB') and (ref_definition_id is not null) and (ref_task_definition_id is null)) or ((node_type = 'TASK') and (ref_definition_id is null) and (ref_task_definition_id is not null))))
);

CREATE TABLE ifj_job_definition_node_policy (
    id NUMBER(19) NOT NULL,
    definition_id NUMBER(19) NOT NULL,
    node_id NUMBER(19) NOT NULL,
    fail_strategy VARCHAR2(32 CHAR) NOT NULL,
    execute_condition VARCHAR2(32 CHAR) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_job_definition_20444c56 PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_0cc6b157 UNIQUE (node_id)
);

CREATE TABLE ifj_job_edge (
    id NUMBER(19) NOT NULL,
    job_id NUMBER(19) NOT NULL,
    from_node_id NUMBER(19) NOT NULL,
    to_node_id NUMBER(19) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_job_edge PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_edge_path UNIQUE (job_id,from_node_id,to_node_id)
);

CREATE TABLE ifj_job_node (
    id NUMBER(19) NOT NULL,
    name VARCHAR2(128 CHAR) NOT NULL,
    job_id NUMBER(19) NOT NULL,
    node_type VARCHAR2(32 CHAR) NOT NULL,
    ref_id NUMBER(19) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_job_node PRIMARY KEY (id)
);

CREATE TABLE ifj_job_node_policy (
    id NUMBER(19) NOT NULL,
    node_id NUMBER(19) NOT NULL,
    fail_strategy VARCHAR2(32 CHAR) NOT NULL,
    execute_condition VARCHAR2(32 CHAR) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_job_node_policy PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_node_policy_node_id UNIQUE (node_id)
);

CREATE TABLE ifj_maintenance_lease (
    lease_name VARCHAR2(64 CHAR) NOT NULL,
    lease_owner VARCHAR2(128 CHAR) DEFAULT NULL,
    lease_token VARCHAR2(36 CHAR) DEFAULT NULL,
    lease_deadline TIMESTAMP(3) DEFAULT NULL,
    fence_token NUMBER(19) DEFAULT 0 NOT NULL,
    last_acquired_at TIMESTAMP(3) DEFAULT NULL,
    last_completed_at TIMESTAMP(3) DEFAULT NULL,
    last_success NUMBER(1) DEFAULT NULL,
    last_failure_code VARCHAR2(64 CHAR) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_maintenance_lease PRIMARY KEY (lease_name),
    CONSTRAINT uk_ifj_maintenance_lease_token UNIQUE (lease_token),
    CONSTRAINT chk_ifj_maintenance_l_fa47fc90 CHECK ((fence_token >= 0)),
    CONSTRAINT chk_ifj_maintenance_l_9250809a CHECK ((((lease_owner is null) and (lease_token is null) and (lease_deadline is null)) or ((lease_owner is not null) and (lease_token is not null) and (lease_deadline is not null))))
);

CREATE TABLE ifj_notification_outbox (
    id NUMBER(19) NOT NULL,
    deduplication_key VARCHAR2(191 CHAR) NOT NULL,
    notify_config_id NUMBER(19) NOT NULL,
    notify_type VARCHAR2(32 CHAR) DEFAULT NULL,
    config_snapshot CLOB,
    config_enabled NUMBER(1) DEFAULT NULL,
    title VARCHAR2(255 CHAR) DEFAULT NULL,
    subject VARCHAR2(255 CHAR) NOT NULL,
    content CLOB NOT NULL,
    source_type VARCHAR2(32 CHAR) NOT NULL,
    correlation_id VARCHAR2(64 CHAR) DEFAULT NULL,
    operator_id VARCHAR2(128 CHAR) DEFAULT NULL,
    target_type VARCHAR2(32 CHAR) DEFAULT NULL,
    target_id NUMBER(19) DEFAULT NULL,
    instance_id NUMBER(19) DEFAULT NULL,
    notify_binding_id NUMBER(19) DEFAULT NULL,
    status VARCHAR2(16 CHAR) NOT NULL,
    available_at TIMESTAMP(3) NOT NULL,
    lease_owner VARCHAR2(128 CHAR) DEFAULT NULL,
    claim_token VARCHAR2(36 CHAR) DEFAULT NULL,
    lease_deadline TIMESTAMP(3) DEFAULT NULL,
    attempt_count NUMBER(10) DEFAULT 0 NOT NULL,
    max_attempts NUMBER(10) NOT NULL,
    last_failure_code VARCHAR2(64 CHAR) DEFAULT NULL,
    last_failure_summary VARCHAR2(500 CHAR) DEFAULT NULL,
    delivered_at TIMESTAMP(3) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_notification_outbox PRIMARY KEY (id),
    CONSTRAINT uk_ifj_notification_o_3c31f053 UNIQUE (deduplication_key),
    CONSTRAINT uk_ifj_notification_o_25c73e23 UNIQUE (claim_token),
    CONSTRAINT chk_ifj_notification__90da384c CHECK (((attempt_count >= 0) and (max_attempts between 1 and 100))),
    CONSTRAINT chk_ifj_notification__8434d5ac CHECK ((((notify_type is null) and (config_snapshot is null) and (config_enabled is null)) or ((notify_type is not null) and (config_snapshot is not null) and (config_enabled is not null)))),
    CONSTRAINT chk_ifj_notification__09435689 CHECK ((((status = 'DELIVERED') and (delivered_at is not null)) or ((status <> 'DELIVERED') and (delivered_at is null)))),
    CONSTRAINT chk_ifj_notification__52206a41 CHECK ((((status = 'CLAIMED') and (lease_owner is not null) and (claim_token is not null) and (lease_deadline is not null)) or ((status <> 'CLAIMED') and (lease_owner is null) and (claim_token is null) and (lease_deadline is null)))),
    CONSTRAINT chk_ifj_notification__b2c3d1d5 CHECK ((status in ('READY','CLAIMED','RETRY_WAIT','DELIVERED','DEAD')))
);

CREATE TABLE ifj_notify_binding (
    id NUMBER(19) NOT NULL,
    target_type VARCHAR2(32 CHAR) NOT NULL,
    target_id NUMBER(19) NOT NULL,
    notify_config_id NUMBER(19) NOT NULL,
    notify_policy VARCHAR2(32 CHAR) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_notify_binding PRIMARY KEY (id),
    CONSTRAINT uk_ifj_notify_binding_target UNIQUE (target_type,target_id,notify_config_id)
);

CREATE TABLE ifj_notify_config (
    id NUMBER(19) NOT NULL,
    name VARCHAR2(128 CHAR) NOT NULL,
    type VARCHAR2(32 CHAR) NOT NULL,
    config CLOB NOT NULL,
    enabled NUMBER(1) DEFAULT 1 NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_notify_config PRIMARY KEY (id)
);

CREATE TABLE ifj_notify_send_log (
    id NUMBER(19) NOT NULL,
    source_type VARCHAR2(32 CHAR) NOT NULL,
    correlation_id VARCHAR2(64 CHAR) DEFAULT NULL,
    operator_id VARCHAR2(128 CHAR) DEFAULT NULL,
    target_type VARCHAR2(32 CHAR) DEFAULT NULL,
    target_id NUMBER(19) DEFAULT NULL,
    instance_id NUMBER(19) DEFAULT NULL,
    notify_binding_id NUMBER(19) DEFAULT NULL,
    notify_config_id NUMBER(19) NOT NULL,
    notify_type VARCHAR2(32 CHAR) DEFAULT NULL,
    result VARCHAR2(32 CHAR) NOT NULL,
    failure_code VARCHAR2(64 CHAR) DEFAULT NULL,
    failure_summary VARCHAR2(500 CHAR) DEFAULT NULL,
    duration_ms NUMBER(19) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_notify_send_log PRIMARY KEY (id)
);

CREATE TABLE ifj_resource (
    id NUMBER(19) NOT NULL,
    code VARCHAR2(128 CHAR) NOT NULL,
    name VARCHAR2(128 CHAR) NOT NULL,
    type VARCHAR2(32 CHAR) NOT NULL,
    config CLOB NOT NULL,
    enabled NUMBER(1) DEFAULT 1 NOT NULL,
    description VARCHAR2(500 CHAR) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_resource PRIMARY KEY (id),
    CONSTRAINT uk_ifj_resource_code UNIQUE (code)
);

CREATE TABLE ifj_runtime_command (
    request_id VARCHAR2(191 CHAR) NOT NULL,
    instance_id NUMBER(19) NOT NULL,
    root_instance_id NUMBER(19) NOT NULL,
    trigger_id NUMBER(19) NOT NULL,
    command_type VARCHAR2(16 CHAR) NOT NULL,
    status VARCHAR2(16 CHAR) NOT NULL,
    requested_by VARCHAR2(128 CHAR) NOT NULL,
    result_message VARCHAR2(500 CHAR) DEFAULT NULL,
    available_at TIMESTAMP(3) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    processing_owner VARCHAR2(128 CHAR) DEFAULT NULL,
    processing_token VARCHAR2(36 CHAR) DEFAULT NULL,
    processing_fence NUMBER(19) DEFAULT NULL,
    processed_at TIMESTAMP(3) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_runtime_command PRIMARY KEY (request_id),
    CONSTRAINT uk_ifj_runtime_comman_0ae37aa8 UNIQUE (processing_token),
    CONSTRAINT chk_ifj_runtime_comma_185442c6 CHECK ((((status = 'PROCESSING') and (processing_owner is not null) and (processing_token is not null) and (processing_fence is not null)) or ((status <> 'PROCESSING') and (processing_owner is null) and (processing_token is null) and (processing_fence is null)))),
    CONSTRAINT chk_ifj_runtime_command_status CHECK ((status in ('PENDING','PROCESSING','SUCCEEDED','REJECTED','FAILED','EXPIRED'))),
    CONSTRAINT chk_ifj_runtime_command_type CHECK ((command_type in ('STOP','PAUSE','RESUME','RETRY')))
);

CREATE TABLE ifj_schedule (
    id NUMBER(19) NOT NULL,
    target_type VARCHAR2(32 CHAR) NOT NULL,
    target_id NUMBER(19) NOT NULL,
    schedule_type VARCHAR2(32 CHAR) NOT NULL,
    cron_expr VARCHAR2(128 CHAR) DEFAULT NULL,
    interval_sec NUMBER(10) DEFAULT NULL,
    calendar_id NUMBER(19) DEFAULT NULL,
    start_time TIMESTAMP(3) DEFAULT NULL,
    end_time TIMESTAMP(3) DEFAULT NULL,
    enabled NUMBER(1) DEFAULT 1 NOT NULL,
    last_fire_time TIMESTAMP(3) DEFAULT NULL,
    next_fire_time TIMESTAMP(3) DEFAULT NULL,
    fire_claim_token VARCHAR2(36 CHAR) DEFAULT NULL,
    fire_claimed_at TIMESTAMP(3) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    variables CLOB,
    CONSTRAINT pk_ifj_schedule PRIMARY KEY (id),
    CONSTRAINT uk_ifj_schedule_fire__5af6299c UNIQUE (fire_claim_token),
    CONSTRAINT chk_ifj_schedule_fire_claim CHECK ((((fire_claim_token is null) and (fire_claimed_at is null)) or ((fire_claim_token is not null) and (fire_claimed_at is not null))))
);

CREATE TABLE ifj_schema_capability (
    capability_key VARCHAR2(64 CHAR) NOT NULL,
    capability_version NUMBER(10) NOT NULL,
    enabled NUMBER(1) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_schema_capability PRIMARY KEY (capability_key),
    CONSTRAINT chk_ifj_schema_cap_enabled CHECK ((enabled IN (0, 1))),
    CONSTRAINT chk_ifj_schema_cap_version CHECK ((capability_version > 0))
);

CREATE TABLE ifj_task (
    id NUMBER(19) NOT NULL,
    name VARCHAR2(128 CHAR) NOT NULL,
    plugin_type VARCHAR2(32 CHAR) NOT NULL,
    plugin_config CLOB NOT NULL,
    retry_times NUMBER(10) DEFAULT 0 NOT NULL,
    retry_interval NUMBER(10) DEFAULT 0 NOT NULL,
    timeout NUMBER(10) DEFAULT NULL,
    enabled NUMBER(1) DEFAULT 1 NOT NULL,
    description VARCHAR2(500 CHAR) DEFAULT NULL,
    concurrency_type VARCHAR2(32 CHAR) DEFAULT NULL,
    lock_key VARCHAR2(255 CHAR) DEFAULT NULL,
    max_concurrency NUMBER(10) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    current_task_definition_id NUMBER(19) DEFAULT NULL,
    current_revision NUMBER(19) DEFAULT NULL,
    retired NUMBER(1) DEFAULT 0 NOT NULL,
    retired_time TIMESTAMP(3) DEFAULT NULL,
    CONSTRAINT pk_ifj_task PRIMARY KEY (id),
    CONSTRAINT ck_ifj_task_current_p_ab96c32b CHECK ((((current_task_definition_id is null) and (current_revision is null)) or ((current_task_definition_id is not null) and (current_revision is not null)))),
    CONSTRAINT ck_ifj_task_current_r_a2032f56 CHECK (((current_revision is null) or (current_revision > 0))),
    CONSTRAINT ck_ifj_task_retired_state CHECK ((((retired = 0) and (retired_time is null)) or ((retired = 1) and (retired_time is not null))))
);

CREATE TABLE ifj_task_definition (
    id NUMBER(19) NOT NULL,
    task_id NUMBER(19) NOT NULL,
    revision NUMBER(19) NOT NULL,
    name VARCHAR2(128 CHAR) NOT NULL,
    plugin_type VARCHAR2(32 CHAR) NOT NULL,
    plugin_config CLOB NOT NULL,
    retry_times NUMBER(10) NOT NULL,
    retry_interval NUMBER(10) NOT NULL,
    timeout NUMBER(10) DEFAULT NULL,
    enabled NUMBER(1) NOT NULL,
    description VARCHAR2(500 CHAR) DEFAULT NULL,
    concurrency_type VARCHAR2(32 CHAR) NOT NULL,
    lock_key VARCHAR2(255 CHAR) DEFAULT NULL,
    max_concurrency NUMBER(10) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_task_definition PRIMARY KEY (id),
    CONSTRAINT uk_ifj_task_definitio_cfb06290 UNIQUE (task_id,revision),
    CONSTRAINT ck_ifj_task_definitio_8fc4b0a9 CHECK ((revision > 0))
);

CREATE TABLE ifj_trigger (
    id NUMBER(19) NOT NULL,
    schedule_id NUMBER(19) DEFAULT NULL,
    target_type VARCHAR2(32 CHAR) NOT NULL,
    target_id NUMBER(19) NOT NULL,
    trigger_type VARCHAR2(32 CHAR) NOT NULL,
    idempotency_key VARCHAR2(191 CHAR) NOT NULL,
    event_code VARCHAR2(128 CHAR) DEFAULT NULL,
    status VARCHAR2(32 CHAR) NOT NULL,
    trigger_time TIMESTAMP(3) NOT NULL,
    message CLOB,
    error_code VARCHAR2(64 CHAR) DEFAULT NULL,
    message_key VARCHAR2(128 CHAR) DEFAULT NULL,
    message_args CLOB DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    definition_id NUMBER(19) DEFAULT NULL,
    task_definition_id NUMBER(19) DEFAULT NULL,
    variables CLOB,
    event_id NUMBER(19) DEFAULT NULL,
    event_binding_id NUMBER(19) DEFAULT NULL,
    CONSTRAINT pk_ifj_trigger PRIMARY KEY (id),
    CONSTRAINT uk_ifj_trigger_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_ifj_trigger_event_binding UNIQUE (event_id,event_binding_id),
    CONSTRAINT chk_ifj_trigger_status CHECK ((status in ('WAITING','RUNNING','SUCCESS','FAILED','STOPPED','TIMED_OUT','SKIPPED')))
);

CREATE TABLE ifj_trigger_work_item (
    trigger_id NUMBER(19) NOT NULL,
    root_instance_id NUMBER(19) DEFAULT NULL,
    status VARCHAR2(16 CHAR) NOT NULL,
    available_at TIMESTAMP(3) NOT NULL,
    lease_owner VARCHAR2(128 CHAR) DEFAULT NULL,
    claim_token VARCHAR2(36 CHAR) DEFAULT NULL,
    lease_deadline TIMESTAMP(3) DEFAULT NULL,
    fence_token NUMBER(19) DEFAULT 0 NOT NULL,
    attempt_count NUMBER(10) DEFAULT 0 NOT NULL,
    max_attempts NUMBER(10) NOT NULL,
    last_error VARCHAR2(500 CHAR) DEFAULT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_trigger_work_item PRIMARY KEY (trigger_id),
    CONSTRAINT uk_ifj_trigger_work_i_97a8118e UNIQUE (claim_token),
    CONSTRAINT uk_ifj_trigger_work_item_root UNIQUE (root_instance_id),
    CONSTRAINT chk_ifj_trigger_work__19119f98 CHECK (((attempt_count >= 0) and (max_attempts between 1 and 100))),
    CONSTRAINT chk_ifj_trigger_work__e872ba72 CHECK ((fence_token >= 0)),
    CONSTRAINT chk_ifj_trigger_work__06fc1cec CHECK ((((status = 'CLAIMED') and (lease_owner is not null) and (claim_token is not null) and (lease_deadline is not null)) or ((status <> 'CLAIMED') and (lease_owner is null) and (claim_token is null) and (lease_deadline is null)))),
    CONSTRAINT chk_ifj_trigger_work__2cd1569d CHECK ((status in ('READY','CLAIMED','SUSPENDED','RETRY_WAIT','COMPLETED','DEAD')))
);
CREATE INDEX idx_ifj_app_run_statu_c908a4f8 ON ifj_app_run (status,heartbeat_at,run_id);
CREATE INDEX idx_ifj_app_run_insta_63af2488 ON ifj_app_run (instance_name,status,heartbeat_at,run_id);
CREATE INDEX idx_ifj_cluster_state_expiry ON ifj_cluster_state (expires_at,state_key);
CREATE INDEX idx_ifj_concurrency_p_204fb54a ON ifj_concurrency_permit (lease_deadline,scope_key,slot_no);
CREATE INDEX idx_ifj_concurrency_p_69ea6637 ON ifj_concurrency_permit (owner_root_instance_id,lease_deadline);
CREATE INDEX idx_ifj_concurrency_p_c15902e1 ON ifj_concurrency_policy (lock_key);
CREATE INDEX idx_ifj_etl_checkpoin_04f0cd14 ON ifj_etl_checkpoint (last_instance_id);
CREATE INDEX idx_ifj_event_code_business ON ifj_event (event_code,business_key,arrived_at,id);
CREATE INDEX idx_ifj_event_code_correlation ON ifj_event (event_code,correlation_key,arrived_at,id);
CREATE INDEX idx_ifj_event_arrived ON ifj_event (arrived_at,id);
CREATE INDEX idx_ifj_event_binding_event ON ifj_event_binding (event_code,enabled);
CREATE INDEX fk_ifj_event_wait_event ON ifj_event_wait (matched_event_id);
CREATE INDEX idx_ifj_event_wait_ma_564c0756 ON ifj_event_wait (status,event_code,business_key,accept_after,id);
CREATE INDEX idx_ifj_event_wait_ma_a33f67de ON ifj_event_wait (status,event_code,correlation_key,accept_after,id);
CREATE INDEX idx_ifj_event_wait_root_status ON ifj_event_wait (root_instance_id,status);
CREATE INDEX idx_ifj_event_wait_timeout ON ifj_event_wait (status,timeout_at,id);
CREATE INDEX idx_ifj_wait_operator_lookup ON ifj_event_wait_operator (operator_id,wait_id);
CREATE INDEX fk_ifj_instance_node ON ifj_instance (node_id);
CREATE INDEX idx_ifj_instance_root_status ON ifj_instance (root_instance_id,status);
CREATE INDEX idx_ifj_instance_target ON ifj_instance (target_type,target_id);
CREATE INDEX idx_ifj_instance_trigger_id ON ifj_instance (trigger_id);
CREATE INDEX fk_ifj_instance_defin_3b1ac219 ON ifj_instance (definition_node_id);
CREATE INDEX idx_ifj_instance_defi_9a71daf2 ON ifj_instance (definition_id,status);
CREATE INDEX idx_ifj_instance_task_7e183bb6 ON ifj_instance (task_definition_id,status);
CREATE INDEX idx_ifj_instance_status_update ON ifj_instance (status,update_time,id);
CREATE INDEX idx_ifj_instance_error_update ON ifj_instance (error_code,update_time,id);
CREATE INDEX idx_ifj_instance_root_timeout ON ifj_instance (parent_instance_id,status,timeout_at,id);
CREATE INDEX idx_ifj_instance_log__8cd01f53 ON ifj_instance_log (instance_id,log_time);
CREATE INDEX idx_ifj_job_enabled ON ifj_job (enabled);
CREATE INDEX idx_ifj_job_current_d_fb089bbb ON ifj_job (current_definition_id);
CREATE INDEX idx_ifj_job_definitio_c5d1885e ON ifj_job_definition (job_id,create_time,id);
CREATE INDEX fk_ifj_job_definition_79056272 ON ifj_job_definition_edge (from_node_id);
CREATE INDEX idx_ifj_job_definitio_ba928276 ON ifj_job_definition_edge (to_node_id);
CREATE INDEX idx_ifj_job_definitio_72a25ac7 ON ifj_job_definition_node (node_type,ref_id);
CREATE INDEX idx_ifj_job_definitio_df55d3cb ON ifj_job_definition_node (ref_definition_id);
CREATE INDEX idx_ifj_job_definitio_205f9549 ON ifj_job_definition_node (ref_task_definition_id);
CREATE INDEX idx_ifj_job_definitio_6c9eb25a ON ifj_job_definition_node_policy (definition_id);
CREATE INDEX fk_ifj_job_edge_from_node ON ifj_job_edge (from_node_id);
CREATE INDEX idx_ifj_job_edge_to_node_id ON ifj_job_edge (to_node_id);
CREATE INDEX idx_ifj_job_node_job_id ON ifj_job_node (job_id);
CREATE INDEX idx_ifj_job_node_ref ON ifj_job_node (node_type,ref_id);
CREATE INDEX idx_ifj_maintenance_l_bdb7ae34 ON ifj_maintenance_lease (lease_deadline,lease_name);
CREATE INDEX idx_ifj_notification__0b82f8a0 ON ifj_notification_outbox (status,available_at,id);
CREATE INDEX idx_ifj_notification__f4a0f220 ON ifj_notification_outbox (status,lease_deadline,id);
CREATE INDEX idx_ifj_notification__a681571b ON ifj_notification_outbox (instance_id,status,id);
CREATE INDEX idx_ifj_notification__3e08a4e8 ON ifj_notification_outbox (create_time,id);
CREATE INDEX fk_ifj_notify_binding_config ON ifj_notify_binding (notify_config_id);
CREATE INDEX idx_ifj_notify_binding_target ON ifj_notify_binding (target_type,target_id);
CREATE INDEX idx_ifj_notify_config_4aa81d29 ON ifj_notify_config (type,enabled);
CREATE INDEX idx_ifj_notify_send_l_c837b8fc ON ifj_notify_send_log (create_time,id);
CREATE INDEX idx_ifj_notify_send_l_16d964d5 ON ifj_notify_send_log (notify_config_id,create_time);
CREATE INDEX idx_ifj_notify_send_l_381b7a26 ON ifj_notify_send_log (target_type,target_id,create_time);
CREATE INDEX idx_ifj_notify_send_l_985ab721 ON ifj_notify_send_log (instance_id,create_time);
CREATE INDEX idx_ifj_notify_send_l_5cb42ddb ON ifj_notify_send_log (result,create_time);
CREATE INDEX idx_ifj_resource_type_enabled ON ifj_resource (type,enabled);
CREATE INDEX fk_ifj_runtime_comman_0c59fe1e ON ifj_runtime_command (instance_id);
CREATE INDEX idx_ifj_runtime_comma_c631e259 ON ifj_runtime_command (trigger_id,status,available_at,expires_at,create_time);
CREATE INDEX idx_ifj_runtime_command_root ON ifj_runtime_command (root_instance_id,create_time);
CREATE INDEX idx_ifj_runtime_comma_76d57552 ON ifj_runtime_command (status,expires_at,request_id);
CREATE INDEX fk_ifj_schedule_calendar ON ifj_schedule (calendar_id);
CREATE INDEX idx_ifj_schedule_target ON ifj_schedule (target_type,target_id);
CREATE INDEX idx_ifj_schedule_fire ON ifj_schedule (enabled,next_fire_time);
CREATE INDEX idx_ifj_task_enabled__74adff46 ON ifj_task (enabled,plugin_type);
CREATE INDEX idx_ifj_task_current__092ba315 ON ifj_task (current_task_definition_id);
CREATE INDEX idx_ifj_task_definiti_423cc129 ON ifj_task_definition (task_id,create_time,id);
CREATE INDEX fk_ifj_trigger_schedule ON ifj_trigger (schedule_id);
CREATE INDEX idx_ifj_trigger_status_time ON ifj_trigger (status,trigger_time);
CREATE INDEX idx_ifj_trigger_target ON ifj_trigger (target_type,target_id);
CREATE INDEX idx_ifj_trigger_defin_2602d34b ON ifj_trigger (definition_id,status);
CREATE INDEX idx_ifj_trigger_task__4b6c84ee ON ifj_trigger (task_definition_id,status);
CREATE INDEX fk_ifj_trigger_event_binding ON ifj_trigger (event_binding_id);
CREATE INDEX idx_ifj_trigger_event ON ifj_trigger (event_id);
CREATE INDEX idx_ifj_trigger_status_update ON ifj_trigger (status,update_time,id);
CREATE INDEX idx_ifj_trigger_error_update ON ifj_trigger (error_code,update_time,id);
CREATE INDEX idx_ifj_trigger_work__79f8a061 ON ifj_trigger_work_item (status,available_at,lease_deadline,trigger_id);
CREATE INDEX idx_ifj_trigger_work__3d5c15e6 ON ifj_trigger_work_item (lease_owner,lease_deadline,trigger_id);
ALTER TABLE ifj_calendar_exception ADD CONSTRAINT fk_ifj_calendar_excep_cc5e52f7 FOREIGN KEY (calendar_id) REFERENCES ifj_calendar (id) ON DELETE CASCADE;
ALTER TABLE ifj_event_wait ADD CONSTRAINT fk_ifj_event_wait_event FOREIGN KEY (matched_event_id) REFERENCES ifj_event (id) ON DELETE SET NULL;
ALTER TABLE ifj_event_wait ADD CONSTRAINT fk_ifj_event_wait_instance FOREIGN KEY (instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE;
ALTER TABLE ifj_event_wait ADD CONSTRAINT fk_ifj_event_wait_root FOREIGN KEY (root_instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE;
ALTER TABLE ifj_event_wait_operator ADD CONSTRAINT fk_ifj_wait_operator_wait FOREIGN KEY (wait_id) REFERENCES ifj_event_wait (id) ON DELETE CASCADE;
ALTER TABLE ifj_instance ADD CONSTRAINT fk_ifj_instance_definition FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id);
ALTER TABLE ifj_instance ADD CONSTRAINT fk_ifj_instance_defin_3b1ac219 FOREIGN KEY (definition_node_id) REFERENCES ifj_job_definition_node (id);
ALTER TABLE ifj_instance ADD CONSTRAINT fk_ifj_instance_node FOREIGN KEY (node_id) REFERENCES ifj_job_node (id) ON DELETE SET NULL;
ALTER TABLE ifj_instance ADD CONSTRAINT fk_ifj_instance_parent FOREIGN KEY (parent_instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE;
ALTER TABLE ifj_instance ADD CONSTRAINT fk_ifj_instance_root FOREIGN KEY (root_instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE;
ALTER TABLE ifj_instance ADD CONSTRAINT fk_ifj_instance_task__c0fe10fb FOREIGN KEY (task_definition_id) REFERENCES ifj_task_definition (id);
ALTER TABLE ifj_instance ADD CONSTRAINT fk_ifj_instance_trigger FOREIGN KEY (trigger_id) REFERENCES ifj_trigger (id) ON DELETE SET NULL;
ALTER TABLE ifj_instance_log ADD CONSTRAINT fk_ifj_instance_log_instance FOREIGN KEY (instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE;
ALTER TABLE ifj_job_definition ADD CONSTRAINT fk_ifj_job_definition_job FOREIGN KEY (job_id) REFERENCES ifj_job (id);
ALTER TABLE ifj_job_definition_edge ADD CONSTRAINT fk_ifj_job_definition_fe04be78 FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id);
ALTER TABLE ifj_job_definition_edge ADD CONSTRAINT fk_ifj_job_definition_79056272 FOREIGN KEY (from_node_id) REFERENCES ifj_job_definition_node (id);
ALTER TABLE ifj_job_definition_edge ADD CONSTRAINT fk_ifj_job_definition_4dda6d20 FOREIGN KEY (to_node_id) REFERENCES ifj_job_definition_node (id);
ALTER TABLE ifj_job_definition_node ADD CONSTRAINT fk_ifj_job_definition_234818f0 FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id);
ALTER TABLE ifj_job_definition_node ADD CONSTRAINT fk_ifj_job_definition_97efe5c6 FOREIGN KEY (ref_definition_id) REFERENCES ifj_job_definition (id);
ALTER TABLE ifj_job_definition_node ADD CONSTRAINT fk_ifj_job_definition_bf063966 FOREIGN KEY (ref_task_definition_id) REFERENCES ifj_task_definition (id);
ALTER TABLE ifj_job_definition_node_policy ADD CONSTRAINT fk_ifj_job_definition_ee502759 FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id);
ALTER TABLE ifj_job_definition_node_policy ADD CONSTRAINT fk_ifj_job_definition_0d5d7339 FOREIGN KEY (node_id) REFERENCES ifj_job_definition_node (id);
ALTER TABLE ifj_job_edge ADD CONSTRAINT fk_ifj_job_edge_from_node FOREIGN KEY (from_node_id) REFERENCES ifj_job_node (id) ON DELETE CASCADE;
ALTER TABLE ifj_job_edge ADD CONSTRAINT fk_ifj_job_edge_job FOREIGN KEY (job_id) REFERENCES ifj_job (id) ON DELETE CASCADE;
ALTER TABLE ifj_job_edge ADD CONSTRAINT fk_ifj_job_edge_to_node FOREIGN KEY (to_node_id) REFERENCES ifj_job_node (id) ON DELETE CASCADE;
ALTER TABLE ifj_job_node ADD CONSTRAINT fk_ifj_job_node_job FOREIGN KEY (job_id) REFERENCES ifj_job (id) ON DELETE CASCADE;
ALTER TABLE ifj_job_node_policy ADD CONSTRAINT fk_ifj_job_node_policy_node FOREIGN KEY (node_id) REFERENCES ifj_job_node (id) ON DELETE CASCADE;
ALTER TABLE ifj_notify_binding ADD CONSTRAINT fk_ifj_notify_binding_config FOREIGN KEY (notify_config_id) REFERENCES ifj_notify_config (id) ON DELETE CASCADE;
ALTER TABLE ifj_runtime_command ADD CONSTRAINT fk_ifj_runtime_comman_0c59fe1e FOREIGN KEY (instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE;
ALTER TABLE ifj_runtime_command ADD CONSTRAINT fk_ifj_runtime_command_root FOREIGN KEY (root_instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE;
ALTER TABLE ifj_runtime_command ADD CONSTRAINT fk_ifj_runtime_command_trigger FOREIGN KEY (trigger_id) REFERENCES ifj_trigger (id) ON DELETE CASCADE;
ALTER TABLE ifj_schedule ADD CONSTRAINT fk_ifj_schedule_calendar FOREIGN KEY (calendar_id) REFERENCES ifj_calendar (id) ON DELETE SET NULL;
ALTER TABLE ifj_task_definition ADD CONSTRAINT fk_ifj_task_definition_task FOREIGN KEY (task_id) REFERENCES ifj_task (id);
ALTER TABLE ifj_trigger ADD CONSTRAINT fk_ifj_trigger_definition FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id);
ALTER TABLE ifj_trigger ADD CONSTRAINT fk_ifj_trigger_event FOREIGN KEY (event_id) REFERENCES ifj_event (id) ON DELETE SET NULL;
ALTER TABLE ifj_trigger ADD CONSTRAINT fk_ifj_trigger_event_binding FOREIGN KEY (event_binding_id) REFERENCES ifj_event_binding (id) ON DELETE SET NULL;
ALTER TABLE ifj_trigger ADD CONSTRAINT fk_ifj_trigger_schedule FOREIGN KEY (schedule_id) REFERENCES ifj_schedule (id) ON DELETE SET NULL;
ALTER TABLE ifj_trigger ADD CONSTRAINT fk_ifj_trigger_task_definition FOREIGN KEY (task_definition_id) REFERENCES ifj_task_definition (id);
ALTER TABLE ifj_trigger_work_item ADD CONSTRAINT fk_ifj_trigger_work_item_root FOREIGN KEY (root_instance_id) REFERENCES ifj_instance (id) ON DELETE SET NULL;
ALTER TABLE ifj_trigger_work_item ADD CONSTRAINT fk_ifj_trigger_work_i_19c2d00f FOREIGN KEY (trigger_id) REFERENCES ifj_trigger (id) ON DELETE CASCADE;

CREATE OR REPLACE TRIGGER trg_ifj_calendar_touch
BEFORE UPDATE ON ifj_calendar
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_calendar_exce_d84b6a4d
BEFORE UPDATE ON ifj_calendar_exception
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_concurrency_p_a17aea28
BEFORE UPDATE ON ifj_concurrency_policy
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_event_binding_touch
BEFORE UPDATE ON ifj_event_binding
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_event_wait_touch
BEFORE UPDATE ON ifj_event_wait
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_instance_touch
BEFORE UPDATE ON ifj_instance
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_job_touch
BEFORE UPDATE ON ifj_job
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_job_edge_touch
BEFORE UPDATE ON ifj_job_edge
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_job_node_touch
BEFORE UPDATE ON ifj_job_node
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_job_node_policy_touch
BEFORE UPDATE ON ifj_job_node_policy
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_notify_binding_touch
BEFORE UPDATE ON ifj_notify_binding
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_notify_config_touch
BEFORE UPDATE ON ifj_notify_config
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_notify_send_log_touch
BEFORE UPDATE ON ifj_notify_send_log
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_resource_touch
BEFORE UPDATE ON ifj_resource
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_schedule_touch
BEFORE UPDATE ON ifj_schedule
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_schema_capabi_4eb5bcaf
BEFORE UPDATE ON ifj_schema_capability
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_task_touch
BEFORE UPDATE ON ifj_task
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

CREATE OR REPLACE TRIGGER trg_ifj_trigger_touch
BEFORE UPDATE ON ifj_trigger
FOR EACH ROW
BEGIN
    :NEW.update_time := SYS_EXTRACT_UTC(SYSTIMESTAMP);
END;
/

INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
VALUES ('platform-primary-id', 1, 1000, CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)));

INSERT ALL
    INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time)
    VALUES ('platform-schema', 19, 1, CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)), CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)))
    INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time)
    VALUES ('event-wait-operator-relation', 1, 1, CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)), CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)))
    INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time)
    VALUES ('stable-id-segment-seed', 1, 1, CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)), CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)))
SELECT 1 FROM dual;
