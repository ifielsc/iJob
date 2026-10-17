-- First release baseline. Logical platform schema 38; physical migration 1.
CREATE TABLE dbo.[ifj_app_run] (
    [run_id] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [instance_name] nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_app_run_instance_name] DEFAULT (NULL),
    [runtime_mode] varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL CONSTRAINT [df_v1_ifj_app_run_runtime_mode] DEFAULT ('STANDALONE'),
    [roles] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL CONSTRAINT [df_v1_ifj_app_run_roles] DEFAULT ('API,SCHEDULER,WORKER'),
    [status] varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [application_version] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_app_run_application_version] DEFAULT (NULL),
    [host_name] varchar(255) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_app_run_host_name] DEFAULT (NULL),
    [process_id] bigint NULL CONSTRAINT [df_v1_ifj_app_run_process_id] DEFAULT (NULL),
    [started_at] datetime2(3) NOT NULL,
    [heartbeat_at] datetime2(3) NOT NULL,
    [stopped_at] datetime2(3) NULL CONSTRAINT [df_v1_ifj_app_run_stopped_at] DEFAULT (NULL),
    [worker_groups] varchar(1100) COLLATE Chinese_PRC_CI_AS NOT NULL CONSTRAINT [df_ifj_app_run_worker_groups] DEFAULT (''),
    [runtime_protocol_version] int NOT NULL CONSTRAINT [df_ifj_app_run_protocol] DEFAULT ((1)),
    [worker_task_ids] varchar(2100) COLLATE Chinese_PRC_CI_AS NOT NULL CONSTRAINT [df_ifj_app_run_worker_task_ids] DEFAULT (''),
    [worker_job_ids] varchar(2100) COLLATE Chinese_PRC_CI_AS NOT NULL CONSTRAINT [df_ifj_app_run_worker_job_ids] DEFAULT (''),
    CONSTRAINT [chk_ifj_app_run_runtime_mode] CHECK ([runtime_mode]='CLUSTER' OR [runtime_mode]='STANDALONE' OR [runtime_mode]='DEVELOPMENT'),
    CONSTRAINT [chk_ifj_app_run_status] CHECK ([status]='CRASHED' OR [status]='STOPPED' OR [status]='DRAINING' OR [status]='RUNNING' OR [status]='STARTING'),
    CONSTRAINT [pk_ifj_app_run] PRIMARY KEY CLUSTERED ([run_id])
);

CREATE TABLE dbo.[ifj_auth_user] (
    [id] bigint NOT NULL,
    [login_id] nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [username] nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [display_name] nvarchar(128) COLLATE Latin1_General_100_BIN2 NULL,
    [password_hash] nvarchar(100) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [role_code] nvarchar(16) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT [df_v1_ifj_auth_user_role_code] DEFAULT ('VIEWER'),
    [enabled] bit NOT NULL CONSTRAINT [df_v1_ifj_auth_user_enabled] DEFAULT ((1)),
    [must_change_password] bit NOT NULL CONSTRAINT [df_v1_ifj_auth_user_must_change_password] DEFAULT ((1)),
    [security_version] bigint NOT NULL CONSTRAINT [df_v1_ifj_auth_user_security_version] DEFAULT ((0)),
    [revision] bigint NOT NULL CONSTRAINT [df_v1_ifj_auth_user_revision] DEFAULT ((0)),
    [created_by] nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [create_time] datetime2(3) NOT NULL,
    [update_time] datetime2(3) NOT NULL,
    CONSTRAINT [ck_ifj_auth_enabled] CHECK ([enabled]=(1) OR [enabled]=(0)),
    CONSTRAINT [ck_ifj_auth_pwd_change] CHECK ([must_change_password]=(1) OR [must_change_password]=(0)),
    CONSTRAINT [ck_ifj_auth_role] CHECK ([role_code]='VIEWER' OR [role_code]='OPERATOR' OR [role_code]='ADMIN'),
    CONSTRAINT [ck_ifj_auth_version] CHECK ([security_version]>=(0) AND [revision]>=(0)),
    CONSTRAINT [pk_ifj_auth_user] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_auth_username] UNIQUE NONCLUSTERED ([username]),
    CONSTRAINT [uk_ifj_auth_login] UNIQUE NONCLUSTERED ([login_id])
);

CREATE TABLE dbo.[ifj_calendar] (
    [id] bigint NOT NULL,
    [name] nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    [workdays_mask] int NOT NULL,
    [description] nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_calendar_description] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_calendar_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_calendar_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_calendar] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_calendar_name] UNIQUE NONCLUSTERED ([name])
);

CREATE TABLE dbo.[ifj_calendar_exception] (
    [id] bigint NOT NULL,
    [calendar_id] bigint NOT NULL,
    [date] date NOT NULL,
    [type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_calendar_exception_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_calendar_exception_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_calendar_exception] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_calendar_exception_date] UNIQUE NONCLUSTERED ([calendar_id], [date])
);

CREATE TABLE dbo.[ifj_cluster_state] (
    [state_key] varchar(512) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [state_value] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [expires_at] datetime2(3) NULL CONSTRAINT [df_v1_ifj_cluster_state_expires_at] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_cluster_state_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_cluster_state_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_cluster_state] PRIMARY KEY CLUSTERED ([state_key])
);

CREATE TABLE dbo.[ifj_concurrency_permit] (
    [scope_key] varchar(191) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [slot_no] int NOT NULL,
    [owner_instance_id] bigint NULL CONSTRAINT [df_v1_ifj_concurrency_permit_owner_instance_id] DEFAULT (NULL),
    [owner_root_instance_id] bigint NULL CONSTRAINT [df_v1_ifj_concurrency_permit_owner_root_instance_id] DEFAULT (NULL),
    [owner_trigger_id] bigint NULL CONSTRAINT [df_v1_ifj_concurrency_permit_owner_trigger_id] DEFAULT (NULL),
    [lease_owner] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_concurrency_permit_lease_owner] DEFAULT (NULL),
    [permit_token] varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_concurrency_permit_permit_token] DEFAULT (NULL),
    [permit_fence] bigint NOT NULL CONSTRAINT [df_v1_ifj_concurrency_permit_permit_fence] DEFAULT ((0)),
    [execution_fence] bigint NULL CONSTRAINT [df_v1_ifj_concurrency_permit_execution_fence] DEFAULT (NULL),
    [lease_deadline] datetime2(3) NULL CONSTRAINT [df_v1_ifj_concurrency_permit_lease_deadline] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_concurrency_permit_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_concurrency_permit_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [chk_ifj_concurrency_permit_fence] CHECK ([permit_fence]>=(0)),
    CONSTRAINT [chk_ifj_concurrency_permit_lease] CHECK ([owner_instance_id] IS NULL AND [owner_root_instance_id] IS NULL AND [owner_trigger_id] IS NULL AND [lease_owner] IS NULL AND [permit_token] IS NULL AND [execution_fence] IS NULL AND [lease_deadline] IS NULL OR [owner_instance_id] IS NOT NULL AND [owner_root_instance_id] IS NOT NULL AND [owner_trigger_id] IS NOT NULL AND [lease_owner] IS NOT NULL AND [permit_token] IS NOT NULL AND [execution_fence] IS NOT NULL AND [lease_deadline] IS NOT NULL),
    CONSTRAINT [chk_ifj_concurrency_permit_slot] CHECK ([slot_no]>(0)),
    CONSTRAINT [pk_ifj_concurrency_permit] PRIMARY KEY CLUSTERED ([scope_key], [slot_no])
);

CREATE TABLE dbo.[ifj_concurrency_resource] (
    [id] bigint NOT NULL,
    [code] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [name] nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    [max_concurrency] int NOT NULL,
    [enabled] bit NOT NULL CONSTRAINT [df_ifj_concurrency_resource_en] DEFAULT ((1)),
    [description] nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_ifj_concurrency_resource_ct] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_ifj_concurrency_resource_ut] DEFAULT (sysutcdatetime()),
    [version] bigint NOT NULL CONSTRAINT [df_concurrency_resource_version] DEFAULT ((1)),
    CONSTRAINT [chk_ifj_concurrency_resource_max] CHECK ([max_concurrency]>=(1) AND [max_concurrency]<=(10000)),
    CONSTRAINT [pk_ifj_concurrency_resource] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_concurrency_resource_code] UNIQUE NONCLUSTERED ([code])
);

CREATE TABLE dbo.[ifj_dag_gateway_decision] (
    [id] bigint NOT NULL,
    [job_instance_id] bigint NOT NULL,
    [definition_node_id] bigint NOT NULL,
    [evaluation_no] int NOT NULL,
    [selected_edge_ids] varchar(MAX) COLLATE Chinese_PRC_CI_AS NULL,
    [context_digest] char(64) COLLATE Chinese_PRC_CI_AS NOT NULL,
    [evaluator_version] int NOT NULL,
    [status] varchar(16) COLLATE Chinese_PRC_CI_AS NOT NULL,
    [error_code] varchar(64) COLLATE Chinese_PRC_CI_AS NULL,
    [execution_fence] bigint NOT NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_ifj_dag_decision_create] DEFAULT (sysutcdatetime()),
    CONSTRAINT [ck_ifj_dag_gateway_decision_eval] CHECK ([evaluation_no]>(0)),
    CONSTRAINT [ck_ifj_dag_gateway_decision_status] CHECK ([status]='SUCCESS' AND [selected_edge_ids] IS NOT NULL AND [error_code] IS NULL OR [status]='FAILED' AND [selected_edge_ids] IS NULL AND [error_code] IS NOT NULL),
    CONSTRAINT [ck_ifj_dag_gateway_decision_version] CHECK ([evaluator_version]=(1)),
    CONSTRAINT [pk_ifj_dag_gateway_decision] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_dag_gateway_decision_eval] UNIQUE NONCLUSTERED ([job_instance_id], [definition_node_id], [evaluation_no])
);

CREATE TABLE dbo.[ifj_etl_checkpoint] (
    [task_id] bigint NOT NULL,
    [checkpoint_key] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [source_fingerprint] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [watermark_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [watermark_value] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NULL,
    [tie_breaker_value] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NULL,
    [last_instance_id] bigint NULL CONSTRAINT [df_v1_ifj_etl_checkpoint_last_instance_id] DEFAULT (NULL),
    [version] bigint NOT NULL CONSTRAINT [df_v1_ifj_etl_checkpoint_version] DEFAULT ((0)),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_etl_checkpoint_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_etl_checkpoint_update_time] DEFAULT (sysutcdatetime()),
    [owner_instance_id] bigint NULL,
    [owner_token] varchar(64) COLLATE Latin1_General_100_BIN2 NULL,
    CONSTRAINT [ck_ifj_etl_checkpoint_task] CHECK ([task_id]>(0)),
    CONSTRAINT [ck_ifj_etl_checkpoint_version] CHECK ([version]>=(0)),
    CONSTRAINT [pk_ifj_etl_checkpoint] PRIMARY KEY CLUSTERED ([task_id], [checkpoint_key])
);

CREATE TABLE dbo.[ifj_etl_file_record] (
    [task_id] bigint NOT NULL,
    [checkpoint_key] nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [path_hash] varchar(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [content_digest] varchar(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [marker_value] nvarchar(2048) COLLATE Chinese_PRC_CI_AS NOT NULL,
    [last_instance_id] bigint NOT NULL,
    CONSTRAINT [pk_ifj_etl_file_record] PRIMARY KEY CLUSTERED ([task_id], [checkpoint_key], [path_hash])
);

CREATE TABLE dbo.[ifj_event] (
    [id] bigint NOT NULL,
    [event_code] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [source_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [source_key] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [business_key] varchar(256) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_event_business_key] DEFAULT (NULL),
    [correlation_key] varchar(256) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_event_correlation_key] DEFAULT (NULL),
    [idempotency_key] varchar(256) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [occurred_at] datetime2(3) NOT NULL,
    [arrived_at] datetime2(3) NOT NULL,
    [summary] nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_event_summary] DEFAULT (NULL),
    [payload] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NULL,
    [payload_digest] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_event_payload_digest] DEFAULT (NULL),
    [payload_bytes] int NOT NULL CONSTRAINT [df_v1_ifj_event_payload_bytes] DEFAULT ((0)),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_event_create_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [chk_ifj_event_payload_bytes] CHECK ([payload_bytes]>=(0)),
    CONSTRAINT [pk_ifj_event] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_event_source_idempotency] UNIQUE NONCLUSTERED ([source_key], [idempotency_key])
);

CREATE TABLE dbo.[ifj_event_binding] (
    [id] bigint NOT NULL,
    [target_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [target_id] bigint NOT NULL,
    [event_code] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [enabled] bit NOT NULL CONSTRAINT [df_v1_ifj_event_binding_enabled] DEFAULT ((1)),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_event_binding_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_event_binding_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_event_binding] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_event_binding_target] UNIQUE NONCLUSTERED ([target_type], [target_id], [event_code])
);

CREATE TABLE dbo.[ifj_event_wait] (
    [id] bigint NOT NULL,
    [instance_id] bigint NOT NULL,
    [root_instance_id] bigint NOT NULL,
    [attempt_no] int NOT NULL,
    [wait_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [status] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [event_code] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_event_wait_event_code] DEFAULT (NULL),
    [business_key] varchar(256) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_event_wait_business_key] DEFAULT (NULL),
    [correlation_key] varchar(256) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_event_wait_correlation_key] DEFAULT (NULL),
    [accept_after] datetime2(3) NOT NULL,
    [timeout_at] datetime2(3) NULL CONSTRAINT [df_v1_ifj_event_wait_timeout_at] DEFAULT (NULL),
    [matched_event_id] bigint NULL CONSTRAINT [df_v1_ifj_event_wait_matched_event_id] DEFAULT (NULL),
    [allowed_operator_ids] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NULL,
    [decision_by] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_event_wait_decision_by] DEFAULT (NULL),
    [decision_comment] nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_event_wait_decision_comment] DEFAULT (NULL),
    [resolved_at] datetime2(3) NULL CONSTRAINT [df_v1_ifj_event_wait_resolved_at] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_event_wait_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_event_wait_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [chk_ifj_event_wait_attempt] CHECK ([attempt_no]>=(0)),
    CONSTRAINT [chk_ifj_event_wait_status] CHECK ([status]='STOPPED' OR [status]='WITHDRAWN' OR [status]='TIMED_OUT' OR [status]='REJECTED' OR [status]='APPROVED' OR [status]='MATCHED' OR [status]='WAITING'),
    CONSTRAINT [chk_ifj_event_wait_type] CHECK ([wait_type]='APPROVAL' OR [wait_type]='EVENT'),
    CONSTRAINT [pk_ifj_event_wait] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_event_wait_attempt] UNIQUE NONCLUSTERED ([instance_id], [attempt_no])
);

CREATE TABLE dbo.[ifj_event_wait_operator] (
    [wait_id] bigint NOT NULL,
    [operator_id] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_event_wait_operator_create_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [chk_ifj_wait_operator_id] CHECK (len([operator_id])>(0)),
    CONSTRAINT [pk_ifj_event_wait_operator] PRIMARY KEY CLUSTERED ([wait_id], [operator_id])
);

CREATE TABLE dbo.[ifj_id_segment] (
    [segment_key] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [next_value] bigint NOT NULL,
    [segment_size] int NOT NULL,
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_id_segment_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [chk_ifj_id_segment_next_value] CHECK ([next_value]>(0)),
    CONSTRAINT [chk_ifj_id_segment_size] CHECK ([segment_size]>=(1) AND [segment_size]<=(100000)),
    CONSTRAINT [pk_ifj_id_segment] PRIMARY KEY CLUSTERED ([segment_key])
);

CREATE TABLE dbo.[ifj_instance] (
    [id] bigint NOT NULL,
    [node_id] bigint NULL CONSTRAINT [df_v1_ifj_instance_node_id] DEFAULT (NULL),
    [parent_instance_id] bigint NULL CONSTRAINT [df_v1_ifj_instance_parent_instance_id] DEFAULT (NULL),
    [root_instance_id] bigint NULL CONSTRAINT [df_v1_ifj_instance_root_instance_id] DEFAULT (NULL),
    [execution_fence] bigint NOT NULL CONSTRAINT [df_v1_ifj_instance_execution_fence] DEFAULT ((0)),
    [target_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [target_id] bigint NOT NULL,
    [trigger_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_instance_trigger_type] DEFAULT (NULL),
    [trigger_id] bigint NULL CONSTRAINT [df_v1_ifj_instance_trigger_id] DEFAULT (NULL),
    [status] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [progress] int NOT NULL CONSTRAINT [df_v1_ifj_instance_progress] DEFAULT ((0)),
    [retry_count] int NOT NULL CONSTRAINT [df_v1_ifj_instance_retry_count] DEFAULT ((0)),
    [start_time] datetime2(3) NULL CONSTRAINT [df_v1_ifj_instance_start_time] DEFAULT (NULL),
    [timeout_at] datetime2(3) NULL CONSTRAINT [df_v1_ifj_instance_timeout_at] DEFAULT (NULL),
    [end_time] datetime2(3) NULL CONSTRAINT [df_v1_ifj_instance_end_time] DEFAULT (NULL),
    [message] nvarchar(MAX) COLLATE Latin1_General_100_CI_AI_SC NULL,
    [error_code] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_instance_error_code] DEFAULT (NULL),
    [message_key] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_instance_message_key] DEFAULT (NULL),
    [message_args] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NULL CONSTRAINT [df_v1_ifj_instance_message_args] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_instance_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_instance_update_time] DEFAULT (sysutcdatetime()),
    [definition_id] bigint NULL CONSTRAINT [df_v1_ifj_instance_definition_id] DEFAULT (NULL),
    [definition_node_id] bigint NULL CONSTRAINT [df_v1_ifj_instance_definition_node_id] DEFAULT (NULL),
    [task_definition_id] bigint NULL CONSTRAINT [df_v1_ifj_instance_task_definition_id] DEFAULT (NULL),
    [input_variables] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NULL,
    [output_variables] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NULL,
    [wait_reason] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NULL,
    [permit_wait_deadline] datetime2(3) NULL,
    CONSTRAINT [chk_ifj_instance_execution_fence] CHECK ([execution_fence]>=(0)),
    CONSTRAINT [chk_ifj_instance_progress] CHECK ([progress]>=(0) AND [progress]<=(100)),
    CONSTRAINT [chk_ifj_instance_retry_count] CHECK ([retry_count]>=(0)),
    CONSTRAINT [chk_ifj_instance_status] CHECK ([status]='SKIPPED' OR [status]='TIMED_OUT' OR [status]='STOPPED' OR [status]='FAILED' OR [status]='SUCCESS' OR [status]='WAITING_EVENT' OR [status]='PAUSED' OR [status]='RUNNING' OR [status]='WAITING'),
    CONSTRAINT [pk_ifj_instance] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE dbo.[ifj_instance_log] (
    [id] bigint NOT NULL,
    [instance_id] bigint NOT NULL,
    [log_time] datetime2(3) NOT NULL,
    [level] varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [content] nvarchar(MAX) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    CONSTRAINT [pk_ifj_instance_log] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE dbo.[ifj_job] (
    [id] bigint NOT NULL,
    [name] nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    [timeout] int NULL CONSTRAINT [df_v1_ifj_job_timeout] DEFAULT (NULL),
    [enabled] bit NOT NULL CONSTRAINT [df_v1_ifj_job_enabled] DEFAULT ((1)),
    [description] nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_job_description] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_job_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_job_update_time] DEFAULT (sysutcdatetime()),
    [current_definition_id] bigint NULL CONSTRAINT [df_v1_ifj_job_current_definition_id] DEFAULT (NULL),
    [current_revision] bigint NULL CONSTRAINT [df_v1_ifj_job_current_revision] DEFAULT (NULL),
    [retired] bit NOT NULL CONSTRAINT [df_v1_ifj_job_retired] DEFAULT ((0)),
    [retired_time] datetime2(3) NULL CONSTRAINT [df_v1_ifj_job_retired_time] DEFAULT (NULL),
    CONSTRAINT [ck_ifj_job_current_pointer_pair] CHECK ([current_definition_id] IS NULL AND [current_revision] IS NULL OR [current_definition_id] IS NOT NULL AND [current_revision] IS NOT NULL),
    CONSTRAINT [ck_ifj_job_retired_state] CHECK ([retired]=(0) AND [retired_time] IS NULL OR [retired]=(1) AND [retired_time] IS NOT NULL),
    CONSTRAINT [pk_ifj_job] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE dbo.[ifj_job_definition] (
    [id] bigint NOT NULL,
    [job_id] bigint NOT NULL,
    [revision] bigint NOT NULL,
    [name] nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    [timeout] int NULL CONSTRAINT [df_v1_ifj_job_definition_timeout] DEFAULT (NULL),
    [enabled] bit NOT NULL,
    [description] nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_job_definition_description] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_job_definition_create_time] DEFAULT (sysutcdatetime()),
    [layout_json] nvarchar(MAX) COLLATE Chinese_PRC_CI_AS NULL,
    CONSTRAINT [ck_ifj_job_definition_revision] CHECK ([revision]>(0)),
    CONSTRAINT [pk_ifj_job_definition] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_job_definition_job_revision] UNIQUE NONCLUSTERED ([job_id], [revision])
);

CREATE TABLE dbo.[ifj_job_definition_edge] (
    [id] bigint NOT NULL,
    [definition_id] bigint NOT NULL,
    [from_node_id] bigint NOT NULL,
    [to_node_id] bigint NOT NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_job_definition_edge_create_time] DEFAULT (sysutcdatetime()),
    [route_key] varchar(64) COLLATE Chinese_PRC_CI_AS NULL,
    [route_priority] int NULL,
    [default_route] bit NOT NULL CONSTRAINT [df_ifj_job_def_edge_default] DEFAULT ((0)),
    [condition_version] int NULL,
    [condition_payload] varchar(MAX) COLLATE Chinese_PRC_CI_AS NULL,
    CONSTRAINT [ck_ifj_job_def_edge_route] CHECK ([route_key] IS NULL AND [route_priority] IS NULL AND [default_route]=(0) AND [condition_version] IS NULL AND [condition_payload] IS NULL OR [route_key] IS NOT NULL AND ([route_priority]>=(0) AND [route_priority]<=(10000)) AND ([default_route]=(1) AND [condition_version] IS NULL AND [condition_payload] IS NULL OR [default_route]=(0) AND [condition_version]=(1) AND [condition_payload] IS NOT NULL)),
    CONSTRAINT [pk_ifj_job_definition_edge] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_job_definition_edge_path] UNIQUE NONCLUSTERED ([definition_id], [from_node_id], [to_node_id])
);

CREATE TABLE dbo.[ifj_job_definition_group] (
    [definition_id] bigint NOT NULL,
    [group_code] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_ifj_job_def_group_create] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_job_definition_group] PRIMARY KEY CLUSTERED ([definition_id], [group_code])
);

CREATE TABLE dbo.[ifj_job_definition_node] (
    [id] bigint NOT NULL,
    [definition_id] bigint NOT NULL,
    [node_key] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [name] nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    [node_type] varchar(32) COLLATE Chinese_PRC_CI_AS NULL,
    [ref_id] bigint NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_job_definition_node_create_time] DEFAULT (sysutcdatetime()),
    [ref_definition_id] bigint NULL CONSTRAINT [df_v1_ifj_job_definition_node_ref_definition_id] DEFAULT (NULL),
    [ref_task_definition_id] bigint NULL CONSTRAINT [df_v1_ifj_job_definition_node_ref_task_definition_id] DEFAULT (NULL),
    [node_kind] varchar(16) COLLATE Chinese_PRC_CI_AS NOT NULL CONSTRAINT [df_ifj_job_def_node_kind] DEFAULT ('EXECUTION'),
    [gateway_mode] varchar(16) COLLATE Chinese_PRC_CI_AS NULL,
    CONSTRAINT [chk_ifj_job_definition_node_ref_snapshot] CHECK ([node_kind]='GATEWAY' AND [ref_definition_id] IS NULL AND [ref_task_definition_id] IS NULL OR [node_kind]='EXECUTION' AND ([node_type]='JOB' AND [ref_definition_id] IS NOT NULL AND [ref_task_definition_id] IS NULL OR [node_type]='TASK' AND [ref_definition_id] IS NULL AND [ref_task_definition_id] IS NOT NULL)),
    CONSTRAINT [ck_ifj_job_def_node_kind] CHECK ([node_kind]='EXECUTION' AND [gateway_mode] IS NULL AND ([node_type]='JOB' OR [node_type]='TASK') AND [ref_id] IS NOT NULL OR [node_kind]='GATEWAY' AND [gateway_mode]='EXCLUSIVE' AND [node_type] IS NULL AND [ref_id] IS NULL AND [ref_definition_id] IS NULL AND [ref_task_definition_id] IS NULL),
    CONSTRAINT [pk_ifj_job_definition_node] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_job_definition_node_key] UNIQUE NONCLUSTERED ([definition_id], [node_key])
);

CREATE TABLE dbo.[ifj_job_definition_node_policy] (
    [id] bigint NOT NULL,
    [definition_id] bigint NOT NULL,
    [node_id] bigint NOT NULL,
    [fail_strategy] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [execute_condition] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_job_definition_node_policy_create_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_job_definition_node_policy] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_job_definition_node_policy_node] UNIQUE NONCLUSTERED ([node_id])
);

CREATE TABLE dbo.[ifj_mail_channel] (
    [id] bigint NOT NULL,
    [name] nvarchar(128) COLLATE Chinese_PRC_CI_AS NOT NULL,
    [config] nvarchar(MAX) COLLATE Chinese_PRC_CI_AS NOT NULL,
    [enabled] bit NOT NULL,
    [revision] bigint NOT NULL,
    [create_time] datetime2(3) NOT NULL,
    [update_time] datetime2(3) NOT NULL,
    CONSTRAINT [PK__ifj_mail__3213E83F17B9385A] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE dbo.[ifj_maintenance_lease] (
    [lease_name] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [lease_owner] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_maintenance_lease_lease_owner] DEFAULT (NULL),
    [lease_token] varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_maintenance_lease_lease_token] DEFAULT (NULL),
    [lease_deadline] datetime2(3) NULL CONSTRAINT [df_v1_ifj_maintenance_lease_lease_deadline] DEFAULT (NULL),
    [fence_token] bigint NOT NULL CONSTRAINT [df_v1_ifj_maintenance_lease_fence_token] DEFAULT ((0)),
    [last_acquired_at] datetime2(3) NULL CONSTRAINT [df_v1_ifj_maintenance_lease_last_acquired_at] DEFAULT (NULL),
    [last_completed_at] datetime2(3) NULL CONSTRAINT [df_v1_ifj_maintenance_lease_last_completed_at] DEFAULT (NULL),
    [last_success] bit NULL CONSTRAINT [df_v1_ifj_maintenance_lease_last_success] DEFAULT (NULL),
    [last_failure_code] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_maintenance_lease_last_failure_code] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_maintenance_lease_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_maintenance_lease_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [chk_ifj_maintenance_lease_fence] CHECK ([fence_token]>=(0)),
    CONSTRAINT [chk_ifj_maintenance_lease_holder] CHECK ([lease_owner] IS NULL AND [lease_token] IS NULL AND [lease_deadline] IS NULL OR [lease_owner] IS NOT NULL AND [lease_token] IS NOT NULL AND [lease_deadline] IS NOT NULL),
    CONSTRAINT [pk_ifj_maintenance_lease] PRIMARY KEY CLUSTERED ([lease_name])
);

CREATE TABLE dbo.[ifj_notification_outbox] (
    [id] bigint NOT NULL,
    [deduplication_key] varchar(191) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [notify_config_id] bigint NOT NULL,
    [notify_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_notification_outbox_notify_type] DEFAULT (NULL),
    [config_snapshot] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NULL,
    [config_enabled] bit NULL CONSTRAINT [df_v1_ifj_notification_outbox_config_enabled] DEFAULT (NULL),
    [title] nvarchar(255) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_notification_outbox_title] DEFAULT (NULL),
    [subject] nvarchar(255) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    [content] nvarchar(MAX) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    [source_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [correlation_id] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_notification_outbox_correlation_id] DEFAULT (NULL),
    [operator_id] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_notification_outbox_operator_id] DEFAULT (NULL),
    [target_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_notification_outbox_target_type] DEFAULT (NULL),
    [target_id] bigint NULL CONSTRAINT [df_v1_ifj_notification_outbox_target_id] DEFAULT (NULL),
    [instance_id] bigint NULL CONSTRAINT [df_v1_ifj_notification_outbox_instance_id] DEFAULT (NULL),
    [notify_binding_id] bigint NULL CONSTRAINT [df_v1_ifj_notification_outbox_notify_binding_id] DEFAULT (NULL),
    [status] varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [available_at] datetime2(3) NOT NULL,
    [lease_owner] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_notification_outbox_lease_owner] DEFAULT (NULL),
    [claim_token] varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_notification_outbox_claim_token] DEFAULT (NULL),
    [lease_deadline] datetime2(3) NULL CONSTRAINT [df_v1_ifj_notification_outbox_lease_deadline] DEFAULT (NULL),
    [attempt_count] int NOT NULL CONSTRAINT [df_v1_ifj_notification_outbox_attempt_count] DEFAULT ((0)),
    [max_attempts] int NOT NULL,
    [last_failure_code] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_notification_outbox_last_failure_code] DEFAULT (NULL),
    [last_failure_summary] nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_notification_outbox_last_failure_summary] DEFAULT (NULL),
    [delivered_at] datetime2(3) NULL CONSTRAINT [df_v1_ifj_notification_outbox_delivered_at] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_notification_outbox_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_notification_outbox_update_time] DEFAULT (sysutcdatetime()),
    [content_version] int NOT NULL CONSTRAINT [df_v1_ifj_notification_outbox_content_version] DEFAULT ((0)),
    [http_status] int NULL,
    CONSTRAINT [chk_ifj_notification_outbox_attempts] CHECK ([attempt_count]>=(0) AND ([max_attempts]>=(1) AND [max_attempts]<=(100))),
    CONSTRAINT [chk_ifj_notification_outbox_config_snapshot] CHECK ([notify_type] IS NULL AND [config_snapshot] IS NULL AND [config_enabled] IS NULL OR [notify_type] IS NOT NULL AND [config_snapshot] IS NOT NULL AND [config_enabled] IS NOT NULL),
    CONSTRAINT [chk_ifj_notification_outbox_delivery] CHECK ([status]='DELIVERED' AND [delivered_at] IS NOT NULL OR [status]<>'DELIVERED' AND [delivered_at] IS NULL),
    CONSTRAINT [chk_ifj_notification_outbox_lease] CHECK ([status]='CLAIMED' AND [lease_owner] IS NOT NULL AND [claim_token] IS NOT NULL AND [lease_deadline] IS NOT NULL OR [status]<>'CLAIMED' AND [lease_owner] IS NULL AND [claim_token] IS NULL AND [lease_deadline] IS NULL),
    CONSTRAINT [chk_ifj_notification_outbox_status] CHECK ([status]='DEAD' OR [status]='DELIVERED' OR [status]='RETRY_WAIT' OR [status]='CLAIMED' OR [status]='READY'),
    CONSTRAINT [pk_ifj_notification_outbox] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_notification_outbox_deduplication] UNIQUE NONCLUSTERED ([deduplication_key])
);

CREATE TABLE dbo.[ifj_notify_binding] (
    [id] bigint NOT NULL,
    [target_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [target_id] bigint NOT NULL,
    [notify_config_id] bigint NOT NULL,
    [notify_policy] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_notify_binding_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_notify_binding_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_notify_binding] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_notify_binding_target] UNIQUE NONCLUSTERED ([target_type], [target_id], [notify_config_id])
);

CREATE TABLE dbo.[ifj_notify_config] (
    [id] bigint NOT NULL,
    [name] nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    [type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [config] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [enabled] bit NOT NULL CONSTRAINT [df_v1_ifj_notify_config_enabled] DEFAULT ((1)),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_notify_config_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_notify_config_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_notify_config] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE dbo.[ifj_notify_rate_limit] (
    [target_key] varchar(64) COLLATE Chinese_PRC_CI_AS NOT NULL,
    [next_allowed_at] bigint NOT NULL,
    CONSTRAINT [PK__ifj_noti__CF5F43DEF25F6639] PRIMARY KEY CLUSTERED ([target_key])
);

CREATE TABLE dbo.[ifj_notify_send_log] (
    [id] bigint NOT NULL,
    [source_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [correlation_id] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_notify_send_log_correlation_id] DEFAULT (NULL),
    [operator_id] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_notify_send_log_operator_id] DEFAULT (NULL),
    [target_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_notify_send_log_target_type] DEFAULT (NULL),
    [target_id] bigint NULL CONSTRAINT [df_v1_ifj_notify_send_log_target_id] DEFAULT (NULL),
    [instance_id] bigint NULL CONSTRAINT [df_v1_ifj_notify_send_log_instance_id] DEFAULT (NULL),
    [notify_binding_id] bigint NULL CONSTRAINT [df_v1_ifj_notify_send_log_notify_binding_id] DEFAULT (NULL),
    [notify_config_id] bigint NOT NULL,
    [notify_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_notify_send_log_notify_type] DEFAULT (NULL),
    [result] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [failure_code] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_notify_send_log_failure_code] DEFAULT (NULL),
    [failure_summary] nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_notify_send_log_failure_summary] DEFAULT (NULL),
    [duration_ms] bigint NULL CONSTRAINT [df_v1_ifj_notify_send_log_duration_ms] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_notify_send_log_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_notify_send_log_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_notify_send_log] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE dbo.[ifj_operation_audit] (
    [id] bigint NOT NULL,
    [occurred_at] datetime2(3) NOT NULL,
    [actor_login_id] nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [actor_name] nvarchar(128) COLLATE Latin1_General_100_BIN2 NULL,
    [action_code] nvarchar(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [target_type] nvarchar(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [target_id] nvarchar(128) COLLATE Latin1_General_100_BIN2 NULL,
    [result_code] nvarchar(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [trace_id] nvarchar(32) COLLATE Latin1_General_100_BIN2 NULL,
    [request_id] nvarchar(128) COLLATE Latin1_General_100_BIN2 NULL,
    [changed_fields] nvarchar(1024) COLLATE Latin1_General_100_BIN2 NULL,
    CONSTRAINT [pk_ifj_operation_audit] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE dbo.[ifj_parameter_binding] (
    [target_type] varchar(16) COLLATE Chinese_PRC_CI_AS NOT NULL,
    [target_id] bigint NOT NULL,
    [template_id] bigint NOT NULL,
    CONSTRAINT [ck_param_binding_type] CHECK ([target_type]='JOB' OR [target_type]='TASK'),
    CONSTRAINT [PK__ifj_para__F5FD64D9ED50CBCF] PRIMARY KEY CLUSTERED ([target_type], [target_id])
);

CREATE TABLE dbo.[ifj_parameter_template] (
    [id] bigint NOT NULL,
    [name] nvarchar(128) COLLATE Chinese_PRC_CI_AS NOT NULL,
    [description] nvarchar(500) COLLATE Chinese_PRC_CI_AS NULL,
    [fields_json] nvarchar(MAX) COLLATE Chinese_PRC_CI_AS NOT NULL,
    [revision] bigint NOT NULL,
    CONSTRAINT [PK__ifj_para__3213E83F1186A015] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UQ__ifj_para__72E12F1BA67BD278] UNIQUE NONCLUSTERED ([name])
);

CREATE TABLE dbo.[ifj_plugin_concurrency_policy] (
    [plugin_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [max_concurrency] int NOT NULL,
    [version] bigint NOT NULL CONSTRAINT [df_ifj_plugin_concurrency_ver] DEFAULT ((1)),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_ifj_plugin_concurrency_ct] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_ifj_plugin_concurrency_ut] DEFAULT (sysutcdatetime()),
    CONSTRAINT [chk_ifj_plugin_concurrency_max] CHECK ([max_concurrency]>=(1) AND [max_concurrency]<=(10000)),
    CONSTRAINT [chk_ifj_plugin_concurrency_ver] CHECK ([version]>(0)),
    CONSTRAINT [pk_ifj_plugin_concurrency_policy] PRIMARY KEY CLUSTERED ([plugin_type])
);

CREATE TABLE dbo.[ifj_resource] (
    [id] bigint NOT NULL,
    [code] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [name] nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    [type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [config] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [enabled] bit NOT NULL CONSTRAINT [df_v1_ifj_resource_enabled] DEFAULT ((1)),
    [description] nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_resource_description] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_resource_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_resource_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_resource] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_resource_code] UNIQUE NONCLUSTERED ([code])
);

CREATE TABLE dbo.[ifj_runtime_command] (
    [request_id] varchar(191) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [instance_id] bigint NOT NULL,
    [root_instance_id] bigint NOT NULL,
    [trigger_id] bigint NOT NULL,
    [command_type] varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [status] varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [requested_by] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [result_message] varchar(500) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_runtime_command_result_message] DEFAULT (NULL),
    [available_at] datetime2(3) NOT NULL,
    [expires_at] datetime2(3) NOT NULL,
    [processing_owner] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_runtime_command_processing_owner] DEFAULT (NULL),
    [processing_token] varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_runtime_command_processing_token] DEFAULT (NULL),
    [processing_fence] bigint NULL CONSTRAINT [df_v1_ifj_runtime_command_processing_fence] DEFAULT (NULL),
    [processed_at] datetime2(3) NULL CONSTRAINT [df_v1_ifj_runtime_command_processed_at] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_runtime_command_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_runtime_command_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [chk_ifj_runtime_command_processing] CHECK ([status]='PROCESSING' AND [processing_owner] IS NOT NULL AND [processing_token] IS NOT NULL AND [processing_fence] IS NOT NULL OR [status]<>'PROCESSING' AND [processing_owner] IS NULL AND [processing_token] IS NULL AND [processing_fence] IS NULL),
    CONSTRAINT [chk_ifj_runtime_command_status] CHECK ([status]='EXPIRED' OR [status]='FAILED' OR [status]='REJECTED' OR [status]='SUCCEEDED' OR [status]='PROCESSING' OR [status]='PENDING'),
    CONSTRAINT [chk_ifj_runtime_command_type] CHECK ([command_type]='RETRY' OR [command_type]='RESUME' OR [command_type]='PAUSE' OR [command_type]='STOP'),
    CONSTRAINT [pk_ifj_runtime_command] PRIMARY KEY CLUSTERED ([request_id])
);

CREATE TABLE dbo.[ifj_runtime_guard] (
    [guard_key] varchar(64) COLLATE Chinese_PRC_CI_AS NOT NULL,
    [revision] bigint NOT NULL,
    CONSTRAINT [pk_ifj_runtime_guard] PRIMARY KEY CLUSTERED ([guard_key])
);

CREATE TABLE dbo.[ifj_schedule] (
    [id] bigint NOT NULL,
    [target_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [target_id] bigint NOT NULL,
    [schedule_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [cron_expr] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_schedule_cron_expr] DEFAULT (NULL),
    [interval_sec] int NULL CONSTRAINT [df_v1_ifj_schedule_interval_sec] DEFAULT (NULL),
    [calendar_id] bigint NULL CONSTRAINT [df_v1_ifj_schedule_calendar_id] DEFAULT (NULL),
    [start_time] datetime2(3) NULL CONSTRAINT [df_v1_ifj_schedule_start_time] DEFAULT (NULL),
    [end_time] datetime2(3) NULL CONSTRAINT [df_v1_ifj_schedule_end_time] DEFAULT (NULL),
    [enabled] bit NOT NULL CONSTRAINT [df_v1_ifj_schedule_enabled] DEFAULT ((1)),
    [last_fire_time] datetime2(3) NULL CONSTRAINT [df_v1_ifj_schedule_last_fire_time] DEFAULT (NULL),
    [next_fire_time] datetime2(3) NULL CONSTRAINT [df_v1_ifj_schedule_next_fire_time] DEFAULT (NULL),
    [fire_claim_token] varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_schedule_fire_claim_token] DEFAULT (NULL),
    [fire_claimed_at] datetime2(3) NULL CONSTRAINT [df_v1_ifj_schedule_fire_claimed_at] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_schedule_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_schedule_update_time] DEFAULT (sysutcdatetime()),
    [variables] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NULL,
    [misfire_policy] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NULL,
    [misfire_grace_sec] int NULL,
    [max_catchup_count] int NULL,
    [max_queue_delay_sec] int NULL,
    [revision] bigint NOT NULL CONSTRAINT [df_ifj_schedule_revision] DEFAULT ((0)),
    [parameter_form] nvarchar(MAX) COLLATE Chinese_PRC_CI_AS NULL,
    CONSTRAINT [chk_ifj_schedule_catchup_v24] CHECK ([max_catchup_count] IS NULL OR [max_catchup_count]>=(1) AND [max_catchup_count]<=(1000)),
    CONSTRAINT [chk_ifj_schedule_delay_v24] CHECK ([max_queue_delay_sec] IS NULL OR [max_queue_delay_sec]>=(1) AND [max_queue_delay_sec]<=(31536000)),
    CONSTRAINT [chk_ifj_schedule_fire_claim] CHECK ([fire_claim_token] IS NULL AND [fire_claimed_at] IS NULL OR [fire_claim_token] IS NOT NULL AND [fire_claimed_at] IS NOT NULL),
    CONSTRAINT [chk_ifj_schedule_grace_v24] CHECK ([misfire_grace_sec] IS NULL OR [misfire_grace_sec]>=(0)),
    CONSTRAINT [chk_ifj_schedule_misfire_v24] CHECK ([misfire_policy] IS NULL OR ([misfire_policy]='CATCH_UP_BOUNDED' OR [misfire_policy]='FIRE_ONCE' OR [misfire_policy]='SKIP')),
    CONSTRAINT [pk_ifj_schedule] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE dbo.[ifj_schedule_misfire_audit] (
    [id] bigint NOT NULL,
    [schedule_id] bigint NULL,
    [fire_claim_token] varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [policy] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [observed_at] datetime2(3) NOT NULL,
    [first_due_time] datetime2(3) NOT NULL,
    [last_due_time] datetime2(3) NULL,
    [due_count] int NOT NULL,
    [count_capped] bit NOT NULL,
    [created_count] int NOT NULL,
    [skipped_count] int NOT NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_ifj_misfire_audit_ct] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_schedule_misfire_audit] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_schedule_misfire_claim] UNIQUE NONCLUSTERED ([fire_claim_token])
);

CREATE TABLE dbo.[ifj_schema_capability] (
    [capability_key] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [capability_version] int NOT NULL,
    [enabled] bit NOT NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_schema_capability_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_schema_capability_update_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [chk_ifj_schema_cap_enabled] CHECK ([enabled]=(1) OR [enabled]=(0)),
    CONSTRAINT [chk_ifj_schema_cap_version] CHECK ([capability_version]>(0)),
    CONSTRAINT [pk_ifj_schema_capability] PRIMARY KEY CLUSTERED ([capability_key])
);

CREATE TABLE dbo.[ifj_task] (
    [id] bigint NOT NULL,
    [name] nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    [plugin_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [plugin_config] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [retry_times] int NOT NULL CONSTRAINT [df_v1_ifj_task_retry_times] DEFAULT ((0)),
    [retry_interval] int NOT NULL CONSTRAINT [df_v1_ifj_task_retry_interval] DEFAULT ((0)),
    [timeout] int NULL CONSTRAINT [df_v1_ifj_task_timeout] DEFAULT (NULL),
    [enabled] bit NOT NULL CONSTRAINT [df_v1_ifj_task_enabled] DEFAULT ((1)),
    [description] nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_task_description] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_task_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_task_update_time] DEFAULT (sysutcdatetime()),
    [current_task_definition_id] bigint NULL CONSTRAINT [df_v1_ifj_task_current_task_definition_id] DEFAULT (NULL),
    [current_revision] bigint NULL CONSTRAINT [df_v1_ifj_task_current_revision] DEFAULT (NULL),
    [retired] bit NOT NULL CONSTRAINT [df_v1_ifj_task_retired] DEFAULT ((0)),
    [retired_time] datetime2(3) NULL CONSTRAINT [df_v1_ifj_task_retired_time] DEFAULT (NULL),
    CONSTRAINT [ck_ifj_task_current_pointer_pair] CHECK ([current_task_definition_id] IS NULL AND [current_revision] IS NULL OR [current_task_definition_id] IS NOT NULL AND [current_revision] IS NOT NULL),
    CONSTRAINT [ck_ifj_task_current_revision_positive] CHECK ([current_revision] IS NULL OR [current_revision]>(0)),
    CONSTRAINT [ck_ifj_task_retired_state] CHECK ([retired]=(0) AND [retired_time] IS NULL OR [retired]=(1) AND [retired_time] IS NOT NULL),
    CONSTRAINT [pk_ifj_task] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE dbo.[ifj_task_concurrency_policy] (
    [task_id] bigint NOT NULL,
    [task_max_concurrency] int NULL,
    [version] bigint NOT NULL CONSTRAINT [df_ifj_task_concurrency_ver] DEFAULT ((1)),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_ifj_task_concurrency_ct] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_ifj_task_concurrency_ut] DEFAULT (sysutcdatetime()),
    CONSTRAINT [chk_ifj_task_concurrency_max] CHECK ([task_max_concurrency] IS NULL OR [task_max_concurrency]>=(1) AND [task_max_concurrency]<=(10000)),
    CONSTRAINT [chk_ifj_task_concurrency_ver] CHECK ([version]>(0)),
    CONSTRAINT [pk_ifj_task_concurrency_policy] PRIMARY KEY CLUSTERED ([task_id])
);

CREATE TABLE dbo.[ifj_task_concurrency_resource] (
    [task_id] bigint NOT NULL,
    [resource_id] bigint NOT NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_ifj_task_concurrency_res_ct] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_task_concurrency_resource] PRIMARY KEY CLUSTERED ([task_id], [resource_id])
);

CREATE TABLE dbo.[ifj_task_definition] (
    [id] bigint NOT NULL,
    [task_id] bigint NOT NULL,
    [revision] bigint NOT NULL,
    [name] nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    [plugin_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [plugin_config] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [retry_times] int NOT NULL,
    [retry_interval] int NOT NULL,
    [timeout] int NULL CONSTRAINT [df_v1_ifj_task_definition_timeout] DEFAULT (NULL),
    [enabled] bit NOT NULL,
    [description] nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_task_definition_description] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_task_definition_create_time] DEFAULT (sysutcdatetime()),
    CONSTRAINT [ck_ifj_task_definition_revision] CHECK ([revision]>(0)),
    CONSTRAINT [pk_ifj_task_definition] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_task_definition_task_revision] UNIQUE NONCLUSTERED ([task_id], [revision])
);

CREATE TABLE dbo.[ifj_task_definition_group] (
    [definition_id] bigint NOT NULL,
    [group_code] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_ifj_task_def_group_create] DEFAULT (sysutcdatetime()),
    CONSTRAINT [pk_ifj_task_definition_group] PRIMARY KEY CLUSTERED ([definition_id], [group_code])
);

CREATE TABLE dbo.[ifj_trigger] (
    [id] bigint NOT NULL,
    [schedule_id] bigint NULL CONSTRAINT [df_v1_ifj_trigger_schedule_id] DEFAULT (NULL),
    [target_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [target_id] bigint NOT NULL,
    [trigger_type] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [idempotency_key] varchar(191) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [event_code] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_trigger_event_code] DEFAULT (NULL),
    [status] varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [trigger_time] datetime2(3) NOT NULL,
    [message] nvarchar(MAX) COLLATE Latin1_General_100_CI_AI_SC NULL,
    [error_code] varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_trigger_error_code] DEFAULT (NULL),
    [message_key] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_trigger_message_key] DEFAULT (NULL),
    [message_args] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NULL CONSTRAINT [df_v1_ifj_trigger_message_args] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_trigger_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_trigger_update_time] DEFAULT (sysutcdatetime()),
    [definition_id] bigint NULL CONSTRAINT [df_v1_ifj_trigger_definition_id] DEFAULT (NULL),
    [task_definition_id] bigint NULL CONSTRAINT [df_v1_ifj_trigger_task_definition_id] DEFAULT (NULL),
    [variables] nvarchar(MAX) COLLATE Latin1_General_100_BIN2 NULL,
    [event_id] bigint NULL CONSTRAINT [df_v1_ifj_trigger_event_id] DEFAULT (NULL),
    [event_binding_id] bigint NULL CONSTRAINT [df_v1_ifj_trigger_event_binding_id] DEFAULT (NULL),
    [business_date] varchar(10) COLLATE Chinese_PRC_CI_AS NULL,
    [business_time] varchar(48) COLLATE Chinese_PRC_CI_AS NULL,
    [business_zone_id] varchar(128) COLLATE Chinese_PRC_CI_AS NULL,
    CONSTRAINT [chk_ifj_trigger_status] CHECK ([status]='SKIPPED' OR [status]='TIMED_OUT' OR [status]='STOPPED' OR [status]='FAILED' OR [status]='SUCCESS' OR [status]='RUNNING' OR [status]='WAITING'),
    CONSTRAINT [pk_ifj_trigger] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [uk_ifj_trigger_idempotency_key] UNIQUE NONCLUSTERED ([idempotency_key])
);

CREATE TABLE dbo.[ifj_trigger_work_item] (
    [trigger_id] bigint NOT NULL,
    [root_instance_id] bigint NULL CONSTRAINT [df_v1_ifj_trigger_work_item_root_instance_id] DEFAULT (NULL),
    [status] varchar(16) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    [available_at] datetime2(3) NOT NULL,
    [lease_owner] varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_trigger_work_item_lease_owner] DEFAULT (NULL),
    [claim_token] varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 NULL CONSTRAINT [df_v1_ifj_trigger_work_item_claim_token] DEFAULT (NULL),
    [lease_deadline] datetime2(3) NULL CONSTRAINT [df_v1_ifj_trigger_work_item_lease_deadline] DEFAULT (NULL),
    [fence_token] bigint NOT NULL CONSTRAINT [df_v1_ifj_trigger_work_item_fence_token] DEFAULT ((0)),
    [attempt_count] int NOT NULL CONSTRAINT [df_v1_ifj_trigger_work_item_attempt_count] DEFAULT ((0)),
    [max_attempts] int NOT NULL,
    [last_error] nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL CONSTRAINT [df_v1_ifj_trigger_work_item_last_error] DEFAULT (NULL),
    [create_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_trigger_work_item_create_time] DEFAULT (sysutcdatetime()),
    [update_time] datetime2(3) NOT NULL CONSTRAINT [df_v1_ifj_trigger_work_item_update_time] DEFAULT (sysutcdatetime()),
    [expires_at] datetime2(3) NULL,
    CONSTRAINT [chk_ifj_trigger_work_item_attempts] CHECK ([attempt_count]>=(0) AND ([max_attempts]>=(1) AND [max_attempts]<=(100))),
    CONSTRAINT [chk_ifj_trigger_work_item_fence] CHECK ([fence_token]>=(0)),
    CONSTRAINT [chk_ifj_trigger_work_item_lease] CHECK ([status]='CLAIMED' AND [lease_owner] IS NOT NULL AND [claim_token] IS NOT NULL AND [lease_deadline] IS NOT NULL OR [status]<>'CLAIMED' AND [lease_owner] IS NULL AND [claim_token] IS NULL AND [lease_deadline] IS NULL),
    CONSTRAINT [chk_ifj_trigger_work_item_status] CHECK ([status]='DEAD' OR [status]='COMPLETED' OR [status]='RETRY_WAIT' OR [status]='SUSPENDED' OR [status]='CLAIMED' OR [status]='READY'),
    CONSTRAINT [pk_ifj_trigger_work_item] PRIMARY KEY CLUSTERED ([trigger_id])
);

CREATE NONCLUSTERED INDEX [idx_ifj_app_run_status_heartbeat] ON dbo.[ifj_app_run] ([status], [heartbeat_at], [run_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_app_run_instance_heartbeat] ON dbo.[ifj_app_run] ([instance_name], [status], [heartbeat_at], [run_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_auth_role] ON dbo.[ifj_auth_user] ([role_code], [enabled]);

CREATE NONCLUSTERED INDEX [idx_ifj_cluster_state_expiry] ON dbo.[ifj_cluster_state] ([expires_at], [state_key]);

CREATE UNIQUE NONCLUSTERED INDEX [uk_ifj_concurrency_permit_token] ON dbo.[ifj_concurrency_permit] ([permit_token]) WHERE ([permit_token] IS NOT NULL);

CREATE NONCLUSTERED INDEX [idx_ifj_concurrency_permit_lease] ON dbo.[ifj_concurrency_permit] ([lease_deadline], [scope_key], [slot_no]);

CREATE NONCLUSTERED INDEX [idx_ifj_concurrency_permit_root] ON dbo.[ifj_concurrency_permit] ([owner_root_instance_id], [lease_deadline]);

CREATE NONCLUSTERED INDEX [idx_ifj_dag_gateway_decision_node] ON dbo.[ifj_dag_gateway_decision] ([definition_node_id], [job_instance_id], [evaluation_no]);

CREATE NONCLUSTERED INDEX [idx_ifj_etl_checkpoint_instance] ON dbo.[ifj_etl_checkpoint] ([last_instance_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_event_code_business] ON dbo.[ifj_event] ([event_code], [business_key], [arrived_at], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_event_code_correlation] ON dbo.[ifj_event] ([event_code], [correlation_key], [arrived_at], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_event_arrived] ON dbo.[ifj_event] ([arrived_at], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_event_binding_event] ON dbo.[ifj_event_binding] ([event_code], [enabled]);

CREATE NONCLUSTERED INDEX [fk_ifj_event_wait_event] ON dbo.[ifj_event_wait] ([matched_event_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_event_wait_match_business] ON dbo.[ifj_event_wait] ([status], [event_code], [business_key], [accept_after], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_event_wait_match_correlation] ON dbo.[ifj_event_wait] ([status], [event_code], [correlation_key], [accept_after], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_event_wait_root_status] ON dbo.[ifj_event_wait] ([root_instance_id], [status]);

CREATE NONCLUSTERED INDEX [idx_ifj_event_wait_timeout] ON dbo.[ifj_event_wait] ([status], [timeout_at], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_event_root_status_v24] ON dbo.[ifj_event_wait] ([status], [root_instance_id], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_wait_operator_lookup] ON dbo.[ifj_event_wait_operator] ([operator_id], [wait_id]);

CREATE UNIQUE NONCLUSTERED INDEX [uk_ifj_instance_parent_node] ON dbo.[ifj_instance] ([parent_instance_id], [node_id]) WHERE ([parent_instance_id] IS NOT NULL AND [node_id] IS NOT NULL);

CREATE UNIQUE NONCLUSTERED INDEX [uk_ifj_instance_parent_definition_node] ON dbo.[ifj_instance] ([parent_instance_id], [definition_node_id]) WHERE ([parent_instance_id] IS NOT NULL AND [definition_node_id] IS NOT NULL);

CREATE NONCLUSTERED INDEX [idx_ifj_instance_root_status] ON dbo.[ifj_instance] ([root_instance_id], [status]);

CREATE NONCLUSTERED INDEX [idx_ifj_instance_target] ON dbo.[ifj_instance] ([target_type], [target_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_instance_trigger_id] ON dbo.[ifj_instance] ([trigger_id]);

CREATE NONCLUSTERED INDEX [fk_ifj_instance_definition_node] ON dbo.[ifj_instance] ([definition_node_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_instance_definition_status] ON dbo.[ifj_instance] ([definition_id], [status]);

CREATE NONCLUSTERED INDEX [idx_ifj_instance_task_definition_status] ON dbo.[ifj_instance] ([task_definition_id], [status]);

CREATE NONCLUSTERED INDEX [idx_ifj_instance_status_update] ON dbo.[ifj_instance] ([status], [update_time], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_instance_error_update] ON dbo.[ifj_instance] ([error_code], [update_time], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_instance_root_timeout] ON dbo.[ifj_instance] ([parent_instance_id], [status], [timeout_at], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_instance_root_history_v25] ON dbo.[ifj_instance] ([parent_instance_id], [create_time], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_instance_log_instance_time] ON dbo.[ifj_instance_log] ([instance_id], [log_time]);

CREATE NONCLUSTERED INDEX [idx_ifj_job_enabled] ON dbo.[ifj_job] ([enabled]);

CREATE NONCLUSTERED INDEX [idx_ifj_job_current_definition_id] ON dbo.[ifj_job] ([current_definition_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_job_definition_job_time] ON dbo.[ifj_job_definition] ([job_id], [create_time], [id]);

CREATE NONCLUSTERED INDEX [fk_ifj_job_definition_edge_from_node] ON dbo.[ifj_job_definition_edge] ([from_node_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_job_definition_edge_to_node] ON dbo.[ifj_job_definition_edge] ([to_node_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_job_def_group_code] ON dbo.[ifj_job_definition_group] ([group_code], [definition_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_job_definition_node_ref] ON dbo.[ifj_job_definition_node] ([node_type], [ref_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_job_definition_node_ref_job_definition] ON dbo.[ifj_job_definition_node] ([ref_definition_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_job_definition_node_ref_task_definition] ON dbo.[ifj_job_definition_node] ([ref_task_definition_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_job_definition_node_policy_definition] ON dbo.[ifj_job_definition_node_policy] ([definition_id]);

CREATE NONCLUSTERED INDEX [idx_mail_channel_updated] ON dbo.[ifj_mail_channel] ([update_time], [id]);

CREATE UNIQUE NONCLUSTERED INDEX [uk_ifj_maintenance_lease_token] ON dbo.[ifj_maintenance_lease] ([lease_token]) WHERE ([lease_token] IS NOT NULL);

CREATE NONCLUSTERED INDEX [idx_ifj_maintenance_lease_deadline] ON dbo.[ifj_maintenance_lease] ([lease_deadline], [lease_name]);

CREATE UNIQUE NONCLUSTERED INDEX [uk_ifj_notification_outbox_claim_token] ON dbo.[ifj_notification_outbox] ([claim_token]) WHERE ([claim_token] IS NOT NULL);

CREATE NONCLUSTERED INDEX [idx_ifj_notification_outbox_dispatch] ON dbo.[ifj_notification_outbox] ([available_at], [id]) INCLUDE ([status], [lease_deadline], [attempt_count], [max_attempts]);

CREATE NONCLUSTERED INDEX [idx_ifj_notification_outbox_lease] ON dbo.[ifj_notification_outbox] ([status], [lease_deadline], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_notification_outbox_instance] ON dbo.[ifj_notification_outbox] ([instance_id], [status], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_notification_outbox_created] ON dbo.[ifj_notification_outbox] ([create_time], [id]);

CREATE NONCLUSTERED INDEX [fk_ifj_notify_binding_config] ON dbo.[ifj_notify_binding] ([notify_config_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_notify_binding_target] ON dbo.[ifj_notify_binding] ([target_type], [target_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_notify_config_type_enabled] ON dbo.[ifj_notify_config] ([type], [enabled]);

CREATE NONCLUSTERED INDEX [idx_ifj_notify_send_log_create_time] ON dbo.[ifj_notify_send_log] ([create_time], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_notify_send_log_config_time] ON dbo.[ifj_notify_send_log] ([notify_config_id], [create_time]);

CREATE NONCLUSTERED INDEX [idx_ifj_notify_send_log_target_time] ON dbo.[ifj_notify_send_log] ([target_type], [target_id], [create_time]);

CREATE NONCLUSTERED INDEX [idx_ifj_notify_send_log_instance_time] ON dbo.[ifj_notify_send_log] ([instance_id], [create_time]);

CREATE NONCLUSTERED INDEX [idx_ifj_notify_send_log_result_time] ON dbo.[ifj_notify_send_log] ([result], [create_time]);

CREATE NONCLUSTERED INDEX [idx_ifj_audit_time] ON dbo.[ifj_operation_audit] ([occurred_at], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_audit_actor] ON dbo.[ifj_operation_audit] ([actor_login_id], [occurred_at], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_audit_target] ON dbo.[ifj_operation_audit] ([target_type], [target_id], [occurred_at], [id]);

CREATE NONCLUSTERED INDEX [idx_param_binding_template] ON dbo.[ifj_parameter_binding] ([template_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_resource_type_enabled] ON dbo.[ifj_resource] ([type], [enabled]);

CREATE UNIQUE NONCLUSTERED INDEX [uk_ifj_runtime_command_processing_token] ON dbo.[ifj_runtime_command] ([processing_token]) WHERE ([processing_token] IS NOT NULL);

CREATE NONCLUSTERED INDEX [fk_ifj_runtime_command_instance] ON dbo.[ifj_runtime_command] ([instance_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_runtime_command_dispatch] ON dbo.[ifj_runtime_command] ([trigger_id], [create_time], [request_id]) INCLUDE ([status], [available_at], [expires_at], [processing_fence]);

CREATE NONCLUSTERED INDEX [idx_ifj_runtime_command_root] ON dbo.[ifj_runtime_command] ([root_instance_id], [create_time]);

CREATE NONCLUSTERED INDEX [idx_ifj_runtime_command_expiration] ON dbo.[ifj_runtime_command] ([status], [expires_at], [request_id]);

CREATE UNIQUE NONCLUSTERED INDEX [uk_ifj_schedule_fire_claim_token] ON dbo.[ifj_schedule] ([fire_claim_token]) WHERE ([fire_claim_token] IS NOT NULL);

CREATE NONCLUSTERED INDEX [fk_ifj_schedule_calendar] ON dbo.[ifj_schedule] ([calendar_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_schedule_target] ON dbo.[ifj_schedule] ([target_type], [target_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_schedule_fire] ON dbo.[ifj_schedule] ([enabled], [next_fire_time]);

CREATE NONCLUSTERED INDEX [idx_ifj_schedule_due_v24] ON dbo.[ifj_schedule] ([enabled], [next_fire_time], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_misfire_schedule_v24] ON dbo.[ifj_schedule_misfire_audit] ([schedule_id], [observed_at], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_task_enabled_plugin_type] ON dbo.[ifj_task] ([enabled], [plugin_type]);

CREATE NONCLUSTERED INDEX [idx_ifj_task_current_definition_id] ON dbo.[ifj_task] ([current_task_definition_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_task_definition_task_time] ON dbo.[ifj_task_definition] ([task_id], [create_time], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_task_def_group_code] ON dbo.[ifj_task_definition_group] ([group_code], [definition_id]);

CREATE UNIQUE NONCLUSTERED INDEX [uk_ifj_trigger_event_binding] ON dbo.[ifj_trigger] ([event_id], [event_binding_id]) WHERE ([event_id] IS NOT NULL AND [event_binding_id] IS NOT NULL);

CREATE NONCLUSTERED INDEX [fk_ifj_trigger_schedule] ON dbo.[ifj_trigger] ([schedule_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_trigger_status_time] ON dbo.[ifj_trigger] ([status], [trigger_time]);

CREATE NONCLUSTERED INDEX [idx_ifj_trigger_target] ON dbo.[ifj_trigger] ([target_type], [target_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_trigger_definition_status] ON dbo.[ifj_trigger] ([definition_id], [status]);

CREATE NONCLUSTERED INDEX [idx_ifj_trigger_task_definition_status] ON dbo.[ifj_trigger] ([task_definition_id], [status]);

CREATE NONCLUSTERED INDEX [fk_ifj_trigger_event_binding] ON dbo.[ifj_trigger] ([event_binding_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_trigger_event] ON dbo.[ifj_trigger] ([event_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_trigger_status_update] ON dbo.[ifj_trigger] ([status], [update_time], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_trigger_error_update] ON dbo.[ifj_trigger] ([error_code], [update_time], [id]);

CREATE NONCLUSTERED INDEX [idx_ifj_trigger_history_v25] ON dbo.[ifj_trigger] ([target_type], [target_id], [trigger_time], [id]);

CREATE UNIQUE NONCLUSTERED INDEX [uk_ifj_trigger_work_item_claim_token] ON dbo.[ifj_trigger_work_item] ([claim_token]) WHERE ([claim_token] IS NOT NULL);

CREATE UNIQUE NONCLUSTERED INDEX [uk_ifj_trigger_work_item_root] ON dbo.[ifj_trigger_work_item] ([root_instance_id]) WHERE ([root_instance_id] IS NOT NULL);

CREATE NONCLUSTERED INDEX [idx_ifj_trigger_work_item_dispatch] ON dbo.[ifj_trigger_work_item] ([available_at], [trigger_id]) INCLUDE ([status], [lease_deadline], [attempt_count], [max_attempts]);

CREATE NONCLUSTERED INDEX [idx_ifj_trigger_work_item_owner] ON dbo.[ifj_trigger_work_item] ([lease_owner], [lease_deadline], [trigger_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_work_dispatch_v24] ON dbo.[ifj_trigger_work_item] ([status], [available_at], [trigger_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_work_lease_v24] ON dbo.[ifj_trigger_work_item] ([status], [lease_deadline], [trigger_id]);

CREATE NONCLUSTERED INDEX [idx_ifj_work_expiry_v24] ON dbo.[ifj_trigger_work_item] ([status], [expires_at], [trigger_id]);

ALTER TABLE dbo.[ifj_calendar_exception] ADD CONSTRAINT [fk_ifj_calendar_exception_calendar] FOREIGN KEY ([calendar_id]) REFERENCES dbo.[ifj_calendar] ([id]);

ALTER TABLE dbo.[ifj_dag_gateway_decision] ADD CONSTRAINT [fk_ifj_dag_gateway_decision_job] FOREIGN KEY ([job_instance_id]) REFERENCES dbo.[ifj_instance] ([id]);

ALTER TABLE dbo.[ifj_dag_gateway_decision] ADD CONSTRAINT [fk_ifj_dag_gateway_decision_node] FOREIGN KEY ([definition_node_id]) REFERENCES dbo.[ifj_job_definition_node] ([id]);

ALTER TABLE dbo.[ifj_event_wait] ADD CONSTRAINT [fk_ifj_event_wait_event] FOREIGN KEY ([matched_event_id]) REFERENCES dbo.[ifj_event] ([id]);

ALTER TABLE dbo.[ifj_event_wait] ADD CONSTRAINT [fk_ifj_event_wait_instance] FOREIGN KEY ([instance_id]) REFERENCES dbo.[ifj_instance] ([id]);

ALTER TABLE dbo.[ifj_event_wait] ADD CONSTRAINT [fk_ifj_event_wait_root] FOREIGN KEY ([root_instance_id]) REFERENCES dbo.[ifj_instance] ([id]);

ALTER TABLE dbo.[ifj_instance] ADD CONSTRAINT [fk_ifj_instance_definition] FOREIGN KEY ([definition_id]) REFERENCES dbo.[ifj_job_definition] ([id]);

ALTER TABLE dbo.[ifj_instance] ADD CONSTRAINT [fk_ifj_instance_definition_node] FOREIGN KEY ([definition_node_id]) REFERENCES dbo.[ifj_job_definition_node] ([id]);

ALTER TABLE dbo.[ifj_instance_log] ADD CONSTRAINT [fk_ifj_instance_log_instance] FOREIGN KEY ([instance_id]) REFERENCES dbo.[ifj_instance] ([id]);

ALTER TABLE dbo.[ifj_instance] ADD CONSTRAINT [fk_ifj_instance_parent] FOREIGN KEY ([parent_instance_id]) REFERENCES dbo.[ifj_instance] ([id]);

ALTER TABLE dbo.[ifj_instance] ADD CONSTRAINT [fk_ifj_instance_root] FOREIGN KEY ([root_instance_id]) REFERENCES dbo.[ifj_instance] ([id]);

ALTER TABLE dbo.[ifj_instance] ADD CONSTRAINT [fk_ifj_instance_task_definition] FOREIGN KEY ([task_definition_id]) REFERENCES dbo.[ifj_task_definition] ([id]);

ALTER TABLE dbo.[ifj_instance] ADD CONSTRAINT [fk_ifj_instance_trigger] FOREIGN KEY ([trigger_id]) REFERENCES dbo.[ifj_trigger] ([id]);

ALTER TABLE dbo.[ifj_job_definition_group] ADD CONSTRAINT [fk_ifj_job_def_group_definition] FOREIGN KEY ([definition_id]) REFERENCES dbo.[ifj_job_definition] ([id]) ON DELETE CASCADE;

ALTER TABLE dbo.[ifj_job_definition_edge] ADD CONSTRAINT [fk_ifj_job_definition_edge_definition] FOREIGN KEY ([definition_id]) REFERENCES dbo.[ifj_job_definition] ([id]);

ALTER TABLE dbo.[ifj_job_definition_edge] ADD CONSTRAINT [fk_ifj_job_definition_edge_from_node] FOREIGN KEY ([from_node_id]) REFERENCES dbo.[ifj_job_definition_node] ([id]);

ALTER TABLE dbo.[ifj_job_definition_edge] ADD CONSTRAINT [fk_ifj_job_definition_edge_to_node] FOREIGN KEY ([to_node_id]) REFERENCES dbo.[ifj_job_definition_node] ([id]);

ALTER TABLE dbo.[ifj_job_definition] ADD CONSTRAINT [fk_ifj_job_definition_job] FOREIGN KEY ([job_id]) REFERENCES dbo.[ifj_job] ([id]);

ALTER TABLE dbo.[ifj_job_definition_node] ADD CONSTRAINT [fk_ifj_job_definition_node_definition] FOREIGN KEY ([definition_id]) REFERENCES dbo.[ifj_job_definition] ([id]);

ALTER TABLE dbo.[ifj_job_definition_node_policy] ADD CONSTRAINT [fk_ifj_job_definition_node_policy_definition] FOREIGN KEY ([definition_id]) REFERENCES dbo.[ifj_job_definition] ([id]);

ALTER TABLE dbo.[ifj_job_definition_node_policy] ADD CONSTRAINT [fk_ifj_job_definition_node_policy_node] FOREIGN KEY ([node_id]) REFERENCES dbo.[ifj_job_definition_node] ([id]);

ALTER TABLE dbo.[ifj_job_definition_node] ADD CONSTRAINT [fk_ifj_job_definition_node_ref_job_definition] FOREIGN KEY ([ref_definition_id]) REFERENCES dbo.[ifj_job_definition] ([id]);

ALTER TABLE dbo.[ifj_job_definition_node] ADD CONSTRAINT [fk_ifj_job_definition_node_ref_task_definition] FOREIGN KEY ([ref_task_definition_id]) REFERENCES dbo.[ifj_task_definition] ([id]);

ALTER TABLE dbo.[ifj_notify_binding] ADD CONSTRAINT [fk_ifj_notify_binding_config] FOREIGN KEY ([notify_config_id]) REFERENCES dbo.[ifj_notify_config] ([id]);

ALTER TABLE dbo.[ifj_runtime_command] ADD CONSTRAINT [fk_ifj_runtime_command_instance] FOREIGN KEY ([instance_id]) REFERENCES dbo.[ifj_instance] ([id]);

ALTER TABLE dbo.[ifj_runtime_command] ADD CONSTRAINT [fk_ifj_runtime_command_root] FOREIGN KEY ([root_instance_id]) REFERENCES dbo.[ifj_instance] ([id]);

ALTER TABLE dbo.[ifj_runtime_command] ADD CONSTRAINT [fk_ifj_runtime_command_trigger] FOREIGN KEY ([trigger_id]) REFERENCES dbo.[ifj_trigger] ([id]);

ALTER TABLE dbo.[ifj_schedule] ADD CONSTRAINT [fk_ifj_schedule_calendar] FOREIGN KEY ([calendar_id]) REFERENCES dbo.[ifj_calendar] ([id]);

ALTER TABLE dbo.[ifj_schedule_misfire_audit] ADD CONSTRAINT [fk_ifj_schedule_misfire_sched] FOREIGN KEY ([schedule_id]) REFERENCES dbo.[ifj_schedule] ([id]) ON DELETE SET NULL;

ALTER TABLE dbo.[ifj_task_concurrency_resource] ADD CONSTRAINT [fk_ifj_task_concurrency_res_ref] FOREIGN KEY ([resource_id]) REFERENCES dbo.[ifj_concurrency_resource] ([id]);

ALTER TABLE dbo.[ifj_task_concurrency_resource] ADD CONSTRAINT [fk_ifj_task_concurrency_res_task] FOREIGN KEY ([task_id]) REFERENCES dbo.[ifj_task_concurrency_policy] ([task_id]) ON DELETE CASCADE;

ALTER TABLE dbo.[ifj_task_concurrency_policy] ADD CONSTRAINT [fk_ifj_task_concurrency_task] FOREIGN KEY ([task_id]) REFERENCES dbo.[ifj_task] ([id]) ON DELETE CASCADE;

ALTER TABLE dbo.[ifj_task_definition_group] ADD CONSTRAINT [fk_ifj_task_def_group_definition] FOREIGN KEY ([definition_id]) REFERENCES dbo.[ifj_task_definition] ([id]) ON DELETE CASCADE;

ALTER TABLE dbo.[ifj_task_definition] ADD CONSTRAINT [fk_ifj_task_definition_task] FOREIGN KEY ([task_id]) REFERENCES dbo.[ifj_task] ([id]);

ALTER TABLE dbo.[ifj_trigger] ADD CONSTRAINT [fk_ifj_trigger_definition] FOREIGN KEY ([definition_id]) REFERENCES dbo.[ifj_job_definition] ([id]);

ALTER TABLE dbo.[ifj_trigger] ADD CONSTRAINT [fk_ifj_trigger_event] FOREIGN KEY ([event_id]) REFERENCES dbo.[ifj_event] ([id]);

ALTER TABLE dbo.[ifj_trigger] ADD CONSTRAINT [fk_ifj_trigger_event_binding] FOREIGN KEY ([event_binding_id]) REFERENCES dbo.[ifj_event_binding] ([id]);

ALTER TABLE dbo.[ifj_trigger] ADD CONSTRAINT [fk_ifj_trigger_schedule] FOREIGN KEY ([schedule_id]) REFERENCES dbo.[ifj_schedule] ([id]);

ALTER TABLE dbo.[ifj_trigger] ADD CONSTRAINT [fk_ifj_trigger_task_definition] FOREIGN KEY ([task_definition_id]) REFERENCES dbo.[ifj_task_definition] ([id]);

ALTER TABLE dbo.[ifj_trigger_work_item] ADD CONSTRAINT [fk_ifj_trigger_work_item_root] FOREIGN KEY ([root_instance_id]) REFERENCES dbo.[ifj_instance] ([id]);

ALTER TABLE dbo.[ifj_trigger_work_item] ADD CONSTRAINT [fk_ifj_trigger_work_item_trigger] FOREIGN KEY ([trigger_id]) REFERENCES dbo.[ifj_trigger] ([id]);

ALTER TABLE dbo.[ifj_event_wait_operator] ADD CONSTRAINT [fk_ifj_wait_operator_wait] FOREIGN KEY ([wait_id]) REFERENCES dbo.[ifj_event_wait] ([id]);

ALTER TABLE dbo.[ifj_parameter_binding] ADD CONSTRAINT [fk_param_binding_template] FOREIGN KEY ([template_id]) REFERENCES dbo.[ifj_parameter_template] ([id]) ON DELETE CASCADE;

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

INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_auth_user', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_calendar', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_calendar_exception', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_concurrency_resource', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_dag_gateway_decision', 1, 1000, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_event', 1, 1000, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_event_binding', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_event_wait', 1, 1000, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_instance', 1, 1000, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_instance_log', 1, 1000, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job_definition', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job_definition_edge', 1, 100, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job_definition_node', 1, 100, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job_definition_node_policy', 1, 100, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_mail_channel', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_notification_outbox', 1, 1000, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_notify_binding', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_notify_config', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_notify_send_log', 1, 1000, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_operation_audit', 1, 1000, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_parameter_template', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_resource', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_schedule', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_schedule_misfire_audit', 1, 1000, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_task', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_task_definition', 1, 1, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_trigger', 1, 1000, SYSUTCDATETIME());
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('platform-primary-id', 9223372036854775807, 1000, SYSUTCDATETIME());
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('auth-accounts', 0);
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('concurrency-policy', 1);
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('runtime-lifecycle', 0);
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('composite-task-concurrency', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('conditional-dag', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('event-wait-operator-relation', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('execution-group-routing', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('execution-target-routing', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('legacy-dag-removed', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('parameter-templates', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('per-table-primary-id', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('platform-schema', 38, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('runtime-history-v2', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('schedule-misfire-policy', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('stable-id-segment-seed', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
CREATE TABLE ifj_release_state (singleton_id INTEGER NOT NULL PRIMARY KEY, state VARCHAR(32) NOT NULL, CONSTRAINT ck_ifj_release_singleton CHECK (singleton_id = 1), CONSTRAINT ck_ifj_release_state CHECK (state IN ('EMPTY','REVIEW_REQUIRED','APPROVED')));
INSERT INTO ifj_release_state (singleton_id, state) VALUES (1, 'EMPTY');
