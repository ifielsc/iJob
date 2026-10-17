-- First release baseline. Logical platform schema 38; physical migration 1.
CREATE FUNCTION public.ifj_touch_update_time() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP(3);
    RETURN NEW;
END;
$$;





CREATE TABLE public.ifj_app_run (
    run_id character varying(64) NOT NULL,
    instance_name character varying(128) DEFAULT NULL::character varying,
    runtime_mode character varying(16) DEFAULT 'STANDALONE'::character varying NOT NULL,
    roles character varying(64) DEFAULT 'API,SCHEDULER,WORKER'::character varying NOT NULL,
    status character varying(16) NOT NULL,
    application_version character varying(64) DEFAULT NULL::character varying,
    host_name character varying(255) DEFAULT NULL::character varying,
    process_id bigint,
    started_at timestamp(3) without time zone NOT NULL,
    heartbeat_at timestamp(3) without time zone NOT NULL,
    stopped_at timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    worker_groups character varying(1100) DEFAULT ''::character varying NOT NULL,
    runtime_protocol_version integer DEFAULT 1 NOT NULL,
    worker_task_ids character varying(2100) DEFAULT ''::character varying NOT NULL,
    worker_job_ids character varying(2100) DEFAULT ''::character varying NOT NULL,
    CONSTRAINT chk_ifj_app_run_runtime_mode CHECK (((runtime_mode)::text = ANY ((ARRAY['DEVELOPMENT'::character varying, 'STANDALONE'::character varying, 'CLUSTER'::character varying])::text[]))),
    CONSTRAINT chk_ifj_app_run_status CHECK (((status)::text = ANY ((ARRAY['STARTING'::character varying, 'RUNNING'::character varying, 'DRAINING'::character varying, 'STOPPED'::character varying, 'CRASHED'::character varying])::text[])))
);



CREATE TABLE public.ifj_auth_user (
    id bigint NOT NULL,
    login_id character varying(128) NOT NULL,
    username character varying(128) NOT NULL,
    display_name character varying(128),
    password_hash character varying(100) NOT NULL,
    role_code character varying(16) DEFAULT 'VIEWER'::character varying NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    must_change_password boolean DEFAULT true NOT NULL,
    security_version bigint DEFAULT 0 NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    created_by character varying(128) NOT NULL,
    create_time timestamp(3) without time zone NOT NULL,
    update_time timestamp(3) without time zone NOT NULL,
    CONSTRAINT ck_ifj_auth_role CHECK (((role_code)::text = ANY ((ARRAY['ADMIN'::character varying, 'OPERATOR'::character varying, 'VIEWER'::character varying])::text[]))),
    CONSTRAINT ck_ifj_auth_version CHECK (((security_version >= 0) AND (revision >= 0)))
);



CREATE TABLE public.ifj_calendar (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    workdays_mask integer NOT NULL,
    description character varying(500) DEFAULT NULL::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);



CREATE TABLE public.ifj_calendar_exception (
    id bigint NOT NULL,
    calendar_id bigint NOT NULL,
    date date NOT NULL,
    type character varying(32) NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);



CREATE TABLE public.ifj_cluster_state (
    state_key character varying(512) NOT NULL,
    state_value text NOT NULL,
    expires_at timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);



CREATE TABLE public.ifj_concurrency_permit (
    scope_key character varying(191) NOT NULL,
    slot_no integer NOT NULL,
    owner_instance_id bigint,
    owner_root_instance_id bigint,
    owner_trigger_id bigint,
    lease_owner character varying(128) DEFAULT NULL::character varying,
    permit_token character varying(36) DEFAULT NULL::character varying,
    permit_fence bigint DEFAULT 0 NOT NULL,
    execution_fence bigint,
    lease_deadline timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT chk_ifj_concurrency_permit_fence CHECK ((permit_fence >= 0)),
    CONSTRAINT chk_ifj_concurrency_permit_lease CHECK ((((owner_instance_id IS NULL) AND (owner_root_instance_id IS NULL) AND (owner_trigger_id IS NULL) AND (lease_owner IS NULL) AND (permit_token IS NULL) AND (execution_fence IS NULL) AND (lease_deadline IS NULL)) OR ((owner_instance_id IS NOT NULL) AND (owner_root_instance_id IS NOT NULL) AND (owner_trigger_id IS NOT NULL) AND (lease_owner IS NOT NULL) AND (permit_token IS NOT NULL) AND (execution_fence IS NOT NULL) AND (lease_deadline IS NOT NULL)))),
    CONSTRAINT chk_ifj_concurrency_permit_slot CHECK ((slot_no > 0))
);



CREATE TABLE public.ifj_concurrency_resource (
    id bigint NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(128) NOT NULL,
    max_concurrency integer NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    description character varying(500),
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    CONSTRAINT ifj_concurrency_resource_max_concurrency_check CHECK (((max_concurrency >= 1) AND (max_concurrency <= 10000)))
);



CREATE TABLE public.ifj_dag_gateway_decision (
    id bigint NOT NULL,
    job_instance_id bigint NOT NULL,
    definition_node_id bigint NOT NULL,
    evaluation_no integer NOT NULL,
    selected_edge_ids text,
    context_digest character(64) NOT NULL,
    evaluator_version integer NOT NULL,
    status character varying(16) NOT NULL,
    error_code character varying(64),
    execution_fence bigint NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT ck_ifj_dag_gateway_decision_eval CHECK ((evaluation_no > 0)),
    CONSTRAINT ck_ifj_dag_gateway_decision_status CHECK (((((status)::text = 'SUCCESS'::text) AND (selected_edge_ids IS NOT NULL) AND (error_code IS NULL)) OR (((status)::text = 'FAILED'::text) AND (selected_edge_ids IS NULL) AND (error_code IS NOT NULL)))),
    CONSTRAINT ck_ifj_dag_gateway_decision_version CHECK ((evaluator_version = 1))
);



CREATE TABLE public.ifj_etl_checkpoint (
    task_id bigint NOT NULL,
    checkpoint_key character varying(128) NOT NULL,
    source_fingerprint character(64) NOT NULL,
    watermark_type character varying(32) NOT NULL,
    watermark_value text,
    tie_breaker_value text,
    last_instance_id bigint,
    version bigint DEFAULT 0 NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    owner_instance_id bigint,
    owner_token character varying(64),
    CONSTRAINT ck_ifj_etl_checkpoint_task CHECK ((task_id > 0)),
    CONSTRAINT ck_ifj_etl_checkpoint_version CHECK ((version >= 0))
);



CREATE TABLE public.ifj_etl_file_record (
    task_id bigint NOT NULL,
    checkpoint_key character varying(128) NOT NULL,
    path_hash character varying(64) NOT NULL,
    content_digest character varying(64) NOT NULL,
    marker_value character varying(2048) NOT NULL,
    last_instance_id bigint NOT NULL
);



CREATE TABLE public.ifj_event (
    id bigint NOT NULL,
    event_code character varying(128) NOT NULL,
    source_type character varying(32) NOT NULL,
    source_key character varying(128) NOT NULL,
    business_key character varying(256) DEFAULT NULL::character varying,
    correlation_key character varying(256) DEFAULT NULL::character varying,
    idempotency_key character varying(256) NOT NULL,
    occurred_at timestamp(3) without time zone NOT NULL,
    arrived_at timestamp(3) without time zone NOT NULL,
    summary character varying(500) DEFAULT NULL::character varying,
    payload text,
    payload_digest character(64) DEFAULT NULL::bpchar,
    payload_bytes integer DEFAULT 0 NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT chk_ifj_event_payload_bytes CHECK ((payload_bytes >= 0))
);



CREATE TABLE public.ifj_event_binding (
    id bigint NOT NULL,
    target_type character varying(32) NOT NULL,
    target_id bigint NOT NULL,
    event_code character varying(128) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);



CREATE TABLE public.ifj_event_wait (
    id bigint NOT NULL,
    instance_id bigint NOT NULL,
    root_instance_id bigint NOT NULL,
    attempt_no integer NOT NULL,
    wait_type character varying(32) NOT NULL,
    status character varying(32) NOT NULL,
    event_code character varying(128) DEFAULT NULL::character varying,
    business_key character varying(256) DEFAULT NULL::character varying,
    correlation_key character varying(256) DEFAULT NULL::character varying,
    accept_after timestamp(3) without time zone NOT NULL,
    timeout_at timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    matched_event_id bigint,
    allowed_operator_ids text,
    decision_by character varying(128) DEFAULT NULL::character varying,
    decision_comment character varying(500) DEFAULT NULL::character varying,
    resolved_at timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT chk_ifj_event_wait_attempt CHECK ((attempt_no >= 0)),
    CONSTRAINT chk_ifj_event_wait_status CHECK (((status)::text = ANY ((ARRAY['WAITING'::character varying, 'MATCHED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'TIMED_OUT'::character varying, 'WITHDRAWN'::character varying, 'STOPPED'::character varying])::text[]))),
    CONSTRAINT chk_ifj_event_wait_type CHECK (((wait_type)::text = ANY ((ARRAY['EVENT'::character varying, 'APPROVAL'::character varying])::text[])))
);



CREATE TABLE public.ifj_event_wait_operator (
    wait_id bigint NOT NULL,
    operator_id character varying(128) NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT chk_ifj_wait_operator_id CHECK ((char_length((operator_id)::text) > 0))
);



CREATE TABLE public.ifj_id_segment (
    segment_key character varying(64) NOT NULL,
    next_value bigint NOT NULL,
    segment_size integer NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT chk_ifj_id_segment_next_value CHECK ((next_value > 0)),
    CONSTRAINT chk_ifj_id_segment_size CHECK (((segment_size >= 1) AND (segment_size <= 100000)))
);



CREATE TABLE public.ifj_instance (
    id bigint NOT NULL,
    node_id bigint,
    parent_instance_id bigint,
    root_instance_id bigint,
    execution_fence bigint DEFAULT 0 NOT NULL,
    target_type character varying(32) NOT NULL,
    target_id bigint NOT NULL,
    trigger_type character varying(32) DEFAULT NULL::character varying,
    trigger_id bigint,
    status character varying(32) NOT NULL,
    progress integer DEFAULT 0 NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    start_time timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    timeout_at timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    end_time timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    message text,
    error_code character varying(64) DEFAULT NULL::character varying,
    message_key character varying(128) DEFAULT NULL::character varying,
    message_args text,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    definition_id bigint,
    definition_node_id bigint,
    task_definition_id bigint,
    input_variables text,
    output_variables text,
    wait_reason character varying(32),
    permit_wait_deadline timestamp(3) without time zone,
    CONSTRAINT chk_ifj_instance_execution_fence CHECK ((execution_fence >= 0)),
    CONSTRAINT chk_ifj_instance_progress CHECK (((progress >= 0) AND (progress <= 100))),
    CONSTRAINT chk_ifj_instance_retry_count CHECK ((retry_count >= 0)),
    CONSTRAINT chk_ifj_instance_status CHECK (((status)::text = ANY ((ARRAY['WAITING'::character varying, 'RUNNING'::character varying, 'PAUSED'::character varying, 'WAITING_EVENT'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying, 'STOPPED'::character varying, 'TIMED_OUT'::character varying, 'SKIPPED'::character varying])::text[])))
);



CREATE TABLE public.ifj_instance_log (
    id bigint NOT NULL,
    instance_id bigint NOT NULL,
    log_time timestamp(3) without time zone NOT NULL,
    level character varying(16) NOT NULL,
    content text NOT NULL
);



CREATE TABLE public.ifj_job (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    timeout integer,
    enabled boolean DEFAULT true NOT NULL,
    description character varying(500) DEFAULT NULL::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    current_definition_id bigint,
    current_revision bigint,
    retired boolean DEFAULT false NOT NULL,
    retired_time timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    CONSTRAINT ck_ifj_job_current_pointer_pair CHECK ((((current_definition_id IS NULL) AND (current_revision IS NULL)) OR ((current_definition_id IS NOT NULL) AND (current_revision IS NOT NULL)))),
    CONSTRAINT ck_ifj_job_retired_state CHECK ((((retired = false) AND (retired_time IS NULL)) OR ((retired = true) AND (retired_time IS NOT NULL))))
);



CREATE TABLE public.ifj_job_definition (
    id bigint NOT NULL,
    job_id bigint NOT NULL,
    revision bigint NOT NULL,
    name character varying(128) NOT NULL,
    timeout integer,
    enabled boolean NOT NULL,
    description character varying(500) DEFAULT NULL::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    layout_json text,
    CONSTRAINT ck_ifj_job_definition_revision CHECK ((revision > 0))
);



CREATE TABLE public.ifj_job_definition_edge (
    id bigint NOT NULL,
    definition_id bigint NOT NULL,
    from_node_id bigint NOT NULL,
    to_node_id bigint NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    route_key character varying(64),
    route_priority integer,
    default_route boolean DEFAULT false NOT NULL,
    condition_version integer,
    condition_payload text,
    CONSTRAINT ck_ifj_job_def_edge_route CHECK ((((route_key IS NULL) AND (route_priority IS NULL) AND (default_route = false) AND (condition_version IS NULL) AND (condition_payload IS NULL)) OR ((route_key IS NOT NULL) AND ((route_priority >= 0) AND (route_priority <= 10000)) AND (((default_route = true) AND (condition_version IS NULL) AND (condition_payload IS NULL)) OR ((default_route = false) AND (condition_version = 1) AND (condition_payload IS NOT NULL))))))
);



CREATE TABLE public.ifj_job_definition_group (
    definition_id bigint NOT NULL,
    group_code character varying(64) NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);



CREATE TABLE public.ifj_job_definition_node (
    id bigint NOT NULL,
    definition_id bigint NOT NULL,
    node_key character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    node_type character varying(32),
    ref_id bigint,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    ref_definition_id bigint,
    ref_task_definition_id bigint,
    node_kind character varying(16) DEFAULT 'EXECUTION'::character varying NOT NULL,
    gateway_mode character varying(16),
    CONSTRAINT chk_ifj_job_definition_node_ref_snapshot CHECK (((((node_kind)::text = 'GATEWAY'::text) AND (ref_definition_id IS NULL) AND (ref_task_definition_id IS NULL)) OR (((node_kind)::text = 'EXECUTION'::text) AND ((((node_type)::text = 'JOB'::text) AND (ref_definition_id IS NOT NULL) AND (ref_task_definition_id IS NULL)) OR (((node_type)::text = 'TASK'::text) AND (ref_definition_id IS NULL) AND (ref_task_definition_id IS NOT NULL)))))),
    CONSTRAINT ck_ifj_job_def_node_kind CHECK (((((node_kind)::text = 'EXECUTION'::text) AND (gateway_mode IS NULL) AND ((node_type)::text = ANY ((ARRAY['TASK'::character varying, 'JOB'::character varying])::text[])) AND (ref_id IS NOT NULL)) OR (((node_kind)::text = 'GATEWAY'::text) AND ((gateway_mode)::text = 'EXCLUSIVE'::text) AND (node_type IS NULL) AND (ref_id IS NULL) AND (ref_definition_id IS NULL) AND (ref_task_definition_id IS NULL))))
);



CREATE TABLE public.ifj_job_definition_node_policy (
    id bigint NOT NULL,
    definition_id bigint NOT NULL,
    node_id bigint NOT NULL,
    fail_strategy character varying(32) NOT NULL,
    execute_condition character varying(32) NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);



CREATE TABLE public.ifj_mail_channel (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    config text NOT NULL,
    enabled boolean NOT NULL,
    revision bigint NOT NULL,
    create_time timestamp(3) without time zone NOT NULL,
    update_time timestamp(3) without time zone NOT NULL
);



CREATE TABLE public.ifj_maintenance_lease (
    lease_name character varying(64) NOT NULL,
    lease_owner character varying(128) DEFAULT NULL::character varying,
    lease_token character varying(36) DEFAULT NULL::character varying,
    lease_deadline timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    fence_token bigint DEFAULT 0 NOT NULL,
    last_acquired_at timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    last_completed_at timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    last_success boolean,
    last_failure_code character varying(64) DEFAULT NULL::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT chk_ifj_maintenance_lease_fence CHECK ((fence_token >= 0)),
    CONSTRAINT chk_ifj_maintenance_lease_holder CHECK ((((lease_owner IS NULL) AND (lease_token IS NULL) AND (lease_deadline IS NULL)) OR ((lease_owner IS NOT NULL) AND (lease_token IS NOT NULL) AND (lease_deadline IS NOT NULL))))
);



CREATE TABLE public.ifj_notification_outbox (
    id bigint NOT NULL,
    deduplication_key character varying(191) NOT NULL,
    notify_config_id bigint NOT NULL,
    notify_type character varying(32) DEFAULT NULL::character varying,
    config_snapshot text,
    config_enabled boolean,
    title character varying(255) DEFAULT NULL::character varying,
    subject character varying(255) NOT NULL,
    content text NOT NULL,
    source_type character varying(32) NOT NULL,
    correlation_id character varying(64) DEFAULT NULL::character varying,
    operator_id character varying(128) DEFAULT NULL::character varying,
    target_type character varying(32) DEFAULT NULL::character varying,
    target_id bigint,
    instance_id bigint,
    notify_binding_id bigint,
    status character varying(16) NOT NULL,
    available_at timestamp(3) without time zone NOT NULL,
    lease_owner character varying(128) DEFAULT NULL::character varying,
    claim_token character varying(36) DEFAULT NULL::character varying,
    lease_deadline timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    attempt_count integer DEFAULT 0 NOT NULL,
    max_attempts integer NOT NULL,
    last_failure_code character varying(64) DEFAULT NULL::character varying,
    last_failure_summary character varying(500) DEFAULT NULL::character varying,
    delivered_at timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    content_version integer DEFAULT 0 NOT NULL,
    http_status integer,
    CONSTRAINT chk_ifj_notification_outbox_attempts CHECK (((attempt_count >= 0) AND ((max_attempts >= 1) AND (max_attempts <= 100)))),
    CONSTRAINT chk_ifj_notification_outbox_config_snapshot CHECK ((((notify_type IS NULL) AND (config_snapshot IS NULL) AND (config_enabled IS NULL)) OR ((notify_type IS NOT NULL) AND (config_snapshot IS NOT NULL) AND (config_enabled IS NOT NULL)))),
    CONSTRAINT chk_ifj_notification_outbox_delivery CHECK (((((status)::text = 'DELIVERED'::text) AND (delivered_at IS NOT NULL)) OR (((status)::text <> 'DELIVERED'::text) AND (delivered_at IS NULL)))),
    CONSTRAINT chk_ifj_notification_outbox_lease CHECK (((((status)::text = 'CLAIMED'::text) AND (lease_owner IS NOT NULL) AND (claim_token IS NOT NULL) AND (lease_deadline IS NOT NULL)) OR (((status)::text <> 'CLAIMED'::text) AND (lease_owner IS NULL) AND (claim_token IS NULL) AND (lease_deadline IS NULL)))),
    CONSTRAINT chk_ifj_notification_outbox_status CHECK (((status)::text = ANY ((ARRAY['READY'::character varying, 'CLAIMED'::character varying, 'RETRY_WAIT'::character varying, 'DELIVERED'::character varying, 'DEAD'::character varying])::text[])))
);



CREATE TABLE public.ifj_notify_binding (
    id bigint NOT NULL,
    target_type character varying(32) NOT NULL,
    target_id bigint NOT NULL,
    notify_config_id bigint NOT NULL,
    notify_policy character varying(32) NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);



CREATE TABLE public.ifj_notify_config (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    type character varying(32) NOT NULL,
    config text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);



CREATE TABLE public.ifj_notify_rate_limit (
    target_key character varying(64) NOT NULL,
    next_allowed_at bigint NOT NULL
);



CREATE TABLE public.ifj_notify_send_log (
    id bigint NOT NULL,
    source_type character varying(32) NOT NULL,
    correlation_id character varying(64) DEFAULT NULL::character varying,
    operator_id character varying(128) DEFAULT NULL::character varying,
    target_type character varying(32) DEFAULT NULL::character varying,
    target_id bigint,
    instance_id bigint,
    notify_binding_id bigint,
    notify_config_id bigint NOT NULL,
    notify_type character varying(32) DEFAULT NULL::character varying,
    result character varying(32) NOT NULL,
    failure_code character varying(64) DEFAULT NULL::character varying,
    failure_summary character varying(500) DEFAULT NULL::character varying,
    duration_ms bigint,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);



CREATE TABLE public.ifj_operation_audit (
    id bigint NOT NULL,
    occurred_at timestamp(3) without time zone NOT NULL,
    actor_login_id character varying(128) NOT NULL,
    actor_name character varying(128),
    action_code character varying(64) NOT NULL,
    target_type character varying(32) NOT NULL,
    target_id character varying(128),
    result_code character varying(32) NOT NULL,
    trace_id character varying(32),
    request_id character varying(128),
    changed_fields character varying(1024)
);



CREATE TABLE public.ifj_parameter_binding (
    target_type character varying(16) NOT NULL,
    target_id bigint NOT NULL,
    template_id bigint NOT NULL,
    CONSTRAINT ck_param_binding_type CHECK (((target_type)::text = ANY ((ARRAY['TASK'::character varying, 'JOB'::character varying])::text[])))
);



CREATE TABLE public.ifj_parameter_template (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    description character varying(500),
    fields_json text NOT NULL,
    revision bigint NOT NULL
);



CREATE TABLE public.ifj_plugin_concurrency_policy (
    plugin_type character varying(32) NOT NULL,
    max_concurrency integer NOT NULL,
    version bigint DEFAULT 1 NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT ifj_plugin_concurrency_policy_max_concurrency_check CHECK (((max_concurrency >= 1) AND (max_concurrency <= 10000))),
    CONSTRAINT ifj_plugin_concurrency_policy_version_check CHECK ((version > 0))
);



CREATE TABLE public.ifj_resource (
    id bigint NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(128) NOT NULL,
    type character varying(32) NOT NULL,
    config text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    description character varying(500) DEFAULT NULL::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);



CREATE TABLE public.ifj_runtime_command (
    request_id character varying(191) NOT NULL,
    instance_id bigint NOT NULL,
    root_instance_id bigint NOT NULL,
    trigger_id bigint NOT NULL,
    command_type character varying(16) NOT NULL,
    status character varying(16) NOT NULL,
    requested_by character varying(128) NOT NULL,
    result_message character varying(500) DEFAULT NULL::character varying,
    available_at timestamp(3) without time zone NOT NULL,
    expires_at timestamp(3) without time zone NOT NULL,
    processing_owner character varying(128) DEFAULT NULL::character varying,
    processing_token character varying(36) DEFAULT NULL::character varying,
    processing_fence bigint,
    processed_at timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT chk_ifj_runtime_command_processing CHECK (((((status)::text = 'PROCESSING'::text) AND (processing_owner IS NOT NULL) AND (processing_token IS NOT NULL) AND (processing_fence IS NOT NULL)) OR (((status)::text <> 'PROCESSING'::text) AND (processing_owner IS NULL) AND (processing_token IS NULL) AND (processing_fence IS NULL)))),
    CONSTRAINT chk_ifj_runtime_command_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'SUCCEEDED'::character varying, 'REJECTED'::character varying, 'FAILED'::character varying, 'EXPIRED'::character varying])::text[]))),
    CONSTRAINT chk_ifj_runtime_command_type CHECK (((command_type)::text = ANY ((ARRAY['STOP'::character varying, 'PAUSE'::character varying, 'RESUME'::character varying, 'RETRY'::character varying])::text[])))
);



CREATE TABLE public.ifj_runtime_guard (
    guard_key character varying(64) NOT NULL,
    revision bigint NOT NULL
);



CREATE TABLE public.ifj_schedule (
    id bigint NOT NULL,
    target_type character varying(32) NOT NULL,
    target_id bigint NOT NULL,
    schedule_type character varying(32) NOT NULL,
    cron_expr character varying(128) DEFAULT NULL::character varying,
    interval_sec integer,
    calendar_id bigint,
    start_time timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    end_time timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    enabled boolean DEFAULT true NOT NULL,
    last_fire_time timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    next_fire_time timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    fire_claim_token character varying(36) DEFAULT NULL::character varying,
    fire_claimed_at timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    variables text,
    misfire_policy character varying(32),
    misfire_grace_sec integer,
    max_catchup_count integer,
    max_queue_delay_sec integer,
    revision bigint DEFAULT 0 NOT NULL,
    parameter_form text,
    CONSTRAINT chk_ifj_schedule_fire_claim CHECK ((((fire_claim_token IS NULL) AND (fire_claimed_at IS NULL)) OR ((fire_claim_token IS NOT NULL) AND (fire_claimed_at IS NOT NULL)))),
    CONSTRAINT chk_ifj_schedule_misfire_policy CHECK (((misfire_policy IS NULL) OR ((misfire_policy)::text = ANY ((ARRAY['SKIP'::character varying, 'FIRE_ONCE'::character varying, 'CATCH_UP_BOUNDED'::character varying])::text[])))),
    CONSTRAINT ifj_schedule_max_catchup_count_check CHECK (((max_catchup_count IS NULL) OR ((max_catchup_count >= 1) AND (max_catchup_count <= 1000)))),
    CONSTRAINT ifj_schedule_max_queue_delay_sec_check CHECK (((max_queue_delay_sec IS NULL) OR ((max_queue_delay_sec >= 1) AND (max_queue_delay_sec <= 31536000)))),
    CONSTRAINT ifj_schedule_misfire_grace_sec_check CHECK (((misfire_grace_sec IS NULL) OR (misfire_grace_sec >= 0)))
);



CREATE TABLE public.ifj_schedule_misfire_audit (
    id bigint NOT NULL,
    schedule_id bigint,
    fire_claim_token character varying(36) NOT NULL,
    policy character varying(32) NOT NULL,
    observed_at timestamp(3) without time zone NOT NULL,
    first_due_time timestamp(3) without time zone NOT NULL,
    last_due_time timestamp(3) without time zone,
    due_count integer NOT NULL,
    count_capped boolean NOT NULL,
    created_count integer NOT NULL,
    skipped_count integer NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT ifj_schedule_misfire_audit_created_count_check CHECK ((created_count >= 0)),
    CONSTRAINT ifj_schedule_misfire_audit_due_count_check CHECK ((due_count >= 0)),
    CONSTRAINT ifj_schedule_misfire_audit_policy_check CHECK (((policy)::text = ANY ((ARRAY['SKIP'::character varying, 'FIRE_ONCE'::character varying, 'CATCH_UP_BOUNDED'::character varying])::text[]))),
    CONSTRAINT ifj_schedule_misfire_audit_skipped_count_check CHECK ((skipped_count >= 0))
);



CREATE TABLE public.ifj_schema_capability (
    capability_key character varying(64) NOT NULL,
    capability_version integer NOT NULL,
    enabled boolean NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT chk_ifj_schema_cap_enabled CHECK ((enabled = ANY (ARRAY[false, true]))),
    CONSTRAINT chk_ifj_schema_cap_version CHECK ((capability_version > 0))
);



CREATE TABLE public.ifj_task (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    plugin_type character varying(32) NOT NULL,
    plugin_config text NOT NULL,
    retry_times integer DEFAULT 0 NOT NULL,
    retry_interval integer DEFAULT 0 NOT NULL,
    timeout integer,
    enabled boolean DEFAULT true NOT NULL,
    description character varying(500) DEFAULT NULL::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    current_task_definition_id bigint,
    current_revision bigint,
    retired boolean DEFAULT false NOT NULL,
    retired_time timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    CONSTRAINT ck_ifj_task_current_pointer_pair CHECK ((((current_task_definition_id IS NULL) AND (current_revision IS NULL)) OR ((current_task_definition_id IS NOT NULL) AND (current_revision IS NOT NULL)))),
    CONSTRAINT ck_ifj_task_current_revision_positive CHECK (((current_revision IS NULL) OR (current_revision > 0))),
    CONSTRAINT ck_ifj_task_retired_state CHECK ((((retired = false) AND (retired_time IS NULL)) OR ((retired = true) AND (retired_time IS NOT NULL))))
);



CREATE TABLE public.ifj_task_concurrency_policy (
    task_id bigint NOT NULL,
    task_max_concurrency integer,
    version bigint DEFAULT 1 NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT ifj_task_concurrency_policy_task_max_concurrency_check CHECK (((task_max_concurrency IS NULL) OR ((task_max_concurrency >= 1) AND (task_max_concurrency <= 10000)))),
    CONSTRAINT ifj_task_concurrency_policy_version_check CHECK ((version > 0))
);



CREATE TABLE public.ifj_task_concurrency_resource (
    task_id bigint NOT NULL,
    resource_id bigint NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);



CREATE TABLE public.ifj_task_definition (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    revision bigint NOT NULL,
    name character varying(128) NOT NULL,
    plugin_type character varying(32) NOT NULL,
    plugin_config text NOT NULL,
    retry_times integer NOT NULL,
    retry_interval integer NOT NULL,
    timeout integer,
    enabled boolean NOT NULL,
    description character varying(500) DEFAULT NULL::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    CONSTRAINT ck_ifj_task_definition_revision CHECK ((revision > 0))
);



CREATE TABLE public.ifj_task_definition_group (
    definition_id bigint NOT NULL,
    group_code character varying(64) NOT NULL,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);



CREATE TABLE public.ifj_trigger (
    id bigint NOT NULL,
    schedule_id bigint,
    target_type character varying(32) NOT NULL,
    target_id bigint NOT NULL,
    trigger_type character varying(32) NOT NULL,
    idempotency_key character varying(191) NOT NULL,
    event_code character varying(128) DEFAULT NULL::character varying,
    status character varying(32) NOT NULL,
    trigger_time timestamp(3) without time zone NOT NULL,
    message text,
    error_code character varying(64) DEFAULT NULL::character varying,
    message_key character varying(128) DEFAULT NULL::character varying,
    message_args text,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    definition_id bigint,
    task_definition_id bigint,
    variables text,
    event_id bigint,
    event_binding_id bigint,
    business_date character varying(10),
    business_time character varying(48),
    business_zone_id character varying(128),
    CONSTRAINT chk_ifj_trigger_status CHECK (((status)::text = ANY ((ARRAY['WAITING'::character varying, 'RUNNING'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying, 'STOPPED'::character varying, 'TIMED_OUT'::character varying, 'SKIPPED'::character varying])::text[])))
);



CREATE TABLE public.ifj_trigger_work_item (
    trigger_id bigint NOT NULL,
    root_instance_id bigint,
    status character varying(16) NOT NULL,
    available_at timestamp(3) without time zone NOT NULL,
    lease_owner character varying(128) DEFAULT NULL::character varying,
    claim_token character varying(36) DEFAULT NULL::character varying,
    lease_deadline timestamp(3) without time zone DEFAULT NULL::timestamp without time zone,
    fence_token bigint DEFAULT 0 NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    max_attempts integer NOT NULL,
    last_error character varying(500) DEFAULT NULL::character varying,
    create_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    update_time timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    expires_at timestamp(3) without time zone,
    CONSTRAINT chk_ifj_trigger_work_item_attempts CHECK (((attempt_count >= 0) AND ((max_attempts >= 1) AND (max_attempts <= 100)))),
    CONSTRAINT chk_ifj_trigger_work_item_fence CHECK ((fence_token >= 0)),
    CONSTRAINT chk_ifj_trigger_work_item_lease CHECK (((((status)::text = 'CLAIMED'::text) AND (lease_owner IS NOT NULL) AND (claim_token IS NOT NULL) AND (lease_deadline IS NOT NULL)) OR (((status)::text <> 'CLAIMED'::text) AND (lease_owner IS NULL) AND (claim_token IS NULL) AND (lease_deadline IS NULL)))),
    CONSTRAINT chk_ifj_trigger_work_item_status CHECK (((status)::text = ANY ((ARRAY['READY'::character varying, 'CLAIMED'::character varying, 'SUSPENDED'::character varying, 'RETRY_WAIT'::character varying, 'COMPLETED'::character varying, 'DEAD'::character varying])::text[])))
);



ALTER TABLE ONLY public.ifj_concurrency_resource
    ADD CONSTRAINT ifj_concurrency_resource_code_key UNIQUE (code);



ALTER TABLE ONLY public.ifj_concurrency_resource
    ADD CONSTRAINT ifj_concurrency_resource_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_mail_channel
    ADD CONSTRAINT ifj_mail_channel_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_notify_rate_limit
    ADD CONSTRAINT ifj_notify_rate_limit_pkey PRIMARY KEY (target_key);



ALTER TABLE ONLY public.ifj_parameter_binding
    ADD CONSTRAINT ifj_parameter_binding_pkey PRIMARY KEY (target_type, target_id);



ALTER TABLE ONLY public.ifj_parameter_template
    ADD CONSTRAINT ifj_parameter_template_name_key UNIQUE (name);



ALTER TABLE ONLY public.ifj_parameter_template
    ADD CONSTRAINT ifj_parameter_template_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_plugin_concurrency_policy
    ADD CONSTRAINT ifj_plugin_concurrency_policy_pkey PRIMARY KEY (plugin_type);



ALTER TABLE ONLY public.ifj_schedule_misfire_audit
    ADD CONSTRAINT ifj_schedule_misfire_audit_fire_claim_token_key UNIQUE (fire_claim_token);



ALTER TABLE ONLY public.ifj_schedule_misfire_audit
    ADD CONSTRAINT ifj_schedule_misfire_audit_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_task_concurrency_policy
    ADD CONSTRAINT ifj_task_concurrency_policy_pkey PRIMARY KEY (task_id);



ALTER TABLE ONLY public.ifj_task_concurrency_resource
    ADD CONSTRAINT ifj_task_concurrency_resource_pkey PRIMARY KEY (task_id, resource_id);



ALTER TABLE ONLY public.ifj_app_run
    ADD CONSTRAINT pk_ifj_app_run PRIMARY KEY (run_id);



ALTER TABLE ONLY public.ifj_auth_user
    ADD CONSTRAINT pk_ifj_auth_user PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_calendar
    ADD CONSTRAINT pk_ifj_calendar PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_calendar_exception
    ADD CONSTRAINT pk_ifj_calendar_exception PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_cluster_state
    ADD CONSTRAINT pk_ifj_cluster_state PRIMARY KEY (state_key);



ALTER TABLE ONLY public.ifj_concurrency_permit
    ADD CONSTRAINT pk_ifj_concurrency_permit PRIMARY KEY (scope_key, slot_no);



ALTER TABLE ONLY public.ifj_dag_gateway_decision
    ADD CONSTRAINT pk_ifj_dag_gateway_decision PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_etl_checkpoint
    ADD CONSTRAINT pk_ifj_etl_checkpoint PRIMARY KEY (task_id, checkpoint_key);



ALTER TABLE ONLY public.ifj_etl_file_record
    ADD CONSTRAINT pk_ifj_etl_file_record PRIMARY KEY (task_id, checkpoint_key, path_hash);



ALTER TABLE ONLY public.ifj_event
    ADD CONSTRAINT pk_ifj_event PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_event_binding
    ADD CONSTRAINT pk_ifj_event_binding PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_event_wait
    ADD CONSTRAINT pk_ifj_event_wait PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_event_wait_operator
    ADD CONSTRAINT pk_ifj_event_wait_operator PRIMARY KEY (wait_id, operator_id);



ALTER TABLE ONLY public.ifj_id_segment
    ADD CONSTRAINT pk_ifj_id_segment PRIMARY KEY (segment_key);



ALTER TABLE ONLY public.ifj_instance
    ADD CONSTRAINT pk_ifj_instance PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_instance_log
    ADD CONSTRAINT pk_ifj_instance_log PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_job
    ADD CONSTRAINT pk_ifj_job PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_job_definition
    ADD CONSTRAINT pk_ifj_job_definition PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_job_definition_edge
    ADD CONSTRAINT pk_ifj_job_definition_edge PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_job_definition_group
    ADD CONSTRAINT pk_ifj_job_definition_group PRIMARY KEY (definition_id, group_code);



ALTER TABLE ONLY public.ifj_job_definition_node
    ADD CONSTRAINT pk_ifj_job_definition_node PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_job_definition_node_policy
    ADD CONSTRAINT pk_ifj_job_definition_node_policy PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_maintenance_lease
    ADD CONSTRAINT pk_ifj_maintenance_lease PRIMARY KEY (lease_name);



ALTER TABLE ONLY public.ifj_notification_outbox
    ADD CONSTRAINT pk_ifj_notification_outbox PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_notify_binding
    ADD CONSTRAINT pk_ifj_notify_binding PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_notify_config
    ADD CONSTRAINT pk_ifj_notify_config PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_notify_send_log
    ADD CONSTRAINT pk_ifj_notify_send_log PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_operation_audit
    ADD CONSTRAINT pk_ifj_operation_audit PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_resource
    ADD CONSTRAINT pk_ifj_resource PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_runtime_command
    ADD CONSTRAINT pk_ifj_runtime_command PRIMARY KEY (request_id);



ALTER TABLE ONLY public.ifj_runtime_guard
    ADD CONSTRAINT pk_ifj_runtime_guard PRIMARY KEY (guard_key);



ALTER TABLE ONLY public.ifj_schedule
    ADD CONSTRAINT pk_ifj_schedule PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_schema_capability
    ADD CONSTRAINT pk_ifj_schema_capability PRIMARY KEY (capability_key);



ALTER TABLE ONLY public.ifj_task
    ADD CONSTRAINT pk_ifj_task PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_task_definition
    ADD CONSTRAINT pk_ifj_task_definition PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_task_definition_group
    ADD CONSTRAINT pk_ifj_task_definition_group PRIMARY KEY (definition_id, group_code);



ALTER TABLE ONLY public.ifj_trigger
    ADD CONSTRAINT pk_ifj_trigger PRIMARY KEY (id);



ALTER TABLE ONLY public.ifj_trigger_work_item
    ADD CONSTRAINT pk_ifj_trigger_work_item PRIMARY KEY (trigger_id);



ALTER TABLE ONLY public.ifj_auth_user
    ADD CONSTRAINT uk_ifj_auth_login UNIQUE (login_id);



ALTER TABLE ONLY public.ifj_auth_user
    ADD CONSTRAINT uk_ifj_auth_username UNIQUE (username);



ALTER TABLE ONLY public.ifj_calendar_exception
    ADD CONSTRAINT uk_ifj_calendar_exception_date UNIQUE (calendar_id, date);



ALTER TABLE ONLY public.ifj_calendar
    ADD CONSTRAINT uk_ifj_calendar_name UNIQUE (name);



ALTER TABLE ONLY public.ifj_concurrency_permit
    ADD CONSTRAINT uk_ifj_concurrency_permit_token UNIQUE (permit_token);



ALTER TABLE ONLY public.ifj_dag_gateway_decision
    ADD CONSTRAINT uk_ifj_dag_gateway_decision_eval UNIQUE (job_instance_id, definition_node_id, evaluation_no);



ALTER TABLE ONLY public.ifj_event_binding
    ADD CONSTRAINT uk_ifj_event_binding_target UNIQUE (target_type, target_id, event_code);



ALTER TABLE ONLY public.ifj_event
    ADD CONSTRAINT uk_ifj_event_source_idempotency UNIQUE (source_key, idempotency_key);



ALTER TABLE ONLY public.ifj_event_wait
    ADD CONSTRAINT uk_ifj_event_wait_attempt UNIQUE (instance_id, attempt_no);



ALTER TABLE ONLY public.ifj_instance
    ADD CONSTRAINT uk_ifj_instance_parent_definition_node UNIQUE (parent_instance_id, definition_node_id);



ALTER TABLE ONLY public.ifj_instance
    ADD CONSTRAINT uk_ifj_instance_parent_node UNIQUE (parent_instance_id, node_id);



ALTER TABLE ONLY public.ifj_job_definition_edge
    ADD CONSTRAINT uk_ifj_job_definition_edge_path UNIQUE (definition_id, from_node_id, to_node_id);



ALTER TABLE ONLY public.ifj_job_definition
    ADD CONSTRAINT uk_ifj_job_definition_job_revision UNIQUE (job_id, revision);



ALTER TABLE ONLY public.ifj_job_definition_node
    ADD CONSTRAINT uk_ifj_job_definition_node_key UNIQUE (definition_id, node_key);



ALTER TABLE ONLY public.ifj_job_definition_node_policy
    ADD CONSTRAINT uk_ifj_job_definition_node_policy_node UNIQUE (node_id);



ALTER TABLE ONLY public.ifj_maintenance_lease
    ADD CONSTRAINT uk_ifj_maintenance_lease_token UNIQUE (lease_token);



ALTER TABLE ONLY public.ifj_notification_outbox
    ADD CONSTRAINT uk_ifj_notification_outbox_claim_token UNIQUE (claim_token);



ALTER TABLE ONLY public.ifj_notification_outbox
    ADD CONSTRAINT uk_ifj_notification_outbox_deduplication UNIQUE (deduplication_key);



ALTER TABLE ONLY public.ifj_notify_binding
    ADD CONSTRAINT uk_ifj_notify_binding_target UNIQUE (target_type, target_id, notify_config_id);



ALTER TABLE ONLY public.ifj_resource
    ADD CONSTRAINT uk_ifj_resource_code UNIQUE (code);



ALTER TABLE ONLY public.ifj_runtime_command
    ADD CONSTRAINT uk_ifj_runtime_command_processing_token UNIQUE (processing_token);



ALTER TABLE ONLY public.ifj_schedule
    ADD CONSTRAINT uk_ifj_schedule_fire_claim_token UNIQUE (fire_claim_token);



ALTER TABLE ONLY public.ifj_task_definition
    ADD CONSTRAINT uk_ifj_task_definition_task_revision UNIQUE (task_id, revision);



ALTER TABLE ONLY public.ifj_trigger
    ADD CONSTRAINT uk_ifj_trigger_event_binding UNIQUE (event_id, event_binding_id);



ALTER TABLE ONLY public.ifj_trigger
    ADD CONSTRAINT uk_ifj_trigger_idempotency_key UNIQUE (idempotency_key);



ALTER TABLE ONLY public.ifj_trigger_work_item
    ADD CONSTRAINT uk_ifj_trigger_work_item_claim_token UNIQUE (claim_token);



ALTER TABLE ONLY public.ifj_trigger_work_item
    ADD CONSTRAINT uk_ifj_trigger_work_item_root UNIQUE (root_instance_id);



CREATE INDEX fk_ifj_event_wait_event ON public.ifj_event_wait USING btree (matched_event_id);



CREATE INDEX fk_ifj_instance_definition_node ON public.ifj_instance USING btree (definition_node_id);



CREATE INDEX fk_ifj_job_definition_edge_from_node ON public.ifj_job_definition_edge USING btree (from_node_id);



CREATE INDEX fk_ifj_notify_binding_config ON public.ifj_notify_binding USING btree (notify_config_id);



CREATE INDEX fk_ifj_runtime_command_instance ON public.ifj_runtime_command USING btree (instance_id);



CREATE INDEX fk_ifj_schedule_calendar ON public.ifj_schedule USING btree (calendar_id);



CREATE INDEX fk_ifj_trigger_event_binding ON public.ifj_trigger USING btree (event_binding_id);



CREATE INDEX fk_ifj_trigger_schedule ON public.ifj_trigger USING btree (schedule_id);



CREATE INDEX idx_ifj_app_run_instance_heartbeat ON public.ifj_app_run USING btree (instance_name, status, heartbeat_at, run_id);



CREATE INDEX idx_ifj_app_run_status_heartbeat ON public.ifj_app_run USING btree (status, heartbeat_at, run_id);



CREATE INDEX idx_ifj_audit_actor ON public.ifj_operation_audit USING btree (actor_login_id, occurred_at, id);



CREATE INDEX idx_ifj_audit_target ON public.ifj_operation_audit USING btree (target_type, target_id, occurred_at, id);



CREATE INDEX idx_ifj_audit_time ON public.ifj_operation_audit USING btree (occurred_at, id);



CREATE INDEX idx_ifj_auth_role ON public.ifj_auth_user USING btree (role_code, enabled);



CREATE INDEX idx_ifj_cluster_state_expiry ON public.ifj_cluster_state USING btree (expires_at, state_key);



CREATE INDEX idx_ifj_concurrency_permit_lease ON public.ifj_concurrency_permit USING btree (lease_deadline, scope_key, slot_no);



CREATE INDEX idx_ifj_concurrency_permit_root ON public.ifj_concurrency_permit USING btree (owner_root_instance_id, lease_deadline);



CREATE INDEX idx_ifj_dag_gateway_decision_node ON public.ifj_dag_gateway_decision USING btree (definition_node_id, job_instance_id, evaluation_no);



CREATE INDEX idx_ifj_etl_checkpoint_instance ON public.ifj_etl_checkpoint USING btree (last_instance_id);



CREATE INDEX idx_ifj_event_arrived ON public.ifj_event USING btree (arrived_at, id);



CREATE INDEX idx_ifj_event_binding_event ON public.ifj_event_binding USING btree (event_code, enabled);



CREATE INDEX idx_ifj_event_code_business ON public.ifj_event USING btree (event_code, business_key, arrived_at, id);



CREATE INDEX idx_ifj_event_code_correlation ON public.ifj_event USING btree (event_code, correlation_key, arrived_at, id);



CREATE INDEX idx_ifj_event_root_status_v24 ON public.ifj_event_wait USING btree (status, root_instance_id, id);



CREATE INDEX idx_ifj_event_wait_match_business ON public.ifj_event_wait USING btree (status, event_code, business_key, accept_after, id);



CREATE INDEX idx_ifj_event_wait_match_correlation ON public.ifj_event_wait USING btree (status, event_code, correlation_key, accept_after, id);



CREATE INDEX idx_ifj_event_wait_root_status ON public.ifj_event_wait USING btree (root_instance_id, status);



CREATE INDEX idx_ifj_event_wait_timeout ON public.ifj_event_wait USING btree (status, timeout_at, id);



CREATE INDEX idx_ifj_instance_definition_status ON public.ifj_instance USING btree (definition_id, status);



CREATE INDEX idx_ifj_instance_error_update ON public.ifj_instance USING btree (error_code, update_time, id);



CREATE INDEX idx_ifj_instance_log_instance_time ON public.ifj_instance_log USING btree (instance_id, log_time);



CREATE INDEX idx_ifj_instance_root_history_v25 ON public.ifj_instance USING btree (parent_instance_id, create_time, id);



CREATE INDEX idx_ifj_instance_root_status ON public.ifj_instance USING btree (root_instance_id, status);



CREATE INDEX idx_ifj_instance_root_timeout ON public.ifj_instance USING btree (parent_instance_id, status, timeout_at, id);



CREATE INDEX idx_ifj_instance_status_update ON public.ifj_instance USING btree (status, update_time, id);



CREATE INDEX idx_ifj_instance_target ON public.ifj_instance USING btree (target_type, target_id);



CREATE INDEX idx_ifj_instance_task_definition_status ON public.ifj_instance USING btree (task_definition_id, status);



CREATE INDEX idx_ifj_instance_trigger_id ON public.ifj_instance USING btree (trigger_id);



CREATE INDEX idx_ifj_job_current_definition_id ON public.ifj_job USING btree (current_definition_id);



CREATE INDEX idx_ifj_job_def_group_code ON public.ifj_job_definition_group USING btree (group_code, definition_id);



CREATE INDEX idx_ifj_job_definition_edge_to_node ON public.ifj_job_definition_edge USING btree (to_node_id);



CREATE INDEX idx_ifj_job_definition_job_time ON public.ifj_job_definition USING btree (job_id, create_time, id);



CREATE INDEX idx_ifj_job_definition_node_policy_definition ON public.ifj_job_definition_node_policy USING btree (definition_id);



CREATE INDEX idx_ifj_job_definition_node_ref ON public.ifj_job_definition_node USING btree (node_type, ref_id);



CREATE INDEX idx_ifj_job_definition_node_ref_job_definition ON public.ifj_job_definition_node USING btree (ref_definition_id);



CREATE INDEX idx_ifj_job_definition_node_ref_task_definition ON public.ifj_job_definition_node USING btree (ref_task_definition_id);



CREATE INDEX idx_ifj_job_enabled ON public.ifj_job USING btree (enabled);



CREATE INDEX idx_ifj_maintenance_lease_deadline ON public.ifj_maintenance_lease USING btree (lease_deadline, lease_name);



CREATE INDEX idx_ifj_misfire_schedule_v24 ON public.ifj_schedule_misfire_audit USING btree (schedule_id, observed_at, id);



CREATE INDEX idx_ifj_notification_outbox_created ON public.ifj_notification_outbox USING btree (create_time, id);



CREATE INDEX idx_ifj_notification_outbox_dispatch ON public.ifj_notification_outbox USING btree (status, available_at, id);



CREATE INDEX idx_ifj_notification_outbox_instance ON public.ifj_notification_outbox USING btree (instance_id, status, id);



CREATE INDEX idx_ifj_notification_outbox_lease ON public.ifj_notification_outbox USING btree (status, lease_deadline, id);



CREATE INDEX idx_ifj_notify_binding_target ON public.ifj_notify_binding USING btree (target_type, target_id);



CREATE INDEX idx_ifj_notify_config_type_enabled ON public.ifj_notify_config USING btree (type, enabled);



CREATE INDEX idx_ifj_notify_send_log_config_time ON public.ifj_notify_send_log USING btree (notify_config_id, create_time);



CREATE INDEX idx_ifj_notify_send_log_create_time ON public.ifj_notify_send_log USING btree (create_time, id);



CREATE INDEX idx_ifj_notify_send_log_instance_time ON public.ifj_notify_send_log USING btree (instance_id, create_time);



CREATE INDEX idx_ifj_notify_send_log_result_time ON public.ifj_notify_send_log USING btree (result, create_time);



CREATE INDEX idx_ifj_notify_send_log_target_time ON public.ifj_notify_send_log USING btree (target_type, target_id, create_time);



CREATE INDEX idx_ifj_resource_type_enabled ON public.ifj_resource USING btree (type, enabled);



CREATE INDEX idx_ifj_runtime_command_dispatch ON public.ifj_runtime_command USING btree (trigger_id, status, available_at, expires_at, create_time);



CREATE INDEX idx_ifj_runtime_command_expiration ON public.ifj_runtime_command USING btree (status, expires_at, request_id);



CREATE INDEX idx_ifj_runtime_command_root ON public.ifj_runtime_command USING btree (root_instance_id, create_time);



CREATE INDEX idx_ifj_schedule_due_v24 ON public.ifj_schedule USING btree (enabled, next_fire_time, id);



CREATE INDEX idx_ifj_schedule_fire ON public.ifj_schedule USING btree (enabled, next_fire_time);



CREATE INDEX idx_ifj_schedule_target ON public.ifj_schedule USING btree (target_type, target_id);



CREATE INDEX idx_ifj_task_current_definition_id ON public.ifj_task USING btree (current_task_definition_id);



CREATE INDEX idx_ifj_task_def_group_code ON public.ifj_task_definition_group USING btree (group_code, definition_id);



CREATE INDEX idx_ifj_task_definition_task_time ON public.ifj_task_definition USING btree (task_id, create_time, id);



CREATE INDEX idx_ifj_task_enabled_plugin_type ON public.ifj_task USING btree (enabled, plugin_type);



CREATE INDEX idx_ifj_trigger_definition_status ON public.ifj_trigger USING btree (definition_id, status);



CREATE INDEX idx_ifj_trigger_error_update ON public.ifj_trigger USING btree (error_code, update_time, id);



CREATE INDEX idx_ifj_trigger_event ON public.ifj_trigger USING btree (event_id);



CREATE INDEX idx_ifj_trigger_history_v25 ON public.ifj_trigger USING btree (target_type, target_id, trigger_time, id);



CREATE INDEX idx_ifj_trigger_status_time ON public.ifj_trigger USING btree (status, trigger_time);



CREATE INDEX idx_ifj_trigger_status_update ON public.ifj_trigger USING btree (status, update_time, id);



CREATE INDEX idx_ifj_trigger_target ON public.ifj_trigger USING btree (target_type, target_id);



CREATE INDEX idx_ifj_trigger_task_definition_status ON public.ifj_trigger USING btree (task_definition_id, status);



CREATE INDEX idx_ifj_trigger_work_item_dispatch ON public.ifj_trigger_work_item USING btree (status, available_at, lease_deadline, trigger_id);



CREATE INDEX idx_ifj_trigger_work_item_owner ON public.ifj_trigger_work_item USING btree (lease_owner, lease_deadline, trigger_id);



CREATE INDEX idx_ifj_wait_operator_lookup ON public.ifj_event_wait_operator USING btree (operator_id, wait_id);



CREATE INDEX idx_ifj_work_dispatch_v24 ON public.ifj_trigger_work_item USING btree (status, available_at, trigger_id);



CREATE INDEX idx_ifj_work_expiry_v24 ON public.ifj_trigger_work_item USING btree (status, expires_at, trigger_id);



CREATE INDEX idx_ifj_work_lease_v24 ON public.ifj_trigger_work_item USING btree (status, lease_deadline, trigger_id);



CREATE INDEX idx_mail_channel_updated ON public.ifj_mail_channel USING btree (update_time, id);



CREATE INDEX idx_param_binding_template ON public.ifj_parameter_binding USING btree (template_id);



CREATE TRIGGER trg_ifj_calendar_exception_touch BEFORE UPDATE ON public.ifj_calendar_exception FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_calendar_touch BEFORE UPDATE ON public.ifj_calendar FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_event_binding_touch BEFORE UPDATE ON public.ifj_event_binding FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_event_wait_touch BEFORE UPDATE ON public.ifj_event_wait FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_instance_touch BEFORE UPDATE ON public.ifj_instance FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_job_touch BEFORE UPDATE ON public.ifj_job FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_notify_binding_touch BEFORE UPDATE ON public.ifj_notify_binding FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_notify_config_touch BEFORE UPDATE ON public.ifj_notify_config FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_notify_send_log_touch BEFORE UPDATE ON public.ifj_notify_send_log FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_resource_touch BEFORE UPDATE ON public.ifj_resource FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_schedule_touch BEFORE UPDATE ON public.ifj_schedule FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_schema_capability_touch BEFORE UPDATE ON public.ifj_schema_capability FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_task_touch BEFORE UPDATE ON public.ifj_task FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



CREATE TRIGGER trg_ifj_trigger_touch BEFORE UPDATE ON public.ifj_trigger FOR EACH ROW EXECUTE FUNCTION public.ifj_touch_update_time();



ALTER TABLE ONLY public.ifj_calendar_exception
    ADD CONSTRAINT fk_ifj_calendar_exception_calendar FOREIGN KEY (calendar_id) REFERENCES public.ifj_calendar(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_dag_gateway_decision
    ADD CONSTRAINT fk_ifj_dag_gateway_decision_job FOREIGN KEY (job_instance_id) REFERENCES public.ifj_instance(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_dag_gateway_decision
    ADD CONSTRAINT fk_ifj_dag_gateway_decision_node FOREIGN KEY (definition_node_id) REFERENCES public.ifj_job_definition_node(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_event_wait
    ADD CONSTRAINT fk_ifj_event_wait_event FOREIGN KEY (matched_event_id) REFERENCES public.ifj_event(id) ON DELETE SET NULL;



ALTER TABLE ONLY public.ifj_event_wait
    ADD CONSTRAINT fk_ifj_event_wait_instance FOREIGN KEY (instance_id) REFERENCES public.ifj_instance(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_event_wait
    ADD CONSTRAINT fk_ifj_event_wait_root FOREIGN KEY (root_instance_id) REFERENCES public.ifj_instance(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_instance
    ADD CONSTRAINT fk_ifj_instance_definition FOREIGN KEY (definition_id) REFERENCES public.ifj_job_definition(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_instance
    ADD CONSTRAINT fk_ifj_instance_definition_node FOREIGN KEY (definition_node_id) REFERENCES public.ifj_job_definition_node(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_instance_log
    ADD CONSTRAINT fk_ifj_instance_log_instance FOREIGN KEY (instance_id) REFERENCES public.ifj_instance(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_instance
    ADD CONSTRAINT fk_ifj_instance_parent FOREIGN KEY (parent_instance_id) REFERENCES public.ifj_instance(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_instance
    ADD CONSTRAINT fk_ifj_instance_root FOREIGN KEY (root_instance_id) REFERENCES public.ifj_instance(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_instance
    ADD CONSTRAINT fk_ifj_instance_task_definition FOREIGN KEY (task_definition_id) REFERENCES public.ifj_task_definition(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_instance
    ADD CONSTRAINT fk_ifj_instance_trigger FOREIGN KEY (trigger_id) REFERENCES public.ifj_trigger(id) ON DELETE SET NULL;



ALTER TABLE ONLY public.ifj_job_definition_group
    ADD CONSTRAINT fk_ifj_job_def_group_definition FOREIGN KEY (definition_id) REFERENCES public.ifj_job_definition(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_job_definition_edge
    ADD CONSTRAINT fk_ifj_job_definition_edge_definition FOREIGN KEY (definition_id) REFERENCES public.ifj_job_definition(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_job_definition_edge
    ADD CONSTRAINT fk_ifj_job_definition_edge_from_node FOREIGN KEY (from_node_id) REFERENCES public.ifj_job_definition_node(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_job_definition_edge
    ADD CONSTRAINT fk_ifj_job_definition_edge_to_node FOREIGN KEY (to_node_id) REFERENCES public.ifj_job_definition_node(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_job_definition
    ADD CONSTRAINT fk_ifj_job_definition_job FOREIGN KEY (job_id) REFERENCES public.ifj_job(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_job_definition_node
    ADD CONSTRAINT fk_ifj_job_definition_node_definition FOREIGN KEY (definition_id) REFERENCES public.ifj_job_definition(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_job_definition_node_policy
    ADD CONSTRAINT fk_ifj_job_definition_node_policy_definition FOREIGN KEY (definition_id) REFERENCES public.ifj_job_definition(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_job_definition_node_policy
    ADD CONSTRAINT fk_ifj_job_definition_node_policy_node FOREIGN KEY (node_id) REFERENCES public.ifj_job_definition_node(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_job_definition_node
    ADD CONSTRAINT fk_ifj_job_definition_node_ref_job_definition FOREIGN KEY (ref_definition_id) REFERENCES public.ifj_job_definition(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_job_definition_node
    ADD CONSTRAINT fk_ifj_job_definition_node_ref_task_definition FOREIGN KEY (ref_task_definition_id) REFERENCES public.ifj_task_definition(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_notify_binding
    ADD CONSTRAINT fk_ifj_notify_binding_config FOREIGN KEY (notify_config_id) REFERENCES public.ifj_notify_config(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_runtime_command
    ADD CONSTRAINT fk_ifj_runtime_command_instance FOREIGN KEY (instance_id) REFERENCES public.ifj_instance(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_runtime_command
    ADD CONSTRAINT fk_ifj_runtime_command_root FOREIGN KEY (root_instance_id) REFERENCES public.ifj_instance(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_runtime_command
    ADD CONSTRAINT fk_ifj_runtime_command_trigger FOREIGN KEY (trigger_id) REFERENCES public.ifj_trigger(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_schedule
    ADD CONSTRAINT fk_ifj_schedule_calendar FOREIGN KEY (calendar_id) REFERENCES public.ifj_calendar(id) ON DELETE SET NULL;



ALTER TABLE ONLY public.ifj_task_definition_group
    ADD CONSTRAINT fk_ifj_task_def_group_definition FOREIGN KEY (definition_id) REFERENCES public.ifj_task_definition(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_task_definition
    ADD CONSTRAINT fk_ifj_task_definition_task FOREIGN KEY (task_id) REFERENCES public.ifj_task(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_trigger
    ADD CONSTRAINT fk_ifj_trigger_definition FOREIGN KEY (definition_id) REFERENCES public.ifj_job_definition(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_trigger
    ADD CONSTRAINT fk_ifj_trigger_event FOREIGN KEY (event_id) REFERENCES public.ifj_event(id) ON DELETE SET NULL;



ALTER TABLE ONLY public.ifj_trigger
    ADD CONSTRAINT fk_ifj_trigger_event_binding FOREIGN KEY (event_binding_id) REFERENCES public.ifj_event_binding(id) ON DELETE SET NULL;



ALTER TABLE ONLY public.ifj_trigger
    ADD CONSTRAINT fk_ifj_trigger_schedule FOREIGN KEY (schedule_id) REFERENCES public.ifj_schedule(id) ON DELETE SET NULL;



ALTER TABLE ONLY public.ifj_trigger
    ADD CONSTRAINT fk_ifj_trigger_task_definition FOREIGN KEY (task_definition_id) REFERENCES public.ifj_task_definition(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_trigger_work_item
    ADD CONSTRAINT fk_ifj_trigger_work_item_root FOREIGN KEY (root_instance_id) REFERENCES public.ifj_instance(id) ON DELETE SET NULL;



ALTER TABLE ONLY public.ifj_trigger_work_item
    ADD CONSTRAINT fk_ifj_trigger_work_item_trigger FOREIGN KEY (trigger_id) REFERENCES public.ifj_trigger(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_event_wait_operator
    ADD CONSTRAINT fk_ifj_wait_operator_wait FOREIGN KEY (wait_id) REFERENCES public.ifj_event_wait(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_parameter_binding
    ADD CONSTRAINT fk_param_binding_template FOREIGN KEY (template_id) REFERENCES public.ifj_parameter_template(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_schedule_misfire_audit
    ADD CONSTRAINT ifj_schedule_misfire_audit_schedule_id_fkey FOREIGN KEY (schedule_id) REFERENCES public.ifj_schedule(id) ON DELETE SET NULL;



ALTER TABLE ONLY public.ifj_task_concurrency_policy
    ADD CONSTRAINT ifj_task_concurrency_policy_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.ifj_task(id) ON DELETE CASCADE;



ALTER TABLE ONLY public.ifj_task_concurrency_resource
    ADD CONSTRAINT ifj_task_concurrency_resource_resource_id_fkey FOREIGN KEY (resource_id) REFERENCES public.ifj_concurrency_resource(id) ON DELETE RESTRICT;



ALTER TABLE ONLY public.ifj_task_concurrency_resource
    ADD CONSTRAINT ifj_task_concurrency_resource_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.ifj_task_concurrency_policy(task_id) ON DELETE CASCADE;





INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_auth_user', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_calendar', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_calendar_exception', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_concurrency_resource', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_dag_gateway_decision', 1, 1000, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_event', 1, 1000, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_event_binding', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_event_wait', 1, 1000, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_instance', 1, 1000, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_instance_log', 1, 1000, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job_definition', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job_definition_edge', 1, 100, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job_definition_node', 1, 100, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job_definition_node_policy', 1, 100, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_mail_channel', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_notification_outbox', 1, 1000, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_notify_binding', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_notify_config', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_notify_send_log', 1, 1000, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_operation_audit', 1, 1000, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_parameter_template', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_resource', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_schedule', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_schedule_misfire_audit', 1, 1000, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_task', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_task_definition', 1, 1, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_trigger', 1, 1000, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('platform-primary-id', 9223372036854775807, 1000, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('auth-accounts', 0);
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('concurrency-policy', 1);
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('runtime-lifecycle', 0);
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('composite-task-concurrency', 1, TRUE, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('conditional-dag', 1, TRUE, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('event-wait-operator-relation', 1, TRUE, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('execution-group-routing', 1, TRUE, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('execution-target-routing', 1, TRUE, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('legacy-dag-removed', 1, TRUE, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('parameter-templates', 1, TRUE, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('per-table-primary-id', 1, TRUE, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('platform-schema', 38, TRUE, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('runtime-history-v2', 1, TRUE, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('schedule-misfire-policy', 1, TRUE, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('stable-id-segment-seed', 1, TRUE, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'), (CURRENT_TIMESTAMP AT TIME ZONE 'UTC'));
CREATE TABLE ifj_release_state (singleton_id INTEGER NOT NULL PRIMARY KEY, state VARCHAR(32) NOT NULL, CONSTRAINT ck_ifj_release_singleton CHECK (singleton_id = 1), CONSTRAINT ck_ifj_release_state CHECK (state IN ('EMPTY','REVIEW_REQUIRED','APPROVED')));
INSERT INTO ifj_release_state (singleton_id, state) VALUES (1, 'EMPTY');
