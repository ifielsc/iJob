-- SQL Server 2019+ platform baseline aligned with the V19 logical schema.
-- IDs are supplied by the application-level database segment allocator; no database-generated IDs are used.
-- Identifier fields use an explicit BIN2 collation and human-search fields use an explicit CI collation.

CREATE TABLE dbo.ifj_app_run (
    run_id varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    instance_name nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    runtime_mode varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL DEFAULT 'STANDALONE',
    roles varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL DEFAULT 'API,SCHEDULER,WORKER',
    status varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    application_version varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    host_name varchar(255) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    process_id bigint DEFAULT NULL,
    started_at datetime2(3) NOT NULL,
    heartbeat_at datetime2(3) NOT NULL,
    stopped_at datetime2(3) DEFAULT NULL,
    CONSTRAINT pk_ifj_app_run PRIMARY KEY (run_id),
    CONSTRAINT chk_ifj_app_run_runtime_mode CHECK ((runtime_mode in ('DEVELOPMENT','STANDALONE','CLUSTER'))),
    CONSTRAINT chk_ifj_app_run_status CHECK ((status in ('STARTING','RUNNING','DRAINING','STOPPED','CRASHED')))
);

CREATE TABLE dbo.ifj_calendar (
    id bigint NOT NULL,
    name nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    workdays_mask int NOT NULL,
    description nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_calendar PRIMARY KEY (id),
    CONSTRAINT uk_ifj_calendar_name UNIQUE (name)
);

CREATE TABLE dbo.ifj_calendar_exception (
    id bigint NOT NULL,
    calendar_id bigint NOT NULL,
    date date NOT NULL,
    type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_calendar_exception PRIMARY KEY (id),
    CONSTRAINT uk_ifj_calendar_exception_date UNIQUE (calendar_id,date)
);

CREATE TABLE dbo.ifj_cluster_state (
    state_key varchar(512) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    state_value nvarchar(max) COLLATE Latin1_General_100_BIN2 NOT NULL,
    expires_at datetime2(3) DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_cluster_state PRIMARY KEY (state_key)
);

CREATE TABLE dbo.ifj_concurrency_permit (
    scope_key varchar(191) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    slot_no int NOT NULL,
    owner_instance_id bigint DEFAULT NULL,
    owner_root_instance_id bigint DEFAULT NULL,
    owner_trigger_id bigint DEFAULT NULL,
    lease_owner varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    permit_token varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    permit_fence bigint NOT NULL DEFAULT 0,
    execution_fence bigint DEFAULT NULL,
    lease_deadline datetime2(3) DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_concurrency_permit PRIMARY KEY (scope_key,slot_no),
    CONSTRAINT chk_ifj_concurrency_permit_fence CHECK ((permit_fence >= 0)),
    CONSTRAINT chk_ifj_concurrency_permit_lease CHECK ((((owner_instance_id is null) and (owner_root_instance_id is null) and (owner_trigger_id is null) and (lease_owner is null) and (permit_token is null) and (execution_fence is null) and (lease_deadline is null)) or ((owner_instance_id is not null) and (owner_root_instance_id is not null) and (owner_trigger_id is not null) and (lease_owner is not null) and (permit_token is not null) and (execution_fence is not null) and (lease_deadline is not null)))),
    CONSTRAINT chk_ifj_concurrency_permit_slot CHECK ((slot_no > 0))
);

CREATE TABLE dbo.ifj_concurrency_policy (
    id bigint NOT NULL,
    target_id bigint NOT NULL,
    target_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    concurrency_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    lock_key varchar(255) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    max_concurrency int DEFAULT NULL,
    enabled bit NOT NULL DEFAULT 1,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_concurrency_policy PRIMARY KEY (id),
    CONSTRAINT uk_ifj_concurrency_policy_target UNIQUE (target_type,target_id)
);

CREATE TABLE dbo.ifj_etl_checkpoint (
    task_id bigint NOT NULL,
    checkpoint_key varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    source_fingerprint varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    watermark_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    watermark_value nvarchar(max) COLLATE Latin1_General_100_BIN2,
    tie_breaker_value nvarchar(max) COLLATE Latin1_General_100_BIN2,
    last_instance_id bigint DEFAULT NULL,
    version bigint NOT NULL DEFAULT 0,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_etl_checkpoint PRIMARY KEY (task_id,checkpoint_key),
    CONSTRAINT ck_ifj_etl_checkpoint_task CHECK ((task_id > 0)),
    CONSTRAINT ck_ifj_etl_checkpoint_version CHECK ((version >= 0))
);

CREATE TABLE dbo.ifj_event (
    id bigint NOT NULL,
    event_code varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    source_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    source_key varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    business_key varchar(256) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    correlation_key varchar(256) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    idempotency_key varchar(256) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    occurred_at datetime2(3) NOT NULL,
    arrived_at datetime2(3) NOT NULL,
    summary nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    payload nvarchar(max) COLLATE Latin1_General_100_BIN2,
    payload_digest varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    payload_bytes int NOT NULL DEFAULT 0,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_event PRIMARY KEY (id),
    CONSTRAINT uk_ifj_event_source_idempotency UNIQUE (source_key,idempotency_key),
    CONSTRAINT chk_ifj_event_payload_bytes CHECK ((payload_bytes >= 0))
);

CREATE TABLE dbo.ifj_event_binding (
    id bigint NOT NULL,
    target_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    target_id bigint NOT NULL,
    event_code varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    enabled bit NOT NULL DEFAULT 1,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_event_binding PRIMARY KEY (id),
    CONSTRAINT uk_ifj_event_binding_target UNIQUE (target_type,target_id,event_code)
);

CREATE TABLE dbo.ifj_event_wait (
    id bigint NOT NULL,
    instance_id bigint NOT NULL,
    root_instance_id bigint NOT NULL,
    attempt_no int NOT NULL,
    wait_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    status varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    event_code varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    business_key varchar(256) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    correlation_key varchar(256) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    accept_after datetime2(3) NOT NULL,
    timeout_at datetime2(3) DEFAULT NULL,
    matched_event_id bigint DEFAULT NULL,
    allowed_operator_ids nvarchar(max) COLLATE Latin1_General_100_BIN2,
    decision_by varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    decision_comment nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    resolved_at datetime2(3) DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_event_wait PRIMARY KEY (id),
    CONSTRAINT uk_ifj_event_wait_attempt UNIQUE (instance_id,attempt_no),
    CONSTRAINT chk_ifj_event_wait_attempt CHECK ((attempt_no >= 0)),
    CONSTRAINT chk_ifj_event_wait_status CHECK ((status in ('WAITING','MATCHED','APPROVED','REJECTED','TIMED_OUT','WITHDRAWN','STOPPED'))),
    CONSTRAINT chk_ifj_event_wait_type CHECK ((wait_type in ('EVENT','APPROVAL')))
);

CREATE TABLE dbo.ifj_event_wait_operator (
    wait_id bigint NOT NULL,
    operator_id varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_event_wait_operator PRIMARY KEY (wait_id,operator_id),
    CONSTRAINT chk_ifj_wait_operator_id CHECK ((LEN(operator_id) > 0))
);

CREATE TABLE dbo.ifj_id_segment (
    segment_key varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    next_value bigint NOT NULL,
    segment_size int NOT NULL,
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_id_segment PRIMARY KEY (segment_key),
    CONSTRAINT chk_ifj_id_segment_next_value CHECK ((next_value > 0)),
    CONSTRAINT chk_ifj_id_segment_size CHECK ((segment_size between 1 and 100000))
);

CREATE TABLE dbo.ifj_instance (
    id bigint NOT NULL,
    node_id bigint DEFAULT NULL,
    parent_instance_id bigint DEFAULT NULL,
    root_instance_id bigint DEFAULT NULL,
    execution_fence bigint NOT NULL DEFAULT 0,
    target_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    target_id bigint NOT NULL,
    trigger_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    trigger_id bigint DEFAULT NULL,
    status varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    progress int NOT NULL DEFAULT 0,
    retry_count int NOT NULL DEFAULT 0,
    start_time datetime2(3) DEFAULT NULL,
    timeout_at datetime2(3) DEFAULT NULL,
    end_time datetime2(3) DEFAULT NULL,
    message nvarchar(max) COLLATE Latin1_General_100_CI_AI_SC,
    error_code varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    message_key varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    message_args nvarchar(max) COLLATE Latin1_General_100_BIN2 DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    definition_id bigint DEFAULT NULL,
    definition_node_id bigint DEFAULT NULL,
    task_definition_id bigint DEFAULT NULL,
    input_variables nvarchar(max) COLLATE Latin1_General_100_BIN2,
    output_variables nvarchar(max) COLLATE Latin1_General_100_BIN2,
    CONSTRAINT pk_ifj_instance PRIMARY KEY (id),
    CONSTRAINT chk_ifj_instance_execution_fence CHECK ((execution_fence >= 0)),
    CONSTRAINT chk_ifj_instance_progress CHECK ((progress between 0 and 100)),
    CONSTRAINT chk_ifj_instance_retry_count CHECK ((retry_count >= 0)),
    CONSTRAINT chk_ifj_instance_status CHECK ((status in ('WAITING','RUNNING','PAUSED','WAITING_EVENT','SUCCESS','FAILED','STOPPED','TIMED_OUT','SKIPPED')))
);

CREATE TABLE dbo.ifj_instance_log (
    id bigint NOT NULL,
    instance_id bigint NOT NULL,
    log_time datetime2(3) NOT NULL,
    level varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    content nvarchar(max) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    CONSTRAINT pk_ifj_instance_log PRIMARY KEY (id)
);

CREATE TABLE dbo.ifj_job (
    id bigint NOT NULL,
    name nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    timeout int DEFAULT NULL,
    enabled bit NOT NULL DEFAULT 1,
    description nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    concurrency_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    lock_key varchar(255) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    max_concurrency int DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    current_definition_id bigint DEFAULT NULL,
    current_revision bigint DEFAULT NULL,
    retired bit NOT NULL DEFAULT 0,
    retired_time datetime2(3) DEFAULT NULL,
    CONSTRAINT pk_ifj_job PRIMARY KEY (id),
    CONSTRAINT ck_ifj_job_current_pointer_pair CHECK ((((current_definition_id is null) and (current_revision is null)) or ((current_definition_id is not null) and (current_revision is not null)))),
    CONSTRAINT ck_ifj_job_retired_state CHECK ((((retired = 0) and (retired_time is null)) or ((retired = 1) and (retired_time is not null))))
);

CREATE TABLE dbo.ifj_job_definition (
    id bigint NOT NULL,
    job_id bigint NOT NULL,
    revision bigint NOT NULL,
    name nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    timeout int DEFAULT NULL,
    enabled bit NOT NULL,
    description nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_job_definition PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_job_revision UNIQUE (job_id,revision),
    CONSTRAINT ck_ifj_job_definition_revision CHECK ((revision > 0))
);

CREATE TABLE dbo.ifj_job_definition_edge (
    id bigint NOT NULL,
    definition_id bigint NOT NULL,
    from_node_id bigint NOT NULL,
    to_node_id bigint NOT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_job_definition_edge PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_edge_path UNIQUE (definition_id,from_node_id,to_node_id)
);

CREATE TABLE dbo.ifj_job_definition_node (
    id bigint NOT NULL,
    definition_id bigint NOT NULL,
    node_key varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    name nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    node_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    ref_id bigint NOT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    ref_definition_id bigint DEFAULT NULL,
    ref_task_definition_id bigint DEFAULT NULL,
    CONSTRAINT pk_ifj_job_definition_node PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_node_key UNIQUE (definition_id,node_key),
    CONSTRAINT chk_ifj_job_definition_node_ref_snapshot CHECK ((((node_type = 'JOB') and (ref_definition_id is not null) and (ref_task_definition_id is null)) or ((node_type = 'TASK') and (ref_definition_id is null) and (ref_task_definition_id is not null))))
);

CREATE TABLE dbo.ifj_job_definition_node_policy (
    id bigint NOT NULL,
    definition_id bigint NOT NULL,
    node_id bigint NOT NULL,
    fail_strategy varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    execute_condition varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_job_definition_node_policy PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_node_policy_node UNIQUE (node_id)
);

CREATE TABLE dbo.ifj_job_edge (
    id bigint NOT NULL,
    job_id bigint NOT NULL,
    from_node_id bigint NOT NULL,
    to_node_id bigint NOT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_job_edge PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_edge_path UNIQUE (job_id,from_node_id,to_node_id)
);

CREATE TABLE dbo.ifj_job_node (
    id bigint NOT NULL,
    name nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    job_id bigint NOT NULL,
    node_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    ref_id bigint NOT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_job_node PRIMARY KEY (id)
);

CREATE TABLE dbo.ifj_job_node_policy (
    id bigint NOT NULL,
    node_id bigint NOT NULL,
    fail_strategy varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    execute_condition varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_job_node_policy PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_node_policy_node_id UNIQUE (node_id)
);

CREATE TABLE dbo.ifj_maintenance_lease (
    lease_name varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    lease_owner varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    lease_token varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    lease_deadline datetime2(3) DEFAULT NULL,
    fence_token bigint NOT NULL DEFAULT 0,
    last_acquired_at datetime2(3) DEFAULT NULL,
    last_completed_at datetime2(3) DEFAULT NULL,
    last_success bit DEFAULT NULL,
    last_failure_code varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_maintenance_lease PRIMARY KEY (lease_name),
    CONSTRAINT chk_ifj_maintenance_lease_fence CHECK ((fence_token >= 0)),
    CONSTRAINT chk_ifj_maintenance_lease_holder CHECK ((((lease_owner is null) and (lease_token is null) and (lease_deadline is null)) or ((lease_owner is not null) and (lease_token is not null) and (lease_deadline is not null))))
);

CREATE TABLE dbo.ifj_notification_outbox (
    id bigint NOT NULL,
    deduplication_key varchar(191) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    notify_config_id bigint NOT NULL,
    notify_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    config_snapshot nvarchar(max) COLLATE Latin1_General_100_BIN2,
    config_enabled bit DEFAULT NULL,
    title nvarchar(255) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    subject nvarchar(255) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    content nvarchar(max) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    source_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    correlation_id varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    operator_id varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    target_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    target_id bigint DEFAULT NULL,
    instance_id bigint DEFAULT NULL,
    notify_binding_id bigint DEFAULT NULL,
    status varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    available_at datetime2(3) NOT NULL,
    lease_owner varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    claim_token varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    lease_deadline datetime2(3) DEFAULT NULL,
    attempt_count int NOT NULL DEFAULT 0,
    max_attempts int NOT NULL,
    last_failure_code varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    last_failure_summary nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    delivered_at datetime2(3) DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_notification_outbox PRIMARY KEY (id),
    CONSTRAINT uk_ifj_notification_outbox_deduplication UNIQUE (deduplication_key),
    CONSTRAINT chk_ifj_notification_outbox_attempts CHECK (((attempt_count >= 0) and (max_attempts between 1 and 100))),
    CONSTRAINT chk_ifj_notification_outbox_config_snapshot CHECK ((((notify_type is null) and (config_snapshot is null) and (config_enabled is null)) or ((notify_type is not null) and (config_snapshot is not null) and (config_enabled is not null)))),
    CONSTRAINT chk_ifj_notification_outbox_delivery CHECK ((((status = 'DELIVERED') and (delivered_at is not null)) or ((status <> 'DELIVERED') and (delivered_at is null)))),
    CONSTRAINT chk_ifj_notification_outbox_lease CHECK ((((status = 'CLAIMED') and (lease_owner is not null) and (claim_token is not null) and (lease_deadline is not null)) or ((status <> 'CLAIMED') and (lease_owner is null) and (claim_token is null) and (lease_deadline is null)))),
    CONSTRAINT chk_ifj_notification_outbox_status CHECK ((status in ('READY','CLAIMED','RETRY_WAIT','DELIVERED','DEAD')))
);

CREATE TABLE dbo.ifj_notify_binding (
    id bigint NOT NULL,
    target_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    target_id bigint NOT NULL,
    notify_config_id bigint NOT NULL,
    notify_policy varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_notify_binding PRIMARY KEY (id),
    CONSTRAINT uk_ifj_notify_binding_target UNIQUE (target_type,target_id,notify_config_id)
);

CREATE TABLE dbo.ifj_notify_config (
    id bigint NOT NULL,
    name nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    config nvarchar(max) COLLATE Latin1_General_100_BIN2 NOT NULL,
    enabled bit NOT NULL DEFAULT 1,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_notify_config PRIMARY KEY (id)
);

CREATE TABLE dbo.ifj_notify_send_log (
    id bigint NOT NULL,
    source_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    correlation_id varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    operator_id varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    target_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    target_id bigint DEFAULT NULL,
    instance_id bigint DEFAULT NULL,
    notify_binding_id bigint DEFAULT NULL,
    notify_config_id bigint NOT NULL,
    notify_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    result varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    failure_code varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    failure_summary nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    duration_ms bigint DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_notify_send_log PRIMARY KEY (id)
);

CREATE TABLE dbo.ifj_resource (
    id bigint NOT NULL,
    code varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    name nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    config nvarchar(max) COLLATE Latin1_General_100_BIN2 NOT NULL,
    enabled bit NOT NULL DEFAULT 1,
    description nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_resource PRIMARY KEY (id),
    CONSTRAINT uk_ifj_resource_code UNIQUE (code)
);

CREATE TABLE dbo.ifj_runtime_command (
    request_id varchar(191) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    instance_id bigint NOT NULL,
    root_instance_id bigint NOT NULL,
    trigger_id bigint NOT NULL,
    command_type varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    status varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    requested_by varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    result_message varchar(500) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    available_at datetime2(3) NOT NULL,
    expires_at datetime2(3) NOT NULL,
    processing_owner varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    processing_token varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    processing_fence bigint DEFAULT NULL,
    processed_at datetime2(3) DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_runtime_command PRIMARY KEY (request_id),
    CONSTRAINT chk_ifj_runtime_command_processing CHECK ((((status = 'PROCESSING') and (processing_owner is not null) and (processing_token is not null) and (processing_fence is not null)) or ((status <> 'PROCESSING') and (processing_owner is null) and (processing_token is null) and (processing_fence is null)))),
    CONSTRAINT chk_ifj_runtime_command_status CHECK ((status in ('PENDING','PROCESSING','SUCCEEDED','REJECTED','FAILED','EXPIRED'))),
    CONSTRAINT chk_ifj_runtime_command_type CHECK ((command_type in ('STOP','PAUSE','RESUME','RETRY')))
);

CREATE TABLE dbo.ifj_schedule (
    id bigint NOT NULL,
    target_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    target_id bigint NOT NULL,
    schedule_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    cron_expr varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    interval_sec int DEFAULT NULL,
    calendar_id bigint DEFAULT NULL,
    start_time datetime2(3) DEFAULT NULL,
    end_time datetime2(3) DEFAULT NULL,
    enabled bit NOT NULL DEFAULT 1,
    last_fire_time datetime2(3) DEFAULT NULL,
    next_fire_time datetime2(3) DEFAULT NULL,
    fire_claim_token varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    fire_claimed_at datetime2(3) DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    variables nvarchar(max) COLLATE Latin1_General_100_BIN2,
    CONSTRAINT pk_ifj_schedule PRIMARY KEY (id),
    CONSTRAINT chk_ifj_schedule_fire_claim CHECK ((((fire_claim_token is null) and (fire_claimed_at is null)) or ((fire_claim_token is not null) and (fire_claimed_at is not null))))
);

CREATE TABLE dbo.ifj_schema_capability (
    capability_key varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    capability_version int NOT NULL,
    enabled bit NOT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_schema_capability PRIMARY KEY (capability_key),
    CONSTRAINT chk_ifj_schema_cap_enabled CHECK ((enabled IN (0, 1))),
    CONSTRAINT chk_ifj_schema_cap_version CHECK ((capability_version > 0))
);

CREATE TABLE dbo.ifj_task (
    id bigint NOT NULL,
    name nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    plugin_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    plugin_config nvarchar(max) COLLATE Latin1_General_100_BIN2 NOT NULL,
    retry_times int NOT NULL DEFAULT 0,
    retry_interval int NOT NULL DEFAULT 0,
    timeout int DEFAULT NULL,
    enabled bit NOT NULL DEFAULT 1,
    description nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    concurrency_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    lock_key varchar(255) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    max_concurrency int DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    current_task_definition_id bigint DEFAULT NULL,
    current_revision bigint DEFAULT NULL,
    retired bit NOT NULL DEFAULT 0,
    retired_time datetime2(3) DEFAULT NULL,
    CONSTRAINT pk_ifj_task PRIMARY KEY (id),
    CONSTRAINT ck_ifj_task_current_pointer_pair CHECK ((((current_task_definition_id is null) and (current_revision is null)) or ((current_task_definition_id is not null) and (current_revision is not null)))),
    CONSTRAINT ck_ifj_task_current_revision_positive CHECK (((current_revision is null) or (current_revision > 0))),
    CONSTRAINT ck_ifj_task_retired_state CHECK ((((retired = 0) and (retired_time is null)) or ((retired = 1) and (retired_time is not null))))
);

CREATE TABLE dbo.ifj_task_definition (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    revision bigint NOT NULL,
    name nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    plugin_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    plugin_config nvarchar(max) COLLATE Latin1_General_100_BIN2 NOT NULL,
    retry_times int NOT NULL,
    retry_interval int NOT NULL,
    timeout int DEFAULT NULL,
    enabled bit NOT NULL,
    description nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    concurrency_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    lock_key varchar(255) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    max_concurrency int DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_task_definition PRIMARY KEY (id),
    CONSTRAINT uk_ifj_task_definition_task_revision UNIQUE (task_id,revision),
    CONSTRAINT ck_ifj_task_definition_revision CHECK ((revision > 0))
);

CREATE TABLE dbo.ifj_trigger (
    id bigint NOT NULL,
    schedule_id bigint DEFAULT NULL,
    target_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    target_id bigint NOT NULL,
    trigger_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    idempotency_key varchar(191) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    event_code varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    status varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    trigger_time datetime2(3) NOT NULL,
    message nvarchar(max) COLLATE Latin1_General_100_CI_AI_SC,
    error_code varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    message_key varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    message_args nvarchar(max) COLLATE Latin1_General_100_BIN2 DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    definition_id bigint DEFAULT NULL,
    task_definition_id bigint DEFAULT NULL,
    variables nvarchar(max) COLLATE Latin1_General_100_BIN2,
    event_id bigint DEFAULT NULL,
    event_binding_id bigint DEFAULT NULL,
    CONSTRAINT pk_ifj_trigger PRIMARY KEY (id),
    CONSTRAINT uk_ifj_trigger_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_ifj_trigger_status CHECK ((status in ('WAITING','RUNNING','SUCCESS','FAILED','STOPPED','TIMED_OUT','SKIPPED')))
);

CREATE TABLE dbo.ifj_trigger_work_item (
    trigger_id bigint NOT NULL,
    root_instance_id bigint DEFAULT NULL,
    status varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    available_at datetime2(3) NOT NULL,
    lease_owner varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    claim_token varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 DEFAULT NULL,
    lease_deadline datetime2(3) DEFAULT NULL,
    fence_token bigint NOT NULL DEFAULT 0,
    attempt_count int NOT NULL DEFAULT 0,
    max_attempts int NOT NULL,
    last_error nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC DEFAULT NULL,
    create_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_trigger_work_item PRIMARY KEY (trigger_id),
    CONSTRAINT chk_ifj_trigger_work_item_attempts CHECK (((attempt_count >= 0) and (max_attempts between 1 and 100))),
    CONSTRAINT chk_ifj_trigger_work_item_fence CHECK ((fence_token >= 0)),
    CONSTRAINT chk_ifj_trigger_work_item_lease CHECK ((((status = 'CLAIMED') and (lease_owner is not null) and (claim_token is not null) and (lease_deadline is not null)) or ((status <> 'CLAIMED') and (lease_owner is null) and (claim_token is null) and (lease_deadline is null)))),
    CONSTRAINT chk_ifj_trigger_work_item_status CHECK ((status in ('READY','CLAIMED','SUSPENDED','RETRY_WAIT','COMPLETED','DEAD')))
);

CREATE UNIQUE INDEX uk_ifj_concurrency_permit_token ON dbo.ifj_concurrency_permit (permit_token) WHERE permit_token IS NOT NULL;
CREATE UNIQUE INDEX uk_ifj_concurrency_permit_owner ON dbo.ifj_concurrency_permit (owner_instance_id) WHERE owner_instance_id IS NOT NULL;
CREATE UNIQUE INDEX uk_ifj_instance_parent_node ON dbo.ifj_instance (parent_instance_id,node_id) WHERE parent_instance_id IS NOT NULL AND node_id IS NOT NULL;
CREATE UNIQUE INDEX uk_ifj_instance_parent_definition_node ON dbo.ifj_instance (parent_instance_id,definition_node_id) WHERE parent_instance_id IS NOT NULL AND definition_node_id IS NOT NULL;
CREATE UNIQUE INDEX uk_ifj_maintenance_lease_token ON dbo.ifj_maintenance_lease (lease_token) WHERE lease_token IS NOT NULL;
CREATE UNIQUE INDEX uk_ifj_notification_outbox_claim_token ON dbo.ifj_notification_outbox (claim_token) WHERE claim_token IS NOT NULL;
CREATE UNIQUE INDEX uk_ifj_runtime_command_processing_token ON dbo.ifj_runtime_command (processing_token) WHERE processing_token IS NOT NULL;
CREATE UNIQUE INDEX uk_ifj_schedule_fire_claim_token ON dbo.ifj_schedule (fire_claim_token) WHERE fire_claim_token IS NOT NULL;
CREATE UNIQUE INDEX uk_ifj_trigger_event_binding ON dbo.ifj_trigger (event_id,event_binding_id) WHERE event_id IS NOT NULL AND event_binding_id IS NOT NULL;
CREATE UNIQUE INDEX uk_ifj_trigger_work_item_claim_token ON dbo.ifj_trigger_work_item (claim_token) WHERE claim_token IS NOT NULL;
CREATE UNIQUE INDEX uk_ifj_trigger_work_item_root ON dbo.ifj_trigger_work_item (root_instance_id) WHERE root_instance_id IS NOT NULL;
CREATE INDEX idx_ifj_app_run_status_heartbeat ON dbo.ifj_app_run (status,heartbeat_at,run_id);
CREATE INDEX idx_ifj_app_run_instance_heartbeat ON dbo.ifj_app_run (instance_name,status,heartbeat_at,run_id);
CREATE INDEX idx_ifj_cluster_state_expiry ON dbo.ifj_cluster_state (expires_at,state_key);
CREATE INDEX idx_ifj_concurrency_permit_lease ON dbo.ifj_concurrency_permit (lease_deadline,scope_key,slot_no);
CREATE INDEX idx_ifj_concurrency_permit_root ON dbo.ifj_concurrency_permit (owner_root_instance_id,lease_deadline);
CREATE INDEX idx_ifj_concurrency_policy_lock_key ON dbo.ifj_concurrency_policy (lock_key);
CREATE INDEX idx_ifj_etl_checkpoint_instance ON dbo.ifj_etl_checkpoint (last_instance_id);
CREATE INDEX idx_ifj_event_code_business ON dbo.ifj_event (event_code,business_key,arrived_at,id);
CREATE INDEX idx_ifj_event_code_correlation ON dbo.ifj_event (event_code,correlation_key,arrived_at,id);
CREATE INDEX idx_ifj_event_arrived ON dbo.ifj_event (arrived_at,id);
CREATE INDEX idx_ifj_event_binding_event ON dbo.ifj_event_binding (event_code,enabled);
CREATE INDEX fk_ifj_event_wait_event ON dbo.ifj_event_wait (matched_event_id);
CREATE INDEX idx_ifj_event_wait_match_business ON dbo.ifj_event_wait (status,event_code,business_key,accept_after,id);
CREATE INDEX idx_ifj_event_wait_match_correlation ON dbo.ifj_event_wait (status,event_code,correlation_key,accept_after,id);
CREATE INDEX idx_ifj_event_wait_root_status ON dbo.ifj_event_wait (root_instance_id,status);
CREATE INDEX idx_ifj_event_wait_timeout ON dbo.ifj_event_wait (status,timeout_at,id);
CREATE INDEX idx_ifj_wait_operator_lookup ON dbo.ifj_event_wait_operator (operator_id,wait_id);
CREATE INDEX fk_ifj_instance_node ON dbo.ifj_instance (node_id);
CREATE INDEX idx_ifj_instance_root_status ON dbo.ifj_instance (root_instance_id,status);
CREATE INDEX idx_ifj_instance_target ON dbo.ifj_instance (target_type,target_id);
CREATE INDEX idx_ifj_instance_trigger_id ON dbo.ifj_instance (trigger_id);
CREATE INDEX fk_ifj_instance_definition_node ON dbo.ifj_instance (definition_node_id);
CREATE INDEX idx_ifj_instance_definition_status ON dbo.ifj_instance (definition_id,status);
CREATE INDEX idx_ifj_instance_task_definition_status ON dbo.ifj_instance (task_definition_id,status);
CREATE INDEX idx_ifj_instance_status_update ON dbo.ifj_instance (status,update_time,id);
CREATE INDEX idx_ifj_instance_error_update ON dbo.ifj_instance (error_code,update_time,id);
CREATE INDEX idx_ifj_instance_root_timeout ON dbo.ifj_instance (parent_instance_id,status,timeout_at,id);
CREATE INDEX idx_ifj_instance_log_instance_time ON dbo.ifj_instance_log (instance_id,log_time);
CREATE INDEX idx_ifj_job_enabled ON dbo.ifj_job (enabled);
CREATE INDEX idx_ifj_job_current_definition_id ON dbo.ifj_job (current_definition_id);
CREATE INDEX idx_ifj_job_definition_job_time ON dbo.ifj_job_definition (job_id,create_time,id);
CREATE INDEX fk_ifj_job_definition_edge_from_node ON dbo.ifj_job_definition_edge (from_node_id);
CREATE INDEX idx_ifj_job_definition_edge_to_node ON dbo.ifj_job_definition_edge (to_node_id);
CREATE INDEX idx_ifj_job_definition_node_ref ON dbo.ifj_job_definition_node (node_type,ref_id);
CREATE INDEX idx_ifj_job_definition_node_ref_job_definition ON dbo.ifj_job_definition_node (ref_definition_id);
CREATE INDEX idx_ifj_job_definition_node_ref_task_definition ON dbo.ifj_job_definition_node (ref_task_definition_id);
CREATE INDEX idx_ifj_job_definition_node_policy_definition ON dbo.ifj_job_definition_node_policy (definition_id);
CREATE INDEX fk_ifj_job_edge_from_node ON dbo.ifj_job_edge (from_node_id);
CREATE INDEX idx_ifj_job_edge_to_node_id ON dbo.ifj_job_edge (to_node_id);
CREATE INDEX idx_ifj_job_node_job_id ON dbo.ifj_job_node (job_id);
CREATE INDEX idx_ifj_job_node_ref ON dbo.ifj_job_node (node_type,ref_id);
CREATE INDEX idx_ifj_maintenance_lease_deadline ON dbo.ifj_maintenance_lease (lease_deadline,lease_name);
CREATE INDEX idx_ifj_notification_outbox_dispatch ON dbo.ifj_notification_outbox (available_at,id) INCLUDE (status,lease_deadline,attempt_count,max_attempts);
CREATE INDEX idx_ifj_notification_outbox_lease ON dbo.ifj_notification_outbox (status,lease_deadline,id);
CREATE INDEX idx_ifj_notification_outbox_instance ON dbo.ifj_notification_outbox (instance_id,status,id);
CREATE INDEX idx_ifj_notification_outbox_created ON dbo.ifj_notification_outbox (create_time,id);
CREATE INDEX fk_ifj_notify_binding_config ON dbo.ifj_notify_binding (notify_config_id);
CREATE INDEX idx_ifj_notify_binding_target ON dbo.ifj_notify_binding (target_type,target_id);
CREATE INDEX idx_ifj_notify_config_type_enabled ON dbo.ifj_notify_config (type,enabled);
CREATE INDEX idx_ifj_notify_send_log_create_time ON dbo.ifj_notify_send_log (create_time,id);
CREATE INDEX idx_ifj_notify_send_log_config_time ON dbo.ifj_notify_send_log (notify_config_id,create_time);
CREATE INDEX idx_ifj_notify_send_log_target_time ON dbo.ifj_notify_send_log (target_type,target_id,create_time);
CREATE INDEX idx_ifj_notify_send_log_instance_time ON dbo.ifj_notify_send_log (instance_id,create_time);
CREATE INDEX idx_ifj_notify_send_log_result_time ON dbo.ifj_notify_send_log (result,create_time);
CREATE INDEX idx_ifj_resource_type_enabled ON dbo.ifj_resource (type,enabled);
CREATE INDEX fk_ifj_runtime_command_instance ON dbo.ifj_runtime_command (instance_id);
CREATE INDEX idx_ifj_runtime_command_dispatch ON dbo.ifj_runtime_command (trigger_id,create_time,request_id) INCLUDE (status,available_at,expires_at,processing_fence);
CREATE INDEX idx_ifj_runtime_command_root ON dbo.ifj_runtime_command (root_instance_id,create_time);
CREATE INDEX idx_ifj_runtime_command_expiration ON dbo.ifj_runtime_command (status,expires_at,request_id);
CREATE INDEX fk_ifj_schedule_calendar ON dbo.ifj_schedule (calendar_id);
CREATE INDEX idx_ifj_schedule_target ON dbo.ifj_schedule (target_type,target_id);
CREATE INDEX idx_ifj_schedule_fire ON dbo.ifj_schedule (enabled,next_fire_time);
CREATE INDEX idx_ifj_task_enabled_plugin_type ON dbo.ifj_task (enabled,plugin_type);
CREATE INDEX idx_ifj_task_current_definition_id ON dbo.ifj_task (current_task_definition_id);
CREATE INDEX idx_ifj_task_definition_task_time ON dbo.ifj_task_definition (task_id,create_time,id);
CREATE INDEX fk_ifj_trigger_schedule ON dbo.ifj_trigger (schedule_id);
CREATE INDEX idx_ifj_trigger_status_time ON dbo.ifj_trigger (status,trigger_time);
CREATE INDEX idx_ifj_trigger_target ON dbo.ifj_trigger (target_type,target_id);
CREATE INDEX idx_ifj_trigger_definition_status ON dbo.ifj_trigger (definition_id,status);
CREATE INDEX idx_ifj_trigger_task_definition_status ON dbo.ifj_trigger (task_definition_id,status);
CREATE INDEX fk_ifj_trigger_event_binding ON dbo.ifj_trigger (event_binding_id);
CREATE INDEX idx_ifj_trigger_event ON dbo.ifj_trigger (event_id);
CREATE INDEX idx_ifj_trigger_status_update ON dbo.ifj_trigger (status,update_time,id);
CREATE INDEX idx_ifj_trigger_error_update ON dbo.ifj_trigger (error_code,update_time,id);
CREATE INDEX idx_ifj_trigger_work_item_dispatch ON dbo.ifj_trigger_work_item (available_at,trigger_id) INCLUDE (status,lease_deadline,attempt_count,max_attempts);
CREATE INDEX idx_ifj_trigger_work_item_owner ON dbo.ifj_trigger_work_item (lease_owner,lease_deadline,trigger_id);

ALTER TABLE dbo.ifj_calendar_exception ADD CONSTRAINT fk_ifj_calendar_exception_calendar FOREIGN KEY (calendar_id) REFERENCES dbo.ifj_calendar (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_event_wait ADD CONSTRAINT fk_ifj_event_wait_event FOREIGN KEY (matched_event_id) REFERENCES dbo.ifj_event (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_event_wait ADD CONSTRAINT fk_ifj_event_wait_instance FOREIGN KEY (instance_id) REFERENCES dbo.ifj_instance (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_event_wait ADD CONSTRAINT fk_ifj_event_wait_root FOREIGN KEY (root_instance_id) REFERENCES dbo.ifj_instance (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_event_wait_operator ADD CONSTRAINT fk_ifj_wait_operator_wait FOREIGN KEY (wait_id) REFERENCES dbo.ifj_event_wait (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_instance ADD CONSTRAINT fk_ifj_instance_definition FOREIGN KEY (definition_id) REFERENCES dbo.ifj_job_definition (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_instance ADD CONSTRAINT fk_ifj_instance_definition_node FOREIGN KEY (definition_node_id) REFERENCES dbo.ifj_job_definition_node (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_instance ADD CONSTRAINT fk_ifj_instance_node FOREIGN KEY (node_id) REFERENCES dbo.ifj_job_node (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_instance ADD CONSTRAINT fk_ifj_instance_parent FOREIGN KEY (parent_instance_id) REFERENCES dbo.ifj_instance (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_instance ADD CONSTRAINT fk_ifj_instance_root FOREIGN KEY (root_instance_id) REFERENCES dbo.ifj_instance (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_instance ADD CONSTRAINT fk_ifj_instance_task_definition FOREIGN KEY (task_definition_id) REFERENCES dbo.ifj_task_definition (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_instance ADD CONSTRAINT fk_ifj_instance_trigger FOREIGN KEY (trigger_id) REFERENCES dbo.ifj_trigger (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_instance_log ADD CONSTRAINT fk_ifj_instance_log_instance FOREIGN KEY (instance_id) REFERENCES dbo.ifj_instance (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_definition ADD CONSTRAINT fk_ifj_job_definition_job FOREIGN KEY (job_id) REFERENCES dbo.ifj_job (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_definition_edge ADD CONSTRAINT fk_ifj_job_definition_edge_definition FOREIGN KEY (definition_id) REFERENCES dbo.ifj_job_definition (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_definition_edge ADD CONSTRAINT fk_ifj_job_definition_edge_from_node FOREIGN KEY (from_node_id) REFERENCES dbo.ifj_job_definition_node (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_definition_edge ADD CONSTRAINT fk_ifj_job_definition_edge_to_node FOREIGN KEY (to_node_id) REFERENCES dbo.ifj_job_definition_node (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_definition_node ADD CONSTRAINT fk_ifj_job_definition_node_definition FOREIGN KEY (definition_id) REFERENCES dbo.ifj_job_definition (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_definition_node ADD CONSTRAINT fk_ifj_job_definition_node_ref_job_definition FOREIGN KEY (ref_definition_id) REFERENCES dbo.ifj_job_definition (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_definition_node ADD CONSTRAINT fk_ifj_job_definition_node_ref_task_definition FOREIGN KEY (ref_task_definition_id) REFERENCES dbo.ifj_task_definition (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_definition_node_policy ADD CONSTRAINT fk_ifj_job_definition_node_policy_definition FOREIGN KEY (definition_id) REFERENCES dbo.ifj_job_definition (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_definition_node_policy ADD CONSTRAINT fk_ifj_job_definition_node_policy_node FOREIGN KEY (node_id) REFERENCES dbo.ifj_job_definition_node (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_edge ADD CONSTRAINT fk_ifj_job_edge_from_node FOREIGN KEY (from_node_id) REFERENCES dbo.ifj_job_node (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_edge ADD CONSTRAINT fk_ifj_job_edge_job FOREIGN KEY (job_id) REFERENCES dbo.ifj_job (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_edge ADD CONSTRAINT fk_ifj_job_edge_to_node FOREIGN KEY (to_node_id) REFERENCES dbo.ifj_job_node (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_node ADD CONSTRAINT fk_ifj_job_node_job FOREIGN KEY (job_id) REFERENCES dbo.ifj_job (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_job_node_policy ADD CONSTRAINT fk_ifj_job_node_policy_node FOREIGN KEY (node_id) REFERENCES dbo.ifj_job_node (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_notify_binding ADD CONSTRAINT fk_ifj_notify_binding_config FOREIGN KEY (notify_config_id) REFERENCES dbo.ifj_notify_config (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_runtime_command ADD CONSTRAINT fk_ifj_runtime_command_instance FOREIGN KEY (instance_id) REFERENCES dbo.ifj_instance (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_runtime_command ADD CONSTRAINT fk_ifj_runtime_command_root FOREIGN KEY (root_instance_id) REFERENCES dbo.ifj_instance (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_runtime_command ADD CONSTRAINT fk_ifj_runtime_command_trigger FOREIGN KEY (trigger_id) REFERENCES dbo.ifj_trigger (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_schedule ADD CONSTRAINT fk_ifj_schedule_calendar FOREIGN KEY (calendar_id) REFERENCES dbo.ifj_calendar (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_task_definition ADD CONSTRAINT fk_ifj_task_definition_task FOREIGN KEY (task_id) REFERENCES dbo.ifj_task (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_trigger ADD CONSTRAINT fk_ifj_trigger_definition FOREIGN KEY (definition_id) REFERENCES dbo.ifj_job_definition (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_trigger ADD CONSTRAINT fk_ifj_trigger_event FOREIGN KEY (event_id) REFERENCES dbo.ifj_event (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_trigger ADD CONSTRAINT fk_ifj_trigger_event_binding FOREIGN KEY (event_binding_id) REFERENCES dbo.ifj_event_binding (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_trigger ADD CONSTRAINT fk_ifj_trigger_schedule FOREIGN KEY (schedule_id) REFERENCES dbo.ifj_schedule (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_trigger ADD CONSTRAINT fk_ifj_trigger_task_definition FOREIGN KEY (task_definition_id) REFERENCES dbo.ifj_task_definition (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_trigger_work_item ADD CONSTRAINT fk_ifj_trigger_work_item_root FOREIGN KEY (root_instance_id) REFERENCES dbo.ifj_instance (id) ON DELETE NO ACTION;
ALTER TABLE dbo.ifj_trigger_work_item ADD CONSTRAINT fk_ifj_trigger_work_item_trigger FOREIGN KEY (trigger_id) REFERENCES dbo.ifj_trigger (id) ON DELETE NO ACTION;

GO
CREATE TRIGGER dbo.trg_ifj_calendar_touch ON dbo.ifj_calendar AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_calendar AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_calendar_exception_touch ON dbo.ifj_calendar_exception AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_calendar_exception AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_concurrency_policy_touch ON dbo.ifj_concurrency_policy AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_concurrency_policy AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_event_binding_touch ON dbo.ifj_event_binding AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_event_binding AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_event_wait_touch ON dbo.ifj_event_wait AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_event_wait AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_instance_touch ON dbo.ifj_instance AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_instance AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_job_touch ON dbo.ifj_job AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_job AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_job_edge_touch ON dbo.ifj_job_edge AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_job_edge AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_job_node_touch ON dbo.ifj_job_node AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_job_node AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_job_node_policy_touch ON dbo.ifj_job_node_policy AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_job_node_policy AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_notify_binding_touch ON dbo.ifj_notify_binding AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_notify_binding AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_notify_config_touch ON dbo.ifj_notify_config AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_notify_config AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_notify_send_log_touch ON dbo.ifj_notify_send_log AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_notify_send_log AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_resource_touch ON dbo.ifj_resource AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_resource AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_schedule_touch ON dbo.ifj_schedule AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_schedule AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_schema_capability_touch ON dbo.ifj_schema_capability AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_schema_capability AS target
      JOIN inserted ON target.capability_key = inserted.capability_key;
END;
GO
CREATE TRIGGER dbo.trg_ifj_task_touch ON dbo.ifj_task AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_task AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO
CREATE TRIGGER dbo.trg_ifj_trigger_touch ON dbo.ifj_trigger AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE target
       SET update_time = SYSUTCDATETIME()
      FROM dbo.ifj_trigger AS target
      JOIN inserted ON target.id = inserted.id;
END;
GO

INSERT INTO dbo.ifj_id_segment (segment_key, next_value, segment_size, update_time)
VALUES ('platform-primary-id', 1, 1000, SYSUTCDATETIME());

INSERT INTO dbo.ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES
    ('platform-schema', 19, 1, SYSUTCDATETIME(), SYSUTCDATETIME()),
    ('event-wait-operator-relation', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME()),
    ('stable-id-segment-seed', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
