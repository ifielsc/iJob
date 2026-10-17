-- SQLite 3.35+ standalone platform baseline aligned with the V19 logical schema.
-- The database must be an absolute local file protected by the iJob sidecar lock.

CREATE TABLE ifj_app_run (
    run_id TEXT NOT NULL,
    instance_name TEXT DEFAULT NULL,
    runtime_mode TEXT NOT NULL DEFAULT 'STANDALONE',
    roles TEXT NOT NULL DEFAULT 'API,SCHEDULER,WORKER',
    status TEXT NOT NULL,
    application_version TEXT DEFAULT NULL,
    host_name TEXT DEFAULT NULL,
    process_id INTEGER DEFAULT NULL,
    started_at TEXT NOT NULL,
    heartbeat_at TEXT NOT NULL,
    stopped_at TEXT DEFAULT NULL,
    CONSTRAINT pk_ifj_app_run PRIMARY KEY (run_id),
    CONSTRAINT chk_ifj_app_run_runtime_mode CHECK ((runtime_mode in ('DEVELOPMENT','STANDALONE','CLUSTER'))),
    CONSTRAINT chk_ifj_app_run_status CHECK ((status in ('STARTING','RUNNING','DRAINING','STOPPED','CRASHED'))),
    CONSTRAINT chk_sqlite_ifj_app_run_run_id_len CHECK (run_id IS NULL OR length(run_id) <= 64),
    CONSTRAINT chk_sqlite_ifj_app_run_instance_name_len CHECK (instance_name IS NULL OR length(instance_name) <= 128),
    CONSTRAINT chk_sqlite_ifj_app_run_runtime_mode_len CHECK (runtime_mode IS NULL OR length(runtime_mode) <= 16),
    CONSTRAINT chk_sqlite_ifj_app_run_roles_len CHECK (roles IS NULL OR length(roles) <= 64),
    CONSTRAINT chk_sqlite_ifj_app_run_status_len CHECK (status IS NULL OR length(status) <= 16),
    CONSTRAINT chk_sqlite_ifj_app_run_application_version_len CHECK (application_version IS NULL OR length(application_version) <= 64),
    CONSTRAINT chk_sqlite_ifj_app_run_host_name_len CHECK (host_name IS NULL OR length(host_name) <= 255),
    CONSTRAINT chk_sqlite_ifj_app_run_process_id_id CHECK (process_id IS NULL OR (process_id > 0 AND process_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_app_run_started_at_timestamp CHECK (started_at IS NULL OR (length(started_at) = 23 AND started_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_app_run_heartbeat_at_timestamp CHECK (heartbeat_at IS NULL OR (length(heartbeat_at) = 23 AND heartbeat_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_app_run_stopped_at_timestamp CHECK (stopped_at IS NULL OR (length(stopped_at) = 23 AND stopped_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_calendar (
    id INTEGER NOT NULL,
    name TEXT NOT NULL,
    workdays_mask INTEGER NOT NULL,
    description TEXT DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_calendar PRIMARY KEY (id),
    CONSTRAINT uk_ifj_calendar_name UNIQUE (name),
    CONSTRAINT chk_sqlite_ifj_calendar_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_calendar_name_len CHECK (name IS NULL OR length(name) <= 128),
    CONSTRAINT chk_sqlite_ifj_calendar_description_len CHECK (description IS NULL OR length(description) <= 500),
    CONSTRAINT chk_sqlite_ifj_calendar_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_calendar_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_calendar_exception (
    id INTEGER NOT NULL,
    calendar_id INTEGER NOT NULL,
    date TEXT NOT NULL,
    type TEXT NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_calendar_exception PRIMARY KEY (id),
    CONSTRAINT uk_ifj_calendar_exception_date UNIQUE (calendar_id,date),
    CONSTRAINT chk_sqlite_ifj_calendar_exception_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_calendar_exception_calendar_id_id CHECK (calendar_id IS NULL OR (calendar_id > 0 AND calendar_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_calendar_exception_date_date CHECK (date IS NULL OR (length(date) = 10 AND date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_calendar_exception_type_len CHECK (type IS NULL OR length(type) <= 32),
    CONSTRAINT chk_sqlite_ifj_calendar_exception_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_calendar_exception_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_calendar_exception_calendar FOREIGN KEY (calendar_id) REFERENCES ifj_calendar (id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE TABLE ifj_cluster_state (
    state_key TEXT NOT NULL,
    state_value text NOT NULL,
    expires_at TEXT DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_cluster_state PRIMARY KEY (state_key),
    CONSTRAINT chk_sqlite_ifj_cluster_state_state_key_len CHECK (state_key IS NULL OR length(state_key) <= 512),
    CONSTRAINT chk_sqlite_ifj_cluster_state_expires_at_timestamp CHECK (expires_at IS NULL OR (length(expires_at) = 23 AND expires_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_cluster_state_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_cluster_state_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_concurrency_permit (
    scope_key TEXT NOT NULL,
    slot_no INTEGER NOT NULL,
    owner_instance_id INTEGER DEFAULT NULL,
    owner_root_instance_id INTEGER DEFAULT NULL,
    owner_trigger_id INTEGER DEFAULT NULL,
    lease_owner TEXT DEFAULT NULL,
    permit_token TEXT DEFAULT NULL,
    permit_fence INTEGER NOT NULL DEFAULT 0,
    execution_fence INTEGER DEFAULT NULL,
    lease_deadline TEXT DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_concurrency_permit PRIMARY KEY (scope_key,slot_no),
    CONSTRAINT uk_ifj_concurrency_permit_token UNIQUE (permit_token),
    CONSTRAINT uk_ifj_concurrency_permit_owner UNIQUE (owner_instance_id),
    CONSTRAINT chk_ifj_concurrency_permit_fence CHECK ((permit_fence >= 0)),
    CONSTRAINT chk_ifj_concurrency_permit_lease CHECK ((((owner_instance_id is null) and (owner_root_instance_id is null) and (owner_trigger_id is null) and (lease_owner is null) and (permit_token is null) and (execution_fence is null) and (lease_deadline is null)) or ((owner_instance_id is not null) and (owner_root_instance_id is not null) and (owner_trigger_id is not null) and (lease_owner is not null) and (permit_token is not null) and (execution_fence is not null) and (lease_deadline is not null)))),
    CONSTRAINT chk_ifj_concurrency_permit_slot CHECK ((slot_no > 0)),
    CONSTRAINT chk_sqlite_ifj_concurrency_permit_scope_key_len CHECK (scope_key IS NULL OR length(scope_key) <= 191),
    CONSTRAINT chk_sqlite_ifj_concurrency_permit_owner_instance_id_id CHECK (owner_instance_id IS NULL OR (owner_instance_id > 0 AND owner_instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_concurrency_permit_owner_root_instance_id_id CHECK (owner_root_instance_id IS NULL OR (owner_root_instance_id > 0 AND owner_root_instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_concurrency_permit_owner_trigger_id_id CHECK (owner_trigger_id IS NULL OR (owner_trigger_id > 0 AND owner_trigger_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_concurrency_permit_lease_owner_len CHECK (lease_owner IS NULL OR length(lease_owner) <= 128),
    CONSTRAINT chk_sqlite_ifj_concurrency_permit_permit_token_len CHECK (permit_token IS NULL OR length(permit_token) <= 36),
    CONSTRAINT chk_sqlite_ifj_concurrency_permit_lease_deadline_timestamp CHECK (lease_deadline IS NULL OR (length(lease_deadline) = 23 AND lease_deadline GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_concurrency_permit_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_concurrency_permit_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_concurrency_policy (
    id INTEGER NOT NULL,
    target_id INTEGER NOT NULL,
    target_type TEXT NOT NULL,
    concurrency_type TEXT NOT NULL,
    lock_key TEXT DEFAULT NULL,
    max_concurrency INTEGER DEFAULT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_concurrency_policy PRIMARY KEY (id),
    CONSTRAINT uk_ifj_concurrency_policy_target UNIQUE (target_type,target_id),
    CONSTRAINT chk_sqlite_ifj_concurrency_policy_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_concurrency_policy_target_id_id CHECK (target_id IS NULL OR (target_id > 0 AND target_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_concurrency_policy_target_type_len CHECK (target_type IS NULL OR length(target_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_concurrency_policy_concurrency_type_len CHECK (concurrency_type IS NULL OR length(concurrency_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_concurrency_policy_lock_key_len CHECK (lock_key IS NULL OR length(lock_key) <= 255),
    CONSTRAINT chk_sqlite_ifj_concurrency_policy_enabled_bool CHECK (enabled IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_concurrency_policy_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_concurrency_policy_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_etl_checkpoint (
    task_id INTEGER NOT NULL,
    checkpoint_key TEXT NOT NULL,
    source_fingerprint TEXT NOT NULL,
    watermark_type TEXT NOT NULL,
    watermark_value text,
    tie_breaker_value text,
    last_instance_id INTEGER DEFAULT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_etl_checkpoint PRIMARY KEY (task_id,checkpoint_key),
    CONSTRAINT ck_ifj_etl_checkpoint_task CHECK ((task_id > 0)),
    CONSTRAINT ck_ifj_etl_checkpoint_version CHECK ((version >= 0)),
    CONSTRAINT chk_sqlite_ifj_etl_checkpoint_task_id_id CHECK (task_id IS NULL OR (task_id > 0 AND task_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_etl_checkpoint_checkpoint_key_len CHECK (checkpoint_key IS NULL OR length(checkpoint_key) <= 128),
    CONSTRAINT chk_sqlite_ifj_etl_checkpoint_source_fingerprint_len CHECK (source_fingerprint IS NULL OR length(source_fingerprint) <= 64),
    CONSTRAINT chk_sqlite_ifj_etl_checkpoint_watermark_type_len CHECK (watermark_type IS NULL OR length(watermark_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_etl_checkpoint_last_instance_id_id CHECK (last_instance_id IS NULL OR (last_instance_id > 0 AND last_instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_etl_checkpoint_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_etl_checkpoint_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_event (
    id INTEGER NOT NULL,
    event_code TEXT NOT NULL,
    source_type TEXT NOT NULL,
    source_key TEXT NOT NULL,
    business_key TEXT DEFAULT NULL,
    correlation_key TEXT DEFAULT NULL,
    idempotency_key TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    arrived_at TEXT NOT NULL,
    summary TEXT DEFAULT NULL,
    payload text,
    payload_digest TEXT DEFAULT NULL,
    payload_bytes INTEGER NOT NULL DEFAULT 0,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_event PRIMARY KEY (id),
    CONSTRAINT uk_ifj_event_source_idempotency UNIQUE (source_key,idempotency_key),
    CONSTRAINT chk_ifj_event_payload_bytes CHECK ((payload_bytes >= 0)),
    CONSTRAINT chk_sqlite_ifj_event_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_event_event_code_len CHECK (event_code IS NULL OR length(event_code) <= 128),
    CONSTRAINT chk_sqlite_ifj_event_source_type_len CHECK (source_type IS NULL OR length(source_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_event_source_key_len CHECK (source_key IS NULL OR length(source_key) <= 128),
    CONSTRAINT chk_sqlite_ifj_event_business_key_len CHECK (business_key IS NULL OR length(business_key) <= 256),
    CONSTRAINT chk_sqlite_ifj_event_correlation_key_len CHECK (correlation_key IS NULL OR length(correlation_key) <= 256),
    CONSTRAINT chk_sqlite_ifj_event_idempotency_key_len CHECK (idempotency_key IS NULL OR length(idempotency_key) <= 256),
    CONSTRAINT chk_sqlite_ifj_event_occurred_at_timestamp CHECK (occurred_at IS NULL OR (length(occurred_at) = 23 AND occurred_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_event_arrived_at_timestamp CHECK (arrived_at IS NULL OR (length(arrived_at) = 23 AND arrived_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_event_summary_len CHECK (summary IS NULL OR length(summary) <= 500),
    CONSTRAINT chk_sqlite_ifj_event_payload_digest_len CHECK (payload_digest IS NULL OR length(payload_digest) <= 64),
    CONSTRAINT chk_sqlite_ifj_event_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_event_binding (
    id INTEGER NOT NULL,
    target_type TEXT NOT NULL,
    target_id INTEGER NOT NULL,
    event_code TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_event_binding PRIMARY KEY (id),
    CONSTRAINT uk_ifj_event_binding_target UNIQUE (target_type,target_id,event_code),
    CONSTRAINT chk_sqlite_ifj_event_binding_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_event_binding_target_type_len CHECK (target_type IS NULL OR length(target_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_event_binding_target_id_id CHECK (target_id IS NULL OR (target_id > 0 AND target_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_event_binding_event_code_len CHECK (event_code IS NULL OR length(event_code) <= 128),
    CONSTRAINT chk_sqlite_ifj_event_binding_enabled_bool CHECK (enabled IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_event_binding_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_event_binding_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_event_wait (
    id INTEGER NOT NULL,
    instance_id INTEGER NOT NULL,
    root_instance_id INTEGER NOT NULL,
    attempt_no INTEGER NOT NULL,
    wait_type TEXT NOT NULL,
    status TEXT NOT NULL,
    event_code TEXT DEFAULT NULL,
    business_key TEXT DEFAULT NULL,
    correlation_key TEXT DEFAULT NULL,
    accept_after TEXT NOT NULL,
    timeout_at TEXT DEFAULT NULL,
    matched_event_id INTEGER DEFAULT NULL,
    allowed_operator_ids text,
    decision_by TEXT DEFAULT NULL,
    decision_comment TEXT DEFAULT NULL,
    resolved_at TEXT DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_event_wait PRIMARY KEY (id),
    CONSTRAINT uk_ifj_event_wait_attempt UNIQUE (instance_id,attempt_no),
    CONSTRAINT chk_ifj_event_wait_attempt CHECK ((attempt_no >= 0)),
    CONSTRAINT chk_ifj_event_wait_status CHECK ((status in ('WAITING','MATCHED','APPROVED','REJECTED','TIMED_OUT','WITHDRAWN','STOPPED'))),
    CONSTRAINT chk_ifj_event_wait_type CHECK ((wait_type in ('EVENT','APPROVAL'))),
    CONSTRAINT chk_sqlite_ifj_event_wait_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_event_wait_instance_id_id CHECK (instance_id IS NULL OR (instance_id > 0 AND instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_event_wait_root_instance_id_id CHECK (root_instance_id IS NULL OR (root_instance_id > 0 AND root_instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_event_wait_wait_type_len CHECK (wait_type IS NULL OR length(wait_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_event_wait_status_len CHECK (status IS NULL OR length(status) <= 32),
    CONSTRAINT chk_sqlite_ifj_event_wait_event_code_len CHECK (event_code IS NULL OR length(event_code) <= 128),
    CONSTRAINT chk_sqlite_ifj_event_wait_business_key_len CHECK (business_key IS NULL OR length(business_key) <= 256),
    CONSTRAINT chk_sqlite_ifj_event_wait_correlation_key_len CHECK (correlation_key IS NULL OR length(correlation_key) <= 256),
    CONSTRAINT chk_sqlite_ifj_event_wait_accept_after_timestamp CHECK (accept_after IS NULL OR (length(accept_after) = 23 AND accept_after GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_event_wait_timeout_at_timestamp CHECK (timeout_at IS NULL OR (length(timeout_at) = 23 AND timeout_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_event_wait_matched_event_id_id CHECK (matched_event_id IS NULL OR (matched_event_id > 0 AND matched_event_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_event_wait_decision_by_len CHECK (decision_by IS NULL OR length(decision_by) <= 128),
    CONSTRAINT chk_sqlite_ifj_event_wait_decision_comment_len CHECK (decision_comment IS NULL OR length(decision_comment) <= 500),
    CONSTRAINT chk_sqlite_ifj_event_wait_resolved_at_timestamp CHECK (resolved_at IS NULL OR (length(resolved_at) = 23 AND resolved_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_event_wait_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_event_wait_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_event_wait_event FOREIGN KEY (matched_event_id) REFERENCES ifj_event (id) ON DELETE SET NULL,
    CONSTRAINT fk_ifj_event_wait_instance FOREIGN KEY (instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_event_wait_root FOREIGN KEY (root_instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE TABLE ifj_event_wait_operator (
    wait_id INTEGER NOT NULL,
    operator_id TEXT NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_event_wait_operator PRIMARY KEY (wait_id,operator_id),
    CONSTRAINT chk_ifj_wait_operator_id CHECK ((length(operator_id) > 0)),
    CONSTRAINT chk_sqlite_ifj_event_wait_operator_wait_id_id CHECK (wait_id IS NULL OR (wait_id > 0 AND wait_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_event_wait_operator_operator_id_len CHECK (operator_id IS NULL OR length(operator_id) <= 128),
    CONSTRAINT chk_sqlite_ifj_event_wait_operator_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_wait_operator_wait FOREIGN KEY (wait_id) REFERENCES ifj_event_wait (id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE TABLE ifj_id_segment (
    segment_key TEXT NOT NULL,
    next_value INTEGER NOT NULL,
    segment_size INTEGER NOT NULL,
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_id_segment PRIMARY KEY (segment_key),
    CONSTRAINT chk_ifj_id_segment_next_value CHECK ((next_value > 0)),
    CONSTRAINT chk_ifj_id_segment_size CHECK ((segment_size between 1 and 100000)),
    CONSTRAINT chk_sqlite_ifj_id_segment_segment_key_len CHECK (segment_key IS NULL OR length(segment_key) <= 64),
    CONSTRAINT chk_sqlite_ifj_id_segment_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_instance (
    id INTEGER NOT NULL,
    node_id INTEGER DEFAULT NULL,
    parent_instance_id INTEGER DEFAULT NULL,
    root_instance_id INTEGER DEFAULT NULL,
    execution_fence INTEGER NOT NULL DEFAULT 0,
    target_type TEXT NOT NULL,
    target_id INTEGER NOT NULL,
    trigger_type TEXT DEFAULT NULL,
    trigger_id INTEGER DEFAULT NULL,
    status TEXT NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    retry_count INTEGER NOT NULL DEFAULT 0,
    start_time TEXT DEFAULT NULL,
    timeout_at TEXT DEFAULT NULL,
    end_time TEXT DEFAULT NULL,
    message text,
    error_code TEXT DEFAULT NULL,
    message_key TEXT DEFAULT NULL,
    message_args text DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    definition_id INTEGER DEFAULT NULL,
    definition_node_id INTEGER DEFAULT NULL,
    task_definition_id INTEGER DEFAULT NULL,
    input_variables text,
    output_variables text,
    CONSTRAINT pk_ifj_instance PRIMARY KEY (id),
    CONSTRAINT uk_ifj_instance_parent_node UNIQUE (parent_instance_id,node_id),
    CONSTRAINT uk_ifj_instance_parent_definition_node UNIQUE (parent_instance_id,definition_node_id),
    CONSTRAINT chk_ifj_instance_execution_fence CHECK ((execution_fence >= 0)),
    CONSTRAINT chk_ifj_instance_progress CHECK ((progress between 0 and 100)),
    CONSTRAINT chk_ifj_instance_retry_count CHECK ((retry_count >= 0)),
    CONSTRAINT chk_ifj_instance_status CHECK ((status in ('WAITING','RUNNING','PAUSED','WAITING_EVENT','SUCCESS','FAILED','STOPPED','TIMED_OUT','SKIPPED'))),
    CONSTRAINT chk_sqlite_ifj_instance_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_node_id_id CHECK (node_id IS NULL OR (node_id > 0 AND node_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_parent_instance_id_id CHECK (parent_instance_id IS NULL OR (parent_instance_id > 0 AND parent_instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_root_instance_id_id CHECK (root_instance_id IS NULL OR (root_instance_id > 0 AND root_instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_target_type_len CHECK (target_type IS NULL OR length(target_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_instance_target_id_id CHECK (target_id IS NULL OR (target_id > 0 AND target_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_trigger_type_len CHECK (trigger_type IS NULL OR length(trigger_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_instance_trigger_id_id CHECK (trigger_id IS NULL OR (trigger_id > 0 AND trigger_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_status_len CHECK (status IS NULL OR length(status) <= 32),
    CONSTRAINT chk_sqlite_ifj_instance_start_time_timestamp CHECK (start_time IS NULL OR (length(start_time) = 23 AND start_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_instance_timeout_at_timestamp CHECK (timeout_at IS NULL OR (length(timeout_at) = 23 AND timeout_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_instance_end_time_timestamp CHECK (end_time IS NULL OR (length(end_time) = 23 AND end_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_instance_error_code_len CHECK (error_code IS NULL OR length(error_code) <= 64),
    CONSTRAINT chk_sqlite_ifj_instance_message_key_len CHECK (message_key IS NULL OR length(message_key) <= 128),
    CONSTRAINT chk_sqlite_ifj_instance_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_instance_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_instance_definition_id_id CHECK (definition_id IS NULL OR (definition_id > 0 AND definition_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_definition_node_id_id CHECK (definition_node_id IS NULL OR (definition_node_id > 0 AND definition_node_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_task_definition_id_id CHECK (task_definition_id IS NULL OR (task_definition_id > 0 AND task_definition_id <= 9223372036854775807)),
    CONSTRAINT fk_ifj_instance_definition FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_instance_definition_node FOREIGN KEY (definition_node_id) REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_instance_node FOREIGN KEY (node_id) REFERENCES ifj_job_node (id) ON DELETE SET NULL,
    CONSTRAINT fk_ifj_instance_parent FOREIGN KEY (parent_instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_instance_root FOREIGN KEY (root_instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_instance_task_definition FOREIGN KEY (task_definition_id) REFERENCES ifj_task_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_instance_trigger FOREIGN KEY (trigger_id) REFERENCES ifj_trigger (id) ON DELETE SET NULL
) WITHOUT ROWID;

CREATE TABLE ifj_instance_log (
    id INTEGER NOT NULL,
    instance_id INTEGER NOT NULL,
    log_time TEXT NOT NULL,
    level TEXT NOT NULL,
    content text NOT NULL,
    CONSTRAINT pk_ifj_instance_log PRIMARY KEY (id),
    CONSTRAINT chk_sqlite_ifj_instance_log_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_log_instance_id_id CHECK (instance_id IS NULL OR (instance_id > 0 AND instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_log_log_time_timestamp CHECK (log_time IS NULL OR (length(log_time) = 23 AND log_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_instance_log_level_len CHECK (level IS NULL OR length(level) <= 16),
    CONSTRAINT fk_ifj_instance_log_instance FOREIGN KEY (instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE TABLE ifj_job (
    id INTEGER NOT NULL,
    name TEXT NOT NULL,
    timeout INTEGER DEFAULT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    description TEXT DEFAULT NULL,
    concurrency_type TEXT DEFAULT NULL,
    lock_key TEXT DEFAULT NULL,
    max_concurrency INTEGER DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    current_definition_id INTEGER DEFAULT NULL,
    current_revision INTEGER DEFAULT NULL,
    retired INTEGER NOT NULL DEFAULT 0,
    retired_time TEXT DEFAULT NULL,
    CONSTRAINT pk_ifj_job PRIMARY KEY (id),
    CONSTRAINT ck_ifj_job_current_pointer_pair CHECK ((((current_definition_id is null) and (current_revision is null)) or ((current_definition_id is not null) and (current_revision is not null)))),
    CONSTRAINT ck_ifj_job_retired_state CHECK ((((retired = 0) and (retired_time is null)) or ((retired = 1) and (retired_time is not null)))),
    CONSTRAINT chk_sqlite_ifj_job_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_name_len CHECK (name IS NULL OR length(name) <= 128),
    CONSTRAINT chk_sqlite_ifj_job_enabled_bool CHECK (enabled IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_job_description_len CHECK (description IS NULL OR length(description) <= 500),
    CONSTRAINT chk_sqlite_ifj_job_concurrency_type_len CHECK (concurrency_type IS NULL OR length(concurrency_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_job_lock_key_len CHECK (lock_key IS NULL OR length(lock_key) <= 255),
    CONSTRAINT chk_sqlite_ifj_job_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_job_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_job_current_definition_id_id CHECK (current_definition_id IS NULL OR (current_definition_id > 0 AND current_definition_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_retired_bool CHECK (retired IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_job_retired_time_timestamp CHECK (retired_time IS NULL OR (length(retired_time) = 23 AND retired_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_job_definition (
    id INTEGER NOT NULL,
    job_id INTEGER NOT NULL,
    revision INTEGER NOT NULL,
    name TEXT NOT NULL,
    timeout INTEGER DEFAULT NULL,
    enabled INTEGER NOT NULL,
    description TEXT DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_job_definition PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_job_revision UNIQUE (job_id,revision),
    CONSTRAINT ck_ifj_job_definition_revision CHECK ((revision > 0)),
    CONSTRAINT chk_sqlite_ifj_job_definition_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_job_id_id CHECK (job_id IS NULL OR (job_id > 0 AND job_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_name_len CHECK (name IS NULL OR length(name) <= 128),
    CONSTRAINT chk_sqlite_ifj_job_definition_enabled_bool CHECK (enabled IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_job_definition_description_len CHECK (description IS NULL OR length(description) <= 500),
    CONSTRAINT chk_sqlite_ifj_job_definition_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_job_definition_job FOREIGN KEY (job_id) REFERENCES ifj_job (id) ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE TABLE ifj_job_definition_edge (
    id INTEGER NOT NULL,
    definition_id INTEGER NOT NULL,
    from_node_id INTEGER NOT NULL,
    to_node_id INTEGER NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_job_definition_edge PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_edge_path UNIQUE (definition_id,from_node_id,to_node_id),
    CONSTRAINT chk_sqlite_ifj_job_definition_edge_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_edge_definition_id_id CHECK (definition_id IS NULL OR (definition_id > 0 AND definition_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_edge_from_node_id_id CHECK (from_node_id IS NULL OR (from_node_id > 0 AND from_node_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_edge_to_node_id_id CHECK (to_node_id IS NULL OR (to_node_id > 0 AND to_node_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_edge_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_job_definition_edge_definition FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_job_definition_edge_from_node FOREIGN KEY (from_node_id) REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_job_definition_edge_to_node FOREIGN KEY (to_node_id) REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE TABLE ifj_job_definition_node (
    id INTEGER NOT NULL,
    definition_id INTEGER NOT NULL,
    node_key TEXT NOT NULL,
    name TEXT NOT NULL,
    node_type TEXT NOT NULL,
    ref_id INTEGER NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    ref_definition_id INTEGER DEFAULT NULL,
    ref_task_definition_id INTEGER DEFAULT NULL,
    CONSTRAINT pk_ifj_job_definition_node PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_node_key UNIQUE (definition_id,node_key),
    CONSTRAINT chk_ifj_job_definition_node_ref_snapshot CHECK ((((node_type = 'JOB') and (ref_definition_id is not null) and (ref_task_definition_id is null)) or ((node_type = 'TASK') and (ref_definition_id is null) and (ref_task_definition_id is not null)))),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_definition_id_id CHECK (definition_id IS NULL OR (definition_id > 0 AND definition_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_node_key_len CHECK (node_key IS NULL OR length(node_key) <= 64),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_name_len CHECK (name IS NULL OR length(name) <= 128),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_node_type_len CHECK (node_type IS NULL OR length(node_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_ref_id_id CHECK (ref_id IS NULL OR (ref_id > 0 AND ref_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_ref_definition_id_id CHECK (ref_definition_id IS NULL OR (ref_definition_id > 0 AND ref_definition_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_ref_task_definition_id_id CHECK (ref_task_definition_id IS NULL OR (ref_task_definition_id > 0 AND ref_task_definition_id <= 9223372036854775807)),
    CONSTRAINT fk_ifj_job_definition_node_definition FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_job_definition_node_ref_job_definition FOREIGN KEY (ref_definition_id) REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_job_definition_node_ref_task_definition FOREIGN KEY (ref_task_definition_id) REFERENCES ifj_task_definition (id) ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE TABLE ifj_job_definition_node_policy (
    id INTEGER NOT NULL,
    definition_id INTEGER NOT NULL,
    node_id INTEGER NOT NULL,
    fail_strategy TEXT NOT NULL,
    execute_condition TEXT NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_job_definition_node_policy PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_node_policy_node UNIQUE (node_id),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_policy_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_policy_definition_id_id CHECK (definition_id IS NULL OR (definition_id > 0 AND definition_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_policy_node_id_id CHECK (node_id IS NULL OR (node_id > 0 AND node_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_policy_fail_strategy_len CHECK (fail_strategy IS NULL OR length(fail_strategy) <= 32),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_policy_execute_condition_len CHECK (execute_condition IS NULL OR length(execute_condition) <= 32),
    CONSTRAINT chk_sqlite_ifj_job_definition_node_policy_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_job_definition_node_policy_definition FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_job_definition_node_policy_node FOREIGN KEY (node_id) REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE TABLE ifj_job_edge (
    id INTEGER NOT NULL,
    job_id INTEGER NOT NULL,
    from_node_id INTEGER NOT NULL,
    to_node_id INTEGER NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_job_edge PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_edge_path UNIQUE (job_id,from_node_id,to_node_id),
    CONSTRAINT chk_sqlite_ifj_job_edge_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_edge_job_id_id CHECK (job_id IS NULL OR (job_id > 0 AND job_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_edge_from_node_id_id CHECK (from_node_id IS NULL OR (from_node_id > 0 AND from_node_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_edge_to_node_id_id CHECK (to_node_id IS NULL OR (to_node_id > 0 AND to_node_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_edge_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_job_edge_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_job_edge_from_node FOREIGN KEY (from_node_id) REFERENCES ifj_job_node (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_job_edge_job FOREIGN KEY (job_id) REFERENCES ifj_job (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_job_edge_to_node FOREIGN KEY (to_node_id) REFERENCES ifj_job_node (id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE TABLE ifj_job_node (
    id INTEGER NOT NULL,
    name TEXT NOT NULL,
    job_id INTEGER NOT NULL,
    node_type TEXT NOT NULL,
    ref_id INTEGER NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_job_node PRIMARY KEY (id),
    CONSTRAINT chk_sqlite_ifj_job_node_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_node_name_len CHECK (name IS NULL OR length(name) <= 128),
    CONSTRAINT chk_sqlite_ifj_job_node_job_id_id CHECK (job_id IS NULL OR (job_id > 0 AND job_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_node_node_type_len CHECK (node_type IS NULL OR length(node_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_job_node_ref_id_id CHECK (ref_id IS NULL OR (ref_id > 0 AND ref_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_node_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_job_node_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_job_node_job FOREIGN KEY (job_id) REFERENCES ifj_job (id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE TABLE ifj_job_node_policy (
    id INTEGER NOT NULL,
    node_id INTEGER NOT NULL,
    fail_strategy TEXT NOT NULL,
    execute_condition TEXT NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_job_node_policy PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_node_policy_node_id UNIQUE (node_id),
    CONSTRAINT chk_sqlite_ifj_job_node_policy_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_node_policy_node_id_id CHECK (node_id IS NULL OR (node_id > 0 AND node_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_job_node_policy_fail_strategy_len CHECK (fail_strategy IS NULL OR length(fail_strategy) <= 32),
    CONSTRAINT chk_sqlite_ifj_job_node_policy_execute_condition_len CHECK (execute_condition IS NULL OR length(execute_condition) <= 32),
    CONSTRAINT chk_sqlite_ifj_job_node_policy_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_job_node_policy_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_job_node_policy_node FOREIGN KEY (node_id) REFERENCES ifj_job_node (id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE TABLE ifj_maintenance_lease (
    lease_name TEXT NOT NULL,
    lease_owner TEXT DEFAULT NULL,
    lease_token TEXT DEFAULT NULL,
    lease_deadline TEXT DEFAULT NULL,
    fence_token INTEGER NOT NULL DEFAULT 0,
    last_acquired_at TEXT DEFAULT NULL,
    last_completed_at TEXT DEFAULT NULL,
    last_success INTEGER DEFAULT NULL,
    last_failure_code TEXT DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_maintenance_lease PRIMARY KEY (lease_name),
    CONSTRAINT uk_ifj_maintenance_lease_token UNIQUE (lease_token),
    CONSTRAINT chk_ifj_maintenance_lease_fence CHECK ((fence_token >= 0)),
    CONSTRAINT chk_ifj_maintenance_lease_holder CHECK ((((lease_owner is null) and (lease_token is null) and (lease_deadline is null)) or ((lease_owner is not null) and (lease_token is not null) and (lease_deadline is not null)))),
    CONSTRAINT chk_sqlite_ifj_maintenance_lease_lease_name_len CHECK (lease_name IS NULL OR length(lease_name) <= 64),
    CONSTRAINT chk_sqlite_ifj_maintenance_lease_lease_owner_len CHECK (lease_owner IS NULL OR length(lease_owner) <= 128),
    CONSTRAINT chk_sqlite_ifj_maintenance_lease_lease_token_len CHECK (lease_token IS NULL OR length(lease_token) <= 36),
    CONSTRAINT chk_sqlite_ifj_maintenance_lease_lease_deadline_timestamp CHECK (lease_deadline IS NULL OR (length(lease_deadline) = 23 AND lease_deadline GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_maintenance_lease_last_acquired_at_timestamp CHECK (last_acquired_at IS NULL OR (length(last_acquired_at) = 23 AND last_acquired_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_maintenance_lease_last_completed_at_timestamp CHECK (last_completed_at IS NULL OR (length(last_completed_at) = 23 AND last_completed_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_maintenance_lease_last_success_bool CHECK (last_success IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_maintenance_lease_last_failure_code_len CHECK (last_failure_code IS NULL OR length(last_failure_code) <= 64),
    CONSTRAINT chk_sqlite_ifj_maintenance_lease_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_maintenance_lease_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_notification_outbox (
    id INTEGER NOT NULL,
    deduplication_key TEXT NOT NULL,
    notify_config_id INTEGER NOT NULL,
    notify_type TEXT DEFAULT NULL,
    config_snapshot text,
    config_enabled INTEGER DEFAULT NULL,
    title TEXT DEFAULT NULL,
    subject TEXT NOT NULL,
    content text NOT NULL,
    source_type TEXT NOT NULL,
    correlation_id TEXT DEFAULT NULL,
    operator_id TEXT DEFAULT NULL,
    target_type TEXT DEFAULT NULL,
    target_id INTEGER DEFAULT NULL,
    instance_id INTEGER DEFAULT NULL,
    notify_binding_id INTEGER DEFAULT NULL,
    status TEXT NOT NULL,
    available_at TEXT NOT NULL,
    lease_owner TEXT DEFAULT NULL,
    claim_token TEXT DEFAULT NULL,
    lease_deadline TEXT DEFAULT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    last_failure_code TEXT DEFAULT NULL,
    last_failure_summary TEXT DEFAULT NULL,
    delivered_at TEXT DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_notification_outbox PRIMARY KEY (id),
    CONSTRAINT uk_ifj_notification_outbox_deduplication UNIQUE (deduplication_key),
    CONSTRAINT uk_ifj_notification_outbox_claim_token UNIQUE (claim_token),
    CONSTRAINT chk_ifj_notification_outbox_attempts CHECK (((attempt_count >= 0) and (max_attempts between 1 and 100))),
    CONSTRAINT chk_ifj_notification_outbox_config_snapshot CHECK ((((notify_type is null) and (config_snapshot is null) and (config_enabled is null)) or ((notify_type is not null) and (config_snapshot is not null) and (config_enabled is not null)))),
    CONSTRAINT chk_ifj_notification_outbox_delivery CHECK ((((status = 'DELIVERED') and (delivered_at is not null)) or ((status <> 'DELIVERED') and (delivered_at is null)))),
    CONSTRAINT chk_ifj_notification_outbox_lease CHECK ((((status = 'CLAIMED') and (lease_owner is not null) and (claim_token is not null) and (lease_deadline is not null)) or ((status <> 'CLAIMED') and (lease_owner is null) and (claim_token is null) and (lease_deadline is null)))),
    CONSTRAINT chk_ifj_notification_outbox_status CHECK ((status in ('READY','CLAIMED','RETRY_WAIT','DELIVERED','DEAD'))),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_deduplication_key_len CHECK (deduplication_key IS NULL OR length(deduplication_key) <= 191),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_notify_config_id_id CHECK (notify_config_id IS NULL OR (notify_config_id > 0 AND notify_config_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_notify_type_len CHECK (notify_type IS NULL OR length(notify_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_config_enabled_bool CHECK (config_enabled IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_title_len CHECK (title IS NULL OR length(title) <= 255),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_subject_len CHECK (subject IS NULL OR length(subject) <= 255),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_source_type_len CHECK (source_type IS NULL OR length(source_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_correlation_id_len CHECK (correlation_id IS NULL OR length(correlation_id) <= 64),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_operator_id_len CHECK (operator_id IS NULL OR length(operator_id) <= 128),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_target_type_len CHECK (target_type IS NULL OR length(target_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_target_id_id CHECK (target_id IS NULL OR (target_id > 0 AND target_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_instance_id_id CHECK (instance_id IS NULL OR (instance_id > 0 AND instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_notify_binding_id_id CHECK (notify_binding_id IS NULL OR (notify_binding_id > 0 AND notify_binding_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_status_len CHECK (status IS NULL OR length(status) <= 16),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_available_at_timestamp CHECK (available_at IS NULL OR (length(available_at) = 23 AND available_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_lease_owner_len CHECK (lease_owner IS NULL OR length(lease_owner) <= 128),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_claim_token_len CHECK (claim_token IS NULL OR length(claim_token) <= 36),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_lease_deadline_timestamp CHECK (lease_deadline IS NULL OR (length(lease_deadline) = 23 AND lease_deadline GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_last_failure_code_len CHECK (last_failure_code IS NULL OR length(last_failure_code) <= 64),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_last_failure_summary_len CHECK (last_failure_summary IS NULL OR length(last_failure_summary) <= 500),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_delivered_at_timestamp CHECK (delivered_at IS NULL OR (length(delivered_at) = 23 AND delivered_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_notification_outbox_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_notify_binding (
    id INTEGER NOT NULL,
    target_type TEXT NOT NULL,
    target_id INTEGER NOT NULL,
    notify_config_id INTEGER NOT NULL,
    notify_policy TEXT NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_notify_binding PRIMARY KEY (id),
    CONSTRAINT uk_ifj_notify_binding_target UNIQUE (target_type,target_id,notify_config_id),
    CONSTRAINT chk_sqlite_ifj_notify_binding_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notify_binding_target_type_len CHECK (target_type IS NULL OR length(target_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_notify_binding_target_id_id CHECK (target_id IS NULL OR (target_id > 0 AND target_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notify_binding_notify_config_id_id CHECK (notify_config_id IS NULL OR (notify_config_id > 0 AND notify_config_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notify_binding_notify_policy_len CHECK (notify_policy IS NULL OR length(notify_policy) <= 32),
    CONSTRAINT chk_sqlite_ifj_notify_binding_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_notify_binding_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_notify_binding_config FOREIGN KEY (notify_config_id) REFERENCES ifj_notify_config (id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE TABLE ifj_notify_config (
    id INTEGER NOT NULL,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    config text NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_notify_config PRIMARY KEY (id),
    CONSTRAINT chk_sqlite_ifj_notify_config_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notify_config_name_len CHECK (name IS NULL OR length(name) <= 128),
    CONSTRAINT chk_sqlite_ifj_notify_config_type_len CHECK (type IS NULL OR length(type) <= 32),
    CONSTRAINT chk_sqlite_ifj_notify_config_enabled_bool CHECK (enabled IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_notify_config_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_notify_config_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_notify_send_log (
    id INTEGER NOT NULL,
    source_type TEXT NOT NULL,
    correlation_id TEXT DEFAULT NULL,
    operator_id TEXT DEFAULT NULL,
    target_type TEXT DEFAULT NULL,
    target_id INTEGER DEFAULT NULL,
    instance_id INTEGER DEFAULT NULL,
    notify_binding_id INTEGER DEFAULT NULL,
    notify_config_id INTEGER NOT NULL,
    notify_type TEXT DEFAULT NULL,
    result TEXT NOT NULL,
    failure_code TEXT DEFAULT NULL,
    failure_summary TEXT DEFAULT NULL,
    duration_ms INTEGER DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_notify_send_log PRIMARY KEY (id),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_source_type_len CHECK (source_type IS NULL OR length(source_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_correlation_id_len CHECK (correlation_id IS NULL OR length(correlation_id) <= 64),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_operator_id_len CHECK (operator_id IS NULL OR length(operator_id) <= 128),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_target_type_len CHECK (target_type IS NULL OR length(target_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_target_id_id CHECK (target_id IS NULL OR (target_id > 0 AND target_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_instance_id_id CHECK (instance_id IS NULL OR (instance_id > 0 AND instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_notify_binding_id_id CHECK (notify_binding_id IS NULL OR (notify_binding_id > 0 AND notify_binding_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_notify_config_id_id CHECK (notify_config_id IS NULL OR (notify_config_id > 0 AND notify_config_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_notify_type_len CHECK (notify_type IS NULL OR length(notify_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_result_len CHECK (result IS NULL OR length(result) <= 32),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_failure_code_len CHECK (failure_code IS NULL OR length(failure_code) <= 64),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_failure_summary_len CHECK (failure_summary IS NULL OR length(failure_summary) <= 500),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_notify_send_log_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_resource (
    id INTEGER NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    config text NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    description TEXT DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_resource PRIMARY KEY (id),
    CONSTRAINT uk_ifj_resource_code UNIQUE (code),
    CONSTRAINT chk_sqlite_ifj_resource_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_resource_code_len CHECK (code IS NULL OR length(code) <= 128),
    CONSTRAINT chk_sqlite_ifj_resource_name_len CHECK (name IS NULL OR length(name) <= 128),
    CONSTRAINT chk_sqlite_ifj_resource_type_len CHECK (type IS NULL OR length(type) <= 32),
    CONSTRAINT chk_sqlite_ifj_resource_enabled_bool CHECK (enabled IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_resource_description_len CHECK (description IS NULL OR length(description) <= 500),
    CONSTRAINT chk_sqlite_ifj_resource_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_resource_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_runtime_command (
    request_id TEXT NOT NULL,
    instance_id INTEGER NOT NULL,
    root_instance_id INTEGER NOT NULL,
    trigger_id INTEGER NOT NULL,
    command_type TEXT NOT NULL,
    status TEXT NOT NULL,
    requested_by TEXT NOT NULL,
    result_message TEXT DEFAULT NULL,
    available_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    processing_owner TEXT DEFAULT NULL,
    processing_token TEXT DEFAULT NULL,
    processing_fence INTEGER DEFAULT NULL,
    processed_at TEXT DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_runtime_command PRIMARY KEY (request_id),
    CONSTRAINT uk_ifj_runtime_command_processing_token UNIQUE (processing_token),
    CONSTRAINT chk_ifj_runtime_command_processing CHECK ((((status = 'PROCESSING') and (processing_owner is not null) and (processing_token is not null) and (processing_fence is not null)) or ((status <> 'PROCESSING') and (processing_owner is null) and (processing_token is null) and (processing_fence is null)))),
    CONSTRAINT chk_ifj_runtime_command_status CHECK ((status in ('PENDING','PROCESSING','SUCCEEDED','REJECTED','FAILED','EXPIRED'))),
    CONSTRAINT chk_ifj_runtime_command_type CHECK ((command_type in ('STOP','PAUSE','RESUME','RETRY'))),
    CONSTRAINT chk_sqlite_ifj_runtime_command_request_id_len CHECK (request_id IS NULL OR length(request_id) <= 191),
    CONSTRAINT chk_sqlite_ifj_runtime_command_instance_id_id CHECK (instance_id IS NULL OR (instance_id > 0 AND instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_runtime_command_root_instance_id_id CHECK (root_instance_id IS NULL OR (root_instance_id > 0 AND root_instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_runtime_command_trigger_id_id CHECK (trigger_id IS NULL OR (trigger_id > 0 AND trigger_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_runtime_command_command_type_len CHECK (command_type IS NULL OR length(command_type) <= 16),
    CONSTRAINT chk_sqlite_ifj_runtime_command_status_len CHECK (status IS NULL OR length(status) <= 16),
    CONSTRAINT chk_sqlite_ifj_runtime_command_requested_by_len CHECK (requested_by IS NULL OR length(requested_by) <= 128),
    CONSTRAINT chk_sqlite_ifj_runtime_command_result_message_len CHECK (result_message IS NULL OR length(result_message) <= 500),
    CONSTRAINT chk_sqlite_ifj_runtime_command_available_at_timestamp CHECK (available_at IS NULL OR (length(available_at) = 23 AND available_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_runtime_command_expires_at_timestamp CHECK (expires_at IS NULL OR (length(expires_at) = 23 AND expires_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_runtime_command_processing_owner_len CHECK (processing_owner IS NULL OR length(processing_owner) <= 128),
    CONSTRAINT chk_sqlite_ifj_runtime_command_processing_token_len CHECK (processing_token IS NULL OR length(processing_token) <= 36),
    CONSTRAINT chk_sqlite_ifj_runtime_command_processed_at_timestamp CHECK (processed_at IS NULL OR (length(processed_at) = 23 AND processed_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_runtime_command_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_runtime_command_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_runtime_command_instance FOREIGN KEY (instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_runtime_command_root FOREIGN KEY (root_instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_runtime_command_trigger FOREIGN KEY (trigger_id) REFERENCES ifj_trigger (id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE TABLE ifj_schedule (
    id INTEGER NOT NULL,
    target_type TEXT NOT NULL,
    target_id INTEGER NOT NULL,
    schedule_type TEXT NOT NULL,
    cron_expr TEXT DEFAULT NULL,
    interval_sec INTEGER DEFAULT NULL,
    calendar_id INTEGER DEFAULT NULL,
    start_time TEXT DEFAULT NULL,
    end_time TEXT DEFAULT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    last_fire_time TEXT DEFAULT NULL,
    next_fire_time TEXT DEFAULT NULL,
    fire_claim_token TEXT DEFAULT NULL,
    fire_claimed_at TEXT DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    variables text,
    CONSTRAINT pk_ifj_schedule PRIMARY KEY (id),
    CONSTRAINT uk_ifj_schedule_fire_claim_token UNIQUE (fire_claim_token),
    CONSTRAINT chk_ifj_schedule_fire_claim CHECK ((((fire_claim_token is null) and (fire_claimed_at is null)) or ((fire_claim_token is not null) and (fire_claimed_at is not null)))),
    CONSTRAINT chk_sqlite_ifj_schedule_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_schedule_target_type_len CHECK (target_type IS NULL OR length(target_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_schedule_target_id_id CHECK (target_id IS NULL OR (target_id > 0 AND target_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_schedule_schedule_type_len CHECK (schedule_type IS NULL OR length(schedule_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_schedule_cron_expr_len CHECK (cron_expr IS NULL OR length(cron_expr) <= 128),
    CONSTRAINT chk_sqlite_ifj_schedule_calendar_id_id CHECK (calendar_id IS NULL OR (calendar_id > 0 AND calendar_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_schedule_start_time_timestamp CHECK (start_time IS NULL OR (length(start_time) = 23 AND start_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_schedule_end_time_timestamp CHECK (end_time IS NULL OR (length(end_time) = 23 AND end_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_schedule_enabled_bool CHECK (enabled IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_schedule_last_fire_time_timestamp CHECK (last_fire_time IS NULL OR (length(last_fire_time) = 23 AND last_fire_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_schedule_next_fire_time_timestamp CHECK (next_fire_time IS NULL OR (length(next_fire_time) = 23 AND next_fire_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_schedule_fire_claim_token_len CHECK (fire_claim_token IS NULL OR length(fire_claim_token) <= 36),
    CONSTRAINT chk_sqlite_ifj_schedule_fire_claimed_at_timestamp CHECK (fire_claimed_at IS NULL OR (length(fire_claimed_at) = 23 AND fire_claimed_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_schedule_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_schedule_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_schedule_calendar FOREIGN KEY (calendar_id) REFERENCES ifj_calendar (id) ON DELETE SET NULL
) WITHOUT ROWID;

CREATE TABLE ifj_schema_capability (
    capability_key TEXT NOT NULL,
    capability_version INTEGER NOT NULL,
    enabled INTEGER NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_schema_capability PRIMARY KEY (capability_key),
    CONSTRAINT chk_ifj_schema_cap_enabled CHECK ((enabled IN (0, 1))),
    CONSTRAINT chk_ifj_schema_cap_version CHECK ((capability_version > 0)),
    CONSTRAINT chk_sqlite_ifj_schema_capability_capability_key_len CHECK (capability_key IS NULL OR length(capability_key) <= 64),
    CONSTRAINT chk_sqlite_ifj_schema_capability_enabled_bool CHECK (enabled IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_schema_capability_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_schema_capability_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_task (
    id INTEGER NOT NULL,
    name TEXT NOT NULL,
    plugin_type TEXT NOT NULL,
    plugin_config text NOT NULL,
    retry_times INTEGER NOT NULL DEFAULT 0,
    retry_interval INTEGER NOT NULL DEFAULT 0,
    timeout INTEGER DEFAULT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    description TEXT DEFAULT NULL,
    concurrency_type TEXT DEFAULT NULL,
    lock_key TEXT DEFAULT NULL,
    max_concurrency INTEGER DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    current_task_definition_id INTEGER DEFAULT NULL,
    current_revision INTEGER DEFAULT NULL,
    retired INTEGER NOT NULL DEFAULT 0,
    retired_time TEXT DEFAULT NULL,
    CONSTRAINT pk_ifj_task PRIMARY KEY (id),
    CONSTRAINT ck_ifj_task_current_pointer_pair CHECK ((((current_task_definition_id is null) and (current_revision is null)) or ((current_task_definition_id is not null) and (current_revision is not null)))),
    CONSTRAINT ck_ifj_task_current_revision_positive CHECK (((current_revision is null) or (current_revision > 0))),
    CONSTRAINT ck_ifj_task_retired_state CHECK ((((retired = 0) and (retired_time is null)) or ((retired = 1) and (retired_time is not null)))),
    CONSTRAINT chk_sqlite_ifj_task_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_task_name_len CHECK (name IS NULL OR length(name) <= 128),
    CONSTRAINT chk_sqlite_ifj_task_plugin_type_len CHECK (plugin_type IS NULL OR length(plugin_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_task_enabled_bool CHECK (enabled IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_task_description_len CHECK (description IS NULL OR length(description) <= 500),
    CONSTRAINT chk_sqlite_ifj_task_concurrency_type_len CHECK (concurrency_type IS NULL OR length(concurrency_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_task_lock_key_len CHECK (lock_key IS NULL OR length(lock_key) <= 255),
    CONSTRAINT chk_sqlite_ifj_task_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_task_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_task_current_task_definition_id_id CHECK (current_task_definition_id IS NULL OR (current_task_definition_id > 0 AND current_task_definition_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_task_retired_bool CHECK (retired IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_task_retired_time_timestamp CHECK (retired_time IS NULL OR (length(retired_time) = 23 AND retired_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'))
) WITHOUT ROWID;

CREATE TABLE ifj_task_definition (
    id INTEGER NOT NULL,
    task_id INTEGER NOT NULL,
    revision INTEGER NOT NULL,
    name TEXT NOT NULL,
    plugin_type TEXT NOT NULL,
    plugin_config text NOT NULL,
    retry_times INTEGER NOT NULL,
    retry_interval INTEGER NOT NULL,
    timeout INTEGER DEFAULT NULL,
    enabled INTEGER NOT NULL,
    description TEXT DEFAULT NULL,
    concurrency_type TEXT NOT NULL,
    lock_key TEXT DEFAULT NULL,
    max_concurrency INTEGER DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_task_definition PRIMARY KEY (id),
    CONSTRAINT uk_ifj_task_definition_task_revision UNIQUE (task_id,revision),
    CONSTRAINT ck_ifj_task_definition_revision CHECK ((revision > 0)),
    CONSTRAINT chk_sqlite_ifj_task_definition_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_task_definition_task_id_id CHECK (task_id IS NULL OR (task_id > 0 AND task_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_task_definition_name_len CHECK (name IS NULL OR length(name) <= 128),
    CONSTRAINT chk_sqlite_ifj_task_definition_plugin_type_len CHECK (plugin_type IS NULL OR length(plugin_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_task_definition_enabled_bool CHECK (enabled IN (0,1)),
    CONSTRAINT chk_sqlite_ifj_task_definition_description_len CHECK (description IS NULL OR length(description) <= 500),
    CONSTRAINT chk_sqlite_ifj_task_definition_concurrency_type_len CHECK (concurrency_type IS NULL OR length(concurrency_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_task_definition_lock_key_len CHECK (lock_key IS NULL OR length(lock_key) <= 255),
    CONSTRAINT chk_sqlite_ifj_task_definition_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_task_definition_task FOREIGN KEY (task_id) REFERENCES ifj_task (id) ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE TABLE ifj_trigger (
    id INTEGER NOT NULL,
    schedule_id INTEGER DEFAULT NULL,
    target_type TEXT NOT NULL,
    target_id INTEGER NOT NULL,
    trigger_type TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    event_code TEXT DEFAULT NULL,
    status TEXT NOT NULL,
    trigger_time TEXT NOT NULL,
    message text,
    error_code TEXT DEFAULT NULL,
    message_key TEXT DEFAULT NULL,
    message_args text DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    definition_id INTEGER DEFAULT NULL,
    task_definition_id INTEGER DEFAULT NULL,
    variables text,
    event_id INTEGER DEFAULT NULL,
    event_binding_id INTEGER DEFAULT NULL,
    CONSTRAINT pk_ifj_trigger PRIMARY KEY (id),
    CONSTRAINT uk_ifj_trigger_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_ifj_trigger_event_binding UNIQUE (event_id,event_binding_id),
    CONSTRAINT chk_ifj_trigger_status CHECK ((status in ('WAITING','RUNNING','SUCCESS','FAILED','STOPPED','TIMED_OUT','SKIPPED'))),
    CONSTRAINT chk_sqlite_ifj_trigger_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_trigger_schedule_id_id CHECK (schedule_id IS NULL OR (schedule_id > 0 AND schedule_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_trigger_target_type_len CHECK (target_type IS NULL OR length(target_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_trigger_target_id_id CHECK (target_id IS NULL OR (target_id > 0 AND target_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_trigger_trigger_type_len CHECK (trigger_type IS NULL OR length(trigger_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_trigger_idempotency_key_len CHECK (idempotency_key IS NULL OR length(idempotency_key) <= 191),
    CONSTRAINT chk_sqlite_ifj_trigger_event_code_len CHECK (event_code IS NULL OR length(event_code) <= 128),
    CONSTRAINT chk_sqlite_ifj_trigger_status_len CHECK (status IS NULL OR length(status) <= 32),
    CONSTRAINT chk_sqlite_ifj_trigger_trigger_time_timestamp CHECK (trigger_time IS NULL OR (length(trigger_time) = 23 AND trigger_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_trigger_error_code_len CHECK (error_code IS NULL OR length(error_code) <= 64),
    CONSTRAINT chk_sqlite_ifj_trigger_message_key_len CHECK (message_key IS NULL OR length(message_key) <= 128),
    CONSTRAINT chk_sqlite_ifj_trigger_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_trigger_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_trigger_definition_id_id CHECK (definition_id IS NULL OR (definition_id > 0 AND definition_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_trigger_task_definition_id_id CHECK (task_definition_id IS NULL OR (task_definition_id > 0 AND task_definition_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_trigger_event_id_id CHECK (event_id IS NULL OR (event_id > 0 AND event_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_trigger_event_binding_id_id CHECK (event_binding_id IS NULL OR (event_binding_id > 0 AND event_binding_id <= 9223372036854775807)),
    CONSTRAINT fk_ifj_trigger_definition FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_trigger_event FOREIGN KEY (event_id) REFERENCES ifj_event (id) ON DELETE SET NULL,
    CONSTRAINT fk_ifj_trigger_event_binding FOREIGN KEY (event_binding_id) REFERENCES ifj_event_binding (id) ON DELETE SET NULL,
    CONSTRAINT fk_ifj_trigger_schedule FOREIGN KEY (schedule_id) REFERENCES ifj_schedule (id) ON DELETE SET NULL,
    CONSTRAINT fk_ifj_trigger_task_definition FOREIGN KEY (task_definition_id) REFERENCES ifj_task_definition (id) ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE TABLE ifj_trigger_work_item (
    trigger_id INTEGER NOT NULL,
    root_instance_id INTEGER DEFAULT NULL,
    status TEXT NOT NULL,
    available_at TEXT NOT NULL,
    lease_owner TEXT DEFAULT NULL,
    claim_token TEXT DEFAULT NULL,
    lease_deadline TEXT DEFAULT NULL,
    fence_token INTEGER NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    last_error TEXT DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_trigger_work_item PRIMARY KEY (trigger_id),
    CONSTRAINT uk_ifj_trigger_work_item_claim_token UNIQUE (claim_token),
    CONSTRAINT uk_ifj_trigger_work_item_root UNIQUE (root_instance_id),
    CONSTRAINT chk_ifj_trigger_work_item_attempts CHECK (((attempt_count >= 0) and (max_attempts between 1 and 100))),
    CONSTRAINT chk_ifj_trigger_work_item_fence CHECK ((fence_token >= 0)),
    CONSTRAINT chk_ifj_trigger_work_item_lease CHECK ((((status = 'CLAIMED') and (lease_owner is not null) and (claim_token is not null) and (lease_deadline is not null)) or ((status <> 'CLAIMED') and (lease_owner is null) and (claim_token is null) and (lease_deadline is null)))),
    CONSTRAINT chk_ifj_trigger_work_item_status CHECK ((status in ('READY','CLAIMED','SUSPENDED','RETRY_WAIT','COMPLETED','DEAD'))),
    CONSTRAINT chk_sqlite_ifj_trigger_work_item_trigger_id_id CHECK (trigger_id IS NULL OR (trigger_id > 0 AND trigger_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_trigger_work_item_root_instance_id_id CHECK (root_instance_id IS NULL OR (root_instance_id > 0 AND root_instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_trigger_work_item_status_len CHECK (status IS NULL OR length(status) <= 16),
    CONSTRAINT chk_sqlite_ifj_trigger_work_item_available_at_timestamp CHECK (available_at IS NULL OR (length(available_at) = 23 AND available_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_trigger_work_item_lease_owner_len CHECK (lease_owner IS NULL OR length(lease_owner) <= 128),
    CONSTRAINT chk_sqlite_ifj_trigger_work_item_claim_token_len CHECK (claim_token IS NULL OR length(claim_token) <= 36),
    CONSTRAINT chk_sqlite_ifj_trigger_work_item_lease_deadline_timestamp CHECK (lease_deadline IS NULL OR (length(lease_deadline) = 23 AND lease_deadline GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_trigger_work_item_last_error_len CHECK (last_error IS NULL OR length(last_error) <= 500),
    CONSTRAINT chk_sqlite_ifj_trigger_work_item_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_trigger_work_item_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT fk_ifj_trigger_work_item_root FOREIGN KEY (root_instance_id) REFERENCES ifj_instance (id) ON DELETE SET NULL,
    CONSTRAINT fk_ifj_trigger_work_item_trigger FOREIGN KEY (trigger_id) REFERENCES ifj_trigger (id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE INDEX idx_ifj_app_run_status_heartbeat ON ifj_app_run (status,heartbeat_at,run_id);
CREATE INDEX idx_ifj_app_run_instance_heartbeat ON ifj_app_run (instance_name,status,heartbeat_at,run_id);
CREATE INDEX idx_ifj_cluster_state_expiry ON ifj_cluster_state (expires_at,state_key);
CREATE INDEX idx_ifj_concurrency_permit_lease ON ifj_concurrency_permit (lease_deadline,scope_key,slot_no);
CREATE INDEX idx_ifj_concurrency_permit_root ON ifj_concurrency_permit (owner_root_instance_id,lease_deadline);
CREATE INDEX idx_ifj_concurrency_policy_lock_key ON ifj_concurrency_policy (lock_key);
CREATE INDEX idx_ifj_etl_checkpoint_instance ON ifj_etl_checkpoint (last_instance_id);
CREATE INDEX idx_ifj_event_code_business ON ifj_event (event_code,business_key,arrived_at,id);
CREATE INDEX idx_ifj_event_code_correlation ON ifj_event (event_code,correlation_key,arrived_at,id);
CREATE INDEX idx_ifj_event_arrived ON ifj_event (arrived_at,id);
CREATE INDEX idx_ifj_event_binding_event ON ifj_event_binding (event_code,enabled);
CREATE INDEX fk_ifj_event_wait_event ON ifj_event_wait (matched_event_id);
CREATE INDEX idx_ifj_event_wait_match_business ON ifj_event_wait (status,event_code,business_key,accept_after,id);
CREATE INDEX idx_ifj_event_wait_match_correlation ON ifj_event_wait (status,event_code,correlation_key,accept_after,id);
CREATE INDEX idx_ifj_event_wait_root_status ON ifj_event_wait (root_instance_id,status);
CREATE INDEX idx_ifj_event_wait_timeout ON ifj_event_wait (status,timeout_at,id);
CREATE INDEX idx_ifj_wait_operator_lookup ON ifj_event_wait_operator (operator_id,wait_id);
CREATE INDEX fk_ifj_instance_node ON ifj_instance (node_id);
CREATE INDEX idx_ifj_instance_root_status ON ifj_instance (root_instance_id,status);
CREATE INDEX idx_ifj_instance_target ON ifj_instance (target_type,target_id);
CREATE INDEX idx_ifj_instance_trigger_id ON ifj_instance (trigger_id);
CREATE INDEX fk_ifj_instance_definition_node ON ifj_instance (definition_node_id);
CREATE INDEX idx_ifj_instance_definition_status ON ifj_instance (definition_id,status);
CREATE INDEX idx_ifj_instance_task_definition_status ON ifj_instance (task_definition_id,status);
CREATE INDEX idx_ifj_instance_status_update ON ifj_instance (status,update_time,id);
CREATE INDEX idx_ifj_instance_error_update ON ifj_instance (error_code,update_time,id);
CREATE INDEX idx_ifj_instance_root_timeout ON ifj_instance (parent_instance_id,status,timeout_at,id);
CREATE INDEX idx_ifj_instance_log_instance_time ON ifj_instance_log (instance_id,log_time);
CREATE INDEX idx_ifj_job_enabled ON ifj_job (enabled);
CREATE INDEX idx_ifj_job_current_definition_id ON ifj_job (current_definition_id);
CREATE INDEX idx_ifj_job_definition_job_time ON ifj_job_definition (job_id,create_time,id);
CREATE INDEX fk_ifj_job_definition_edge_from_node ON ifj_job_definition_edge (from_node_id);
CREATE INDEX idx_ifj_job_definition_edge_to_node ON ifj_job_definition_edge (to_node_id);
CREATE INDEX idx_ifj_job_definition_node_ref ON ifj_job_definition_node (node_type,ref_id);
CREATE INDEX idx_ifj_job_definition_node_ref_job_definition ON ifj_job_definition_node (ref_definition_id);
CREATE INDEX idx_ifj_job_definition_node_ref_task_definition ON ifj_job_definition_node (ref_task_definition_id);
CREATE INDEX idx_ifj_job_definition_node_policy_definition ON ifj_job_definition_node_policy (definition_id);
CREATE INDEX fk_ifj_job_edge_from_node ON ifj_job_edge (from_node_id);
CREATE INDEX idx_ifj_job_edge_to_node_id ON ifj_job_edge (to_node_id);
CREATE INDEX idx_ifj_job_node_job_id ON ifj_job_node (job_id);
CREATE INDEX idx_ifj_job_node_ref ON ifj_job_node (node_type,ref_id);
CREATE INDEX idx_ifj_maintenance_lease_deadline ON ifj_maintenance_lease (lease_deadline,lease_name);
CREATE INDEX idx_ifj_notification_outbox_dispatch ON ifj_notification_outbox (status,available_at,id);
CREATE INDEX idx_ifj_notification_outbox_lease ON ifj_notification_outbox (status,lease_deadline,id);
CREATE INDEX idx_ifj_notification_outbox_instance ON ifj_notification_outbox (instance_id,status,id);
CREATE INDEX idx_ifj_notification_outbox_created ON ifj_notification_outbox (create_time,id);
CREATE INDEX fk_ifj_notify_binding_config ON ifj_notify_binding (notify_config_id);
CREATE INDEX idx_ifj_notify_binding_target ON ifj_notify_binding (target_type,target_id);
CREATE INDEX idx_ifj_notify_config_type_enabled ON ifj_notify_config (type,enabled);
CREATE INDEX idx_ifj_notify_send_log_create_time ON ifj_notify_send_log (create_time,id);
CREATE INDEX idx_ifj_notify_send_log_config_time ON ifj_notify_send_log (notify_config_id,create_time);
CREATE INDEX idx_ifj_notify_send_log_target_time ON ifj_notify_send_log (target_type,target_id,create_time);
CREATE INDEX idx_ifj_notify_send_log_instance_time ON ifj_notify_send_log (instance_id,create_time);
CREATE INDEX idx_ifj_notify_send_log_result_time ON ifj_notify_send_log (result,create_time);
CREATE INDEX idx_ifj_resource_type_enabled ON ifj_resource (type,enabled);
CREATE INDEX fk_ifj_runtime_command_instance ON ifj_runtime_command (instance_id);
CREATE INDEX idx_ifj_runtime_command_dispatch ON ifj_runtime_command (trigger_id,status,available_at,expires_at,create_time);
CREATE INDEX idx_ifj_runtime_command_root ON ifj_runtime_command (root_instance_id,create_time);
CREATE INDEX idx_ifj_runtime_command_expiration ON ifj_runtime_command (status,expires_at,request_id);
CREATE INDEX fk_ifj_schedule_calendar ON ifj_schedule (calendar_id);
CREATE INDEX idx_ifj_schedule_target ON ifj_schedule (target_type,target_id);
CREATE INDEX idx_ifj_schedule_fire ON ifj_schedule (enabled,next_fire_time);
CREATE INDEX idx_ifj_task_enabled_plugin_type ON ifj_task (enabled,plugin_type);
CREATE INDEX idx_ifj_task_current_definition_id ON ifj_task (current_task_definition_id);
CREATE INDEX idx_ifj_task_definition_task_time ON ifj_task_definition (task_id,create_time,id);
CREATE INDEX fk_ifj_trigger_schedule ON ifj_trigger (schedule_id);
CREATE INDEX idx_ifj_trigger_status_time ON ifj_trigger (status,trigger_time);
CREATE INDEX idx_ifj_trigger_target ON ifj_trigger (target_type,target_id);
CREATE INDEX idx_ifj_trigger_definition_status ON ifj_trigger (definition_id,status);
CREATE INDEX idx_ifj_trigger_task_definition_status ON ifj_trigger (task_definition_id,status);
CREATE INDEX fk_ifj_trigger_event_binding ON ifj_trigger (event_binding_id);
CREATE INDEX idx_ifj_trigger_event ON ifj_trigger (event_id);
CREATE INDEX idx_ifj_trigger_status_update ON ifj_trigger (status,update_time,id);
CREATE INDEX idx_ifj_trigger_error_update ON ifj_trigger (error_code,update_time,id);
CREATE INDEX idx_ifj_trigger_work_item_dispatch ON ifj_trigger_work_item (status,available_at,lease_deadline,trigger_id);
CREATE INDEX idx_ifj_trigger_work_item_owner ON ifj_trigger_work_item (lease_owner,lease_deadline,trigger_id);

CREATE TRIGGER trg_ifj_calendar_touch AFTER UPDATE ON ifj_calendar
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_calendar SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_calendar_exception_touch AFTER UPDATE ON ifj_calendar_exception
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_calendar_exception SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_concurrency_policy_touch AFTER UPDATE ON ifj_concurrency_policy
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_concurrency_policy SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_event_binding_touch AFTER UPDATE ON ifj_event_binding
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_event_binding SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_event_wait_touch AFTER UPDATE ON ifj_event_wait
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_event_wait SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_instance_touch AFTER UPDATE ON ifj_instance
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_instance SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_job_touch AFTER UPDATE ON ifj_job
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_job SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_job_edge_touch AFTER UPDATE ON ifj_job_edge
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_job_edge SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_job_node_touch AFTER UPDATE ON ifj_job_node
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_job_node SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_job_node_policy_touch AFTER UPDATE ON ifj_job_node_policy
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_job_node_policy SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_notify_binding_touch AFTER UPDATE ON ifj_notify_binding
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_notify_binding SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_notify_config_touch AFTER UPDATE ON ifj_notify_config
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_notify_config SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_notify_send_log_touch AFTER UPDATE ON ifj_notify_send_log
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_notify_send_log SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_resource_touch AFTER UPDATE ON ifj_resource
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_resource SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_schedule_touch AFTER UPDATE ON ifj_schedule
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_schedule SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_schema_capability_touch AFTER UPDATE ON ifj_schema_capability
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_schema_capability SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE capability_key = NEW.capability_key;
END;
CREATE TRIGGER trg_ifj_task_touch AFTER UPDATE ON ifj_task
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_task SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
CREATE TRIGGER trg_ifj_trigger_touch AFTER UPDATE ON ifj_trigger
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_trigger SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;

INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
VALUES ('platform-primary-id', 1, 1000, strftime('%Y-%m-%d %H:%M:%f','now'));

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES
    ('platform-schema', 19, 1, strftime('%Y-%m-%d %H:%M:%f','now'), strftime('%Y-%m-%d %H:%M:%f','now')),
    ('event-wait-operator-relation', 1, 1, strftime('%Y-%m-%d %H:%M:%f','now'), strftime('%Y-%m-%d %H:%M:%f','now')),
    ('stable-id-segment-seed', 1, 1, strftime('%Y-%m-%d %H:%M:%f','now'), strftime('%Y-%m-%d %H:%M:%f','now'));
