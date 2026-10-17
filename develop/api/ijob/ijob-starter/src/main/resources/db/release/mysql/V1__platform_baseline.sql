-- First release baseline. Logical platform schema 38; physical migration 1.
CREATE TABLE `ifj_app_run` (
  `run_id` varchar(64) NOT NULL,
  `instance_name` varchar(128) DEFAULT NULL,
  `runtime_mode` varchar(16) NOT NULL DEFAULT 'STANDALONE',
  `roles` varchar(64) NOT NULL DEFAULT 'API,SCHEDULER,WORKER',
  `worker_groups` varchar(1100) NOT NULL DEFAULT '',
  `worker_task_ids` varchar(2100) NOT NULL DEFAULT '',
  `worker_job_ids` varchar(2100) NOT NULL DEFAULT '',
  `runtime_protocol_version` int NOT NULL DEFAULT '1',
  `status` varchar(16) NOT NULL,
  `application_version` varchar(64) DEFAULT NULL,
  `host_name` varchar(255) DEFAULT NULL,
  `process_id` bigint DEFAULT NULL,
  `started_at` datetime(3) NOT NULL,
  `heartbeat_at` datetime(3) NOT NULL,
  `stopped_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`run_id`),
  KEY `idx_ifj_app_run_status_heartbeat` (`status`,`heartbeat_at`,`run_id`),
  KEY `idx_ifj_app_run_instance_heartbeat` (`instance_name`,`status`,`heartbeat_at`,`run_id`),
  CONSTRAINT `chk_ifj_app_run_runtime_mode` CHECK ((`runtime_mode` in (_utf8mb4'DEVELOPMENT',_utf8mb4'STANDALONE',_utf8mb4'CLUSTER'))),
  CONSTRAINT `chk_ifj_app_run_status` CHECK ((`status` in (_utf8mb4'STARTING',_utf8mb4'RUNNING',_utf8mb4'DRAINING',_utf8mb4'STOPPED',_utf8mb4'CRASHED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_auth_user` (
  `id` bigint NOT NULL,
  `login_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `username` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `display_name` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `password_hash` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `role_code` varchar(16) COLLATE utf8mb4_bin NOT NULL DEFAULT 'VIEWER',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `must_change_password` tinyint(1) NOT NULL DEFAULT '1',
  `security_version` bigint NOT NULL DEFAULT '0',
  `revision` bigint NOT NULL DEFAULT '0',
  `created_by` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_auth_login` (`login_id`),
  UNIQUE KEY `uk_ifj_auth_username` (`username`),
  KEY `idx_ifj_auth_role` (`role_code`,`enabled`),
  CONSTRAINT `ck_ifj_auth_enabled` CHECK ((`enabled` in (0,1))),
  CONSTRAINT `ck_ifj_auth_pwd_change` CHECK ((`must_change_password` in (0,1))),
  CONSTRAINT `ck_ifj_auth_role` CHECK ((`role_code` in (_utf8mb4'ADMIN',_utf8mb4'OPERATOR',_utf8mb4'VIEWER'))),
  CONSTRAINT `ck_ifj_auth_version` CHECK (((`security_version` >= 0) and (`revision` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `ifj_calendar` (
  `id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `workdays_mask` int NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_calendar_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_calendar_exception` (
  `id` bigint NOT NULL,
  `calendar_id` bigint NOT NULL,
  `date` date NOT NULL,
  `type` varchar(32) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_calendar_exception_date` (`calendar_id`,`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_cluster_state` (
  `state_key` varchar(512) NOT NULL,
  `state_value` longtext NOT NULL,
  `expires_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`state_key`),
  KEY `idx_ifj_cluster_state_expiry` (`expires_at`,`state_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_concurrency_permit` (
  `scope_key` varchar(191) NOT NULL,
  `slot_no` int NOT NULL,
  `owner_instance_id` bigint DEFAULT NULL,
  `owner_root_instance_id` bigint DEFAULT NULL,
  `owner_trigger_id` bigint DEFAULT NULL,
  `lease_owner` varchar(128) DEFAULT NULL,
  `permit_token` varchar(36) DEFAULT NULL,
  `permit_fence` bigint NOT NULL DEFAULT '0',
  `execution_fence` bigint DEFAULT NULL,
  `lease_deadline` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`scope_key`,`slot_no`),
  UNIQUE KEY `uk_ifj_concurrency_permit_token` (`permit_token`),
  KEY `idx_ifj_concurrency_permit_lease` (`lease_deadline`,`scope_key`,`slot_no`),
  KEY `idx_ifj_concurrency_permit_root` (`owner_root_instance_id`,`lease_deadline`),
  CONSTRAINT `chk_ifj_concurrency_permit_fence` CHECK ((`permit_fence` >= 0)),
  CONSTRAINT `chk_ifj_concurrency_permit_lease` CHECK ((((`owner_instance_id` is null) and (`owner_root_instance_id` is null) and (`owner_trigger_id` is null) and (`lease_owner` is null) and (`permit_token` is null) and (`execution_fence` is null) and (`lease_deadline` is null)) or ((`owner_instance_id` is not null) and (`owner_root_instance_id` is not null) and (`owner_trigger_id` is not null) and (`lease_owner` is not null) and (`permit_token` is not null) and (`execution_fence` is not null) and (`lease_deadline` is not null)))),
  CONSTRAINT `chk_ifj_concurrency_permit_slot` CHECK ((`slot_no` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_concurrency_resource` (
  `id` bigint NOT NULL,
  `code` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `name` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `max_concurrency` int NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `description` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `version` bigint NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_concurrency_resource_code` (`code`),
  CONSTRAINT `chk_ifj_concurrency_resource_max` CHECK ((`max_concurrency` between 1 and 10000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `ifj_dag_gateway_decision` (
  `id` bigint NOT NULL,
  `job_instance_id` bigint NOT NULL,
  `definition_node_id` bigint NOT NULL,
  `evaluation_no` int NOT NULL,
  `selected_edge_ids` longtext COLLATE utf8mb4_bin,
  `context_digest` char(64) COLLATE utf8mb4_bin NOT NULL,
  `evaluator_version` int NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin NOT NULL,
  `error_code` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `execution_fence` bigint NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_dag_gateway_decision_eval` (`job_instance_id`,`definition_node_id`,`evaluation_no`),
  KEY `idx_ifj_dag_gateway_decision_node` (`definition_node_id`,`job_instance_id`,`evaluation_no`),
  CONSTRAINT `ck_ifj_dag_gateway_decision_eval` CHECK ((`evaluation_no` > 0)),
  CONSTRAINT `ck_ifj_dag_gateway_decision_status` CHECK ((((`status` = _utf8mb4'SUCCESS') and (`selected_edge_ids` is not null) and (`error_code` is null)) or ((`status` = _utf8mb4'FAILED') and (`selected_edge_ids` is null) and (`error_code` is not null)))),
  CONSTRAINT `ck_ifj_dag_gateway_decision_version` CHECK ((`evaluator_version` = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `ifj_etl_checkpoint` (
  `task_id` bigint NOT NULL,
  `checkpoint_key` varchar(128) NOT NULL,
  `source_fingerprint` char(64) NOT NULL,
  `watermark_type` varchar(32) NOT NULL,
  `watermark_value` longtext,
  `tie_breaker_value` longtext,
  `last_instance_id` bigint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `owner_instance_id` bigint DEFAULT NULL,
  `owner_token` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`task_id`,`checkpoint_key`),
  KEY `idx_ifj_etl_checkpoint_instance` (`last_instance_id`),
  CONSTRAINT `ck_ifj_etl_checkpoint_task` CHECK ((`task_id` > 0)),
  CONSTRAINT `ck_ifj_etl_checkpoint_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_etl_file_record` (
  `task_id` bigint NOT NULL,
  `checkpoint_key` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `path_hash` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `content_digest` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `marker_value` varchar(2048) COLLATE utf8mb4_bin NOT NULL,
  `last_instance_id` bigint NOT NULL,
  PRIMARY KEY (`task_id`,`checkpoint_key`,`path_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `ifj_event` (
  `id` bigint NOT NULL,
  `event_code` varchar(128) NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_key` varchar(128) NOT NULL,
  `business_key` varchar(256) DEFAULT NULL,
  `correlation_key` varchar(256) DEFAULT NULL,
  `idempotency_key` varchar(256) NOT NULL,
  `occurred_at` datetime(3) NOT NULL,
  `arrived_at` datetime(3) NOT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `payload` longtext,
  `payload_digest` char(64) DEFAULT NULL,
  `payload_bytes` int NOT NULL DEFAULT '0',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_event_source_idempotency` (`source_key`,`idempotency_key`),
  KEY `idx_ifj_event_code_business` (`event_code`,`business_key`,`arrived_at`,`id`),
  KEY `idx_ifj_event_code_correlation` (`event_code`,`correlation_key`,`arrived_at`,`id`),
  KEY `idx_ifj_event_arrived` (`arrived_at`,`id`),
  CONSTRAINT `chk_ifj_event_payload_bytes` CHECK ((`payload_bytes` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_event_binding` (
  `id` bigint NOT NULL,
  `target_type` varchar(32) NOT NULL,
  `target_id` bigint NOT NULL,
  `event_code` varchar(128) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_event_binding_target` (`target_type`,`target_id`,`event_code`),
  KEY `idx_ifj_event_binding_event` (`event_code`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_event_wait` (
  `id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `root_instance_id` bigint NOT NULL,
  `attempt_no` int NOT NULL,
  `wait_type` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL,
  `event_code` varchar(128) DEFAULT NULL,
  `business_key` varchar(256) DEFAULT NULL,
  `correlation_key` varchar(256) DEFAULT NULL,
  `accept_after` datetime(3) NOT NULL,
  `timeout_at` datetime(3) DEFAULT NULL,
  `matched_event_id` bigint DEFAULT NULL,
  `allowed_operator_ids` longtext,
  `decision_by` varchar(128) DEFAULT NULL,
  `decision_comment` varchar(500) DEFAULT NULL,
  `resolved_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_event_wait_attempt` (`instance_id`,`attempt_no`),
  KEY `fk_ifj_event_wait_event` (`matched_event_id`),
  KEY `idx_ifj_event_wait_match_business` (`status`,`event_code`,`business_key`,`accept_after`,`id`),
  KEY `idx_ifj_event_wait_match_correlation` (`status`,`event_code`,`correlation_key`,`accept_after`,`id`),
  KEY `idx_ifj_event_wait_root_status` (`root_instance_id`,`status`),
  KEY `idx_ifj_event_wait_timeout` (`status`,`timeout_at`,`id`),
  KEY `idx_ifj_event_root_status_v24` (`status`,`root_instance_id`,`id`),
  CONSTRAINT `chk_ifj_event_wait_attempt` CHECK ((`attempt_no` >= 0)),
  CONSTRAINT `chk_ifj_event_wait_status` CHECK ((`status` in (_utf8mb4'WAITING',_utf8mb4'MATCHED',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'TIMED_OUT',_utf8mb4'WITHDRAWN',_utf8mb4'STOPPED'))),
  CONSTRAINT `chk_ifj_event_wait_type` CHECK ((`wait_type` in (_utf8mb4'EVENT',_utf8mb4'APPROVAL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_event_wait_operator` (
  `wait_id` bigint NOT NULL,
  `operator_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`wait_id`,`operator_id`),
  KEY `idx_ifj_wait_operator_lookup` (`operator_id`,`wait_id`),
  CONSTRAINT `chk_ifj_wait_operator_id` CHECK ((char_length(`operator_id`) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_id_segment` (
  `segment_key` varchar(64) NOT NULL,
  `next_value` bigint NOT NULL,
  `segment_size` int NOT NULL,
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`segment_key`),
  CONSTRAINT `chk_ifj_id_segment_next_value` CHECK ((`next_value` > 0)),
  CONSTRAINT `chk_ifj_id_segment_size` CHECK ((`segment_size` between 1 and 100000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_instance` (
  `id` bigint NOT NULL,
  `node_id` bigint DEFAULT NULL,
  `parent_instance_id` bigint DEFAULT NULL,
  `root_instance_id` bigint DEFAULT NULL,
  `execution_fence` bigint NOT NULL DEFAULT '0',
  `target_type` varchar(32) NOT NULL,
  `target_id` bigint NOT NULL,
  `trigger_type` varchar(32) DEFAULT NULL,
  `trigger_id` bigint DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `progress` int NOT NULL DEFAULT '0',
  `retry_count` int NOT NULL DEFAULT '0',
  `start_time` datetime(3) DEFAULT NULL,
  `timeout_at` datetime(3) DEFAULT NULL,
  `wait_reason` varchar(32) DEFAULT NULL,
  `permit_wait_deadline` datetime(3) DEFAULT NULL,
  `end_time` datetime(3) DEFAULT NULL,
  `message` text,
  `error_code` varchar(64) DEFAULT NULL,
  `message_key` varchar(128) DEFAULT NULL,
  `message_args` json DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `definition_id` bigint DEFAULT NULL,
  `definition_node_id` bigint DEFAULT NULL,
  `task_definition_id` bigint DEFAULT NULL,
  `input_variables` longtext,
  `output_variables` longtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_instance_parent_node` (`parent_instance_id`,`node_id`),
  UNIQUE KEY `uk_ifj_instance_parent_definition_node` (`parent_instance_id`,`definition_node_id`),
  KEY `idx_ifj_instance_root_status` (`root_instance_id`,`status`),
  KEY `idx_ifj_instance_target` (`target_type`,`target_id`),
  KEY `idx_ifj_instance_trigger_id` (`trigger_id`),
  KEY `fk_ifj_instance_definition_node` (`definition_node_id`),
  KEY `idx_ifj_instance_definition_status` (`definition_id`,`status`),
  KEY `idx_ifj_instance_task_definition_status` (`task_definition_id`,`status`),
  KEY `idx_ifj_instance_status_update` (`status`,`update_time`,`id`),
  KEY `idx_ifj_instance_error_update` (`error_code`,`update_time`,`id`),
  KEY `idx_ifj_instance_root_timeout` (`parent_instance_id`,`status`,`timeout_at`,`id`),
  KEY `idx_ifj_instance_root_history_v25` (`parent_instance_id`,`create_time`,`id`),
  CONSTRAINT `chk_ifj_instance_execution_fence` CHECK ((`execution_fence` >= 0)),
  CONSTRAINT `chk_ifj_instance_progress` CHECK ((`progress` between 0 and 100)),
  CONSTRAINT `chk_ifj_instance_retry_count` CHECK ((`retry_count` >= 0)),
  CONSTRAINT `chk_ifj_instance_status` CHECK ((`status` in (_utf8mb4'WAITING',_utf8mb4'RUNNING',_utf8mb4'PAUSED',_utf8mb4'WAITING_EVENT',_utf8mb4'SUCCESS',_utf8mb4'FAILED',_utf8mb4'STOPPED',_utf8mb4'TIMED_OUT',_utf8mb4'SKIPPED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_instance_log` (
  `id` bigint NOT NULL,
  `instance_id` bigint NOT NULL,
  `log_time` datetime(3) NOT NULL,
  `level` varchar(16) NOT NULL,
  `content` longtext NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ifj_instance_log_instance_time` (`instance_id`,`log_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_job` (
  `id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `timeout` int DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `description` varchar(500) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `current_definition_id` bigint DEFAULT NULL,
  `current_revision` bigint DEFAULT NULL,
  `retired` tinyint(1) NOT NULL DEFAULT '0',
  `retired_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ifj_job_enabled` (`enabled`),
  KEY `idx_ifj_job_current_definition_id` (`current_definition_id`),
  CONSTRAINT `ck_ifj_job_current_pointer_pair` CHECK ((((`current_definition_id` is null) and (`current_revision` is null)) or ((`current_definition_id` is not null) and (`current_revision` is not null)))),
  CONSTRAINT `ck_ifj_job_retired_state` CHECK ((((`retired` = 0) and (`retired_time` is null)) or ((`retired` = 1) and (`retired_time` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_job_definition` (
  `id` bigint NOT NULL,
  `job_id` bigint NOT NULL,
  `revision` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `timeout` int DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `layout_json` longtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_job_definition_job_revision` (`job_id`,`revision`),
  KEY `idx_ifj_job_definition_job_time` (`job_id`,`create_time`,`id`),
  CONSTRAINT `ck_ifj_job_definition_revision` CHECK ((`revision` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_job_definition_edge` (
  `id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `from_node_id` bigint NOT NULL,
  `to_node_id` bigint NOT NULL,
  `route_key` varchar(64) DEFAULT NULL,
  `route_priority` int DEFAULT NULL,
  `default_route` tinyint(1) NOT NULL DEFAULT '0',
  `condition_version` int DEFAULT NULL,
  `condition_payload` longtext,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_job_definition_edge_path` (`definition_id`,`from_node_id`,`to_node_id`),
  KEY `fk_ifj_job_definition_edge_from_node` (`from_node_id`),
  KEY `idx_ifj_job_definition_edge_to_node` (`to_node_id`),
  CONSTRAINT `ck_ifj_job_def_edge_route` CHECK ((((`route_key` is null) and (`route_priority` is null) and (`default_route` = false) and (`condition_version` is null) and (`condition_payload` is null)) or ((`route_key` is not null) and (`route_priority` between 0 and 10000) and (((`default_route` = true) and (`condition_version` is null) and (`condition_payload` is null)) or ((`default_route` = false) and (`condition_version` = 1) and (`condition_payload` is not null))))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_job_definition_group` (
  `definition_id` bigint NOT NULL,
  `group_code` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`definition_id`,`group_code`),
  KEY `idx_ifj_job_def_group_code` (`group_code`,`definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `ifj_job_definition_node` (
  `id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `node_key` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `node_kind` varchar(16) NOT NULL DEFAULT 'EXECUTION',
  `gateway_mode` varchar(16) DEFAULT NULL,
  `node_type` varchar(32) DEFAULT NULL,
  `ref_id` bigint DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `ref_definition_id` bigint DEFAULT NULL,
  `ref_task_definition_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_job_definition_node_key` (`definition_id`,`node_key`),
  KEY `idx_ifj_job_definition_node_ref` (`node_type`,`ref_id`),
  KEY `idx_ifj_job_definition_node_ref_job_definition` (`ref_definition_id`),
  KEY `idx_ifj_job_definition_node_ref_task_definition` (`ref_task_definition_id`),
  CONSTRAINT `chk_ifj_job_definition_node_ref_snapshot` CHECK ((((`node_kind` = _utf8mb4'GATEWAY') and (`ref_definition_id` is null) and (`ref_task_definition_id` is null)) or ((`node_kind` = _utf8mb4'EXECUTION') and (((`node_type` = _utf8mb4'JOB') and (`ref_definition_id` is not null) and (`ref_task_definition_id` is null)) or ((`node_type` = _utf8mb4'TASK') and (`ref_definition_id` is null) and (`ref_task_definition_id` is not null)))))),
  CONSTRAINT `ck_ifj_job_def_node_kind` CHECK ((((`node_kind` = _utf8mb4'EXECUTION') and (`gateway_mode` is null) and (`node_type` in (_utf8mb4'TASK',_utf8mb4'JOB')) and (`ref_id` is not null)) or ((`node_kind` = _utf8mb4'GATEWAY') and (`gateway_mode` = _utf8mb4'EXCLUSIVE') and (`node_type` is null) and (`ref_id` is null) and (`ref_definition_id` is null) and (`ref_task_definition_id` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_job_definition_node_policy` (
  `id` bigint NOT NULL,
  `definition_id` bigint NOT NULL,
  `node_id` bigint NOT NULL,
  `fail_strategy` varchar(32) NOT NULL,
  `execute_condition` varchar(32) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_job_definition_node_policy_node` (`node_id`),
  KEY `idx_ifj_job_definition_node_policy_definition` (`definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_mail_channel` (
  `id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `config` longtext NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `revision` bigint NOT NULL,
  `create_time` datetime(3) NOT NULL,
  `update_time` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_mail_channel_updated` (`update_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_maintenance_lease` (
  `lease_name` varchar(64) NOT NULL,
  `lease_owner` varchar(128) DEFAULT NULL,
  `lease_token` varchar(36) DEFAULT NULL,
  `lease_deadline` datetime(3) DEFAULT NULL,
  `fence_token` bigint NOT NULL DEFAULT '0',
  `last_acquired_at` datetime(3) DEFAULT NULL,
  `last_completed_at` datetime(3) DEFAULT NULL,
  `last_success` tinyint(1) DEFAULT NULL,
  `last_failure_code` varchar(64) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`lease_name`),
  UNIQUE KEY `uk_ifj_maintenance_lease_token` (`lease_token`),
  KEY `idx_ifj_maintenance_lease_deadline` (`lease_deadline`,`lease_name`),
  CONSTRAINT `chk_ifj_maintenance_lease_fence` CHECK ((`fence_token` >= 0)),
  CONSTRAINT `chk_ifj_maintenance_lease_holder` CHECK ((((`lease_owner` is null) and (`lease_token` is null) and (`lease_deadline` is null)) or ((`lease_owner` is not null) and (`lease_token` is not null) and (`lease_deadline` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_notification_outbox` (
  `id` bigint NOT NULL,
  `deduplication_key` varchar(191) NOT NULL,
  `notify_config_id` bigint NOT NULL,
  `notify_type` varchar(32) DEFAULT NULL,
  `config_snapshot` longtext,
  `config_enabled` tinyint(1) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `subject` varchar(255) NOT NULL,
  `content` longtext NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `correlation_id` varchar(64) DEFAULT NULL,
  `operator_id` varchar(128) DEFAULT NULL,
  `target_type` varchar(32) DEFAULT NULL,
  `target_id` bigint DEFAULT NULL,
  `instance_id` bigint DEFAULT NULL,
  `notify_binding_id` bigint DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `available_at` datetime(3) NOT NULL,
  `lease_owner` varchar(128) DEFAULT NULL,
  `claim_token` varchar(36) DEFAULT NULL,
  `lease_deadline` datetime(3) DEFAULT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `max_attempts` int NOT NULL,
  `last_failure_code` varchar(64) DEFAULT NULL,
  `last_failure_summary` varchar(500) DEFAULT NULL,
  `delivered_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `content_version` int NOT NULL DEFAULT '0',
  `http_status` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_notification_outbox_deduplication` (`deduplication_key`),
  UNIQUE KEY `uk_ifj_notification_outbox_claim_token` (`claim_token`),
  KEY `idx_ifj_notification_outbox_dispatch` (`status`,`available_at`,`id`),
  KEY `idx_ifj_notification_outbox_lease` (`status`,`lease_deadline`,`id`),
  KEY `idx_ifj_notification_outbox_instance` (`instance_id`,`status`,`id`),
  KEY `idx_ifj_notification_outbox_created` (`create_time`,`id`),
  CONSTRAINT `chk_ifj_notification_outbox_attempts` CHECK (((`attempt_count` >= 0) and (`max_attempts` between 1 and 100))),
  CONSTRAINT `chk_ifj_notification_outbox_config_snapshot` CHECK ((((`notify_type` is null) and (`config_snapshot` is null) and (`config_enabled` is null)) or ((`notify_type` is not null) and (`config_snapshot` is not null) and (`config_enabled` is not null)))),
  CONSTRAINT `chk_ifj_notification_outbox_delivery` CHECK ((((`status` = _utf8mb4'DELIVERED') and (`delivered_at` is not null)) or ((`status` <> _utf8mb4'DELIVERED') and (`delivered_at` is null)))),
  CONSTRAINT `chk_ifj_notification_outbox_lease` CHECK ((((`status` = _utf8mb4'CLAIMED') and (`lease_owner` is not null) and (`claim_token` is not null) and (`lease_deadline` is not null)) or ((`status` <> _utf8mb4'CLAIMED') and (`lease_owner` is null) and (`claim_token` is null) and (`lease_deadline` is null)))),
  CONSTRAINT `chk_ifj_notification_outbox_status` CHECK ((`status` in (_utf8mb4'READY',_utf8mb4'CLAIMED',_utf8mb4'RETRY_WAIT',_utf8mb4'DELIVERED',_utf8mb4'DEAD')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_notify_binding` (
  `id` bigint NOT NULL,
  `target_type` varchar(32) NOT NULL,
  `target_id` bigint NOT NULL,
  `notify_config_id` bigint NOT NULL,
  `notify_policy` varchar(32) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_notify_binding_target` (`target_type`,`target_id`,`notify_config_id`),
  KEY `fk_ifj_notify_binding_config` (`notify_config_id`),
  KEY `idx_ifj_notify_binding_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_notify_config` (
  `id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `type` varchar(32) NOT NULL,
  `config` longtext NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ifj_notify_config_type_enabled` (`type`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_notify_rate_limit` (
  `target_key` varchar(64) NOT NULL,
  `next_allowed_at` bigint NOT NULL,
  PRIMARY KEY (`target_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_notify_send_log` (
  `id` bigint NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `correlation_id` varchar(64) DEFAULT NULL,
  `operator_id` varchar(128) DEFAULT NULL,
  `target_type` varchar(32) DEFAULT NULL,
  `target_id` bigint DEFAULT NULL,
  `instance_id` bigint DEFAULT NULL,
  `notify_binding_id` bigint DEFAULT NULL,
  `notify_config_id` bigint NOT NULL,
  `notify_type` varchar(32) DEFAULT NULL,
  `result` varchar(32) NOT NULL,
  `failure_code` varchar(64) DEFAULT NULL,
  `failure_summary` varchar(500) DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ifj_notify_send_log_create_time` (`create_time`,`id`),
  KEY `idx_ifj_notify_send_log_config_time` (`notify_config_id`,`create_time`),
  KEY `idx_ifj_notify_send_log_target_time` (`target_type`,`target_id`,`create_time`),
  KEY `idx_ifj_notify_send_log_instance_time` (`instance_id`,`create_time`),
  KEY `idx_ifj_notify_send_log_result_time` (`result`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_operation_audit` (
  `id` bigint NOT NULL,
  `occurred_at` datetime(3) NOT NULL,
  `actor_login_id` varchar(128) COLLATE utf8mb4_bin NOT NULL,
  `actor_name` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `action_code` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `target_type` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `target_id` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `result_code` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `trace_id` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `request_id` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `changed_fields` varchar(1024) COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ifj_audit_time` (`occurred_at`,`id`),
  KEY `idx_ifj_audit_actor` (`actor_login_id`,`occurred_at`,`id`),
  KEY `idx_ifj_audit_target` (`target_type`,`target_id`,`occurred_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `ifj_parameter_binding` (
  `target_type` varchar(16) NOT NULL,
  `target_id` bigint NOT NULL,
  `template_id` bigint NOT NULL,
  PRIMARY KEY (`target_type`,`target_id`),
  KEY `idx_param_binding_template` (`template_id`),
  CONSTRAINT `ck_param_binding_type` CHECK ((`target_type` in (_utf8mb4'TASK',_utf8mb4'JOB')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_parameter_template` (
  `id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `fields_json` longtext NOT NULL,
  `revision` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_plugin_concurrency_policy` (
  `plugin_type` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `max_concurrency` int NOT NULL,
  `version` bigint NOT NULL DEFAULT '1',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`plugin_type`),
  CONSTRAINT `chk_ifj_plugin_concurrency_max` CHECK ((`max_concurrency` between 1 and 10000)),
  CONSTRAINT `chk_ifj_plugin_concurrency_version` CHECK ((`version` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `ifj_resource` (
  `id` bigint NOT NULL,
  `code` varchar(128) NOT NULL,
  `name` varchar(128) NOT NULL,
  `type` varchar(32) NOT NULL,
  `config` longtext NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `description` varchar(500) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_resource_code` (`code`),
  KEY `idx_ifj_resource_type_enabled` (`type`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_runtime_command` (
  `request_id` varchar(191) NOT NULL,
  `instance_id` bigint NOT NULL,
  `root_instance_id` bigint NOT NULL,
  `trigger_id` bigint NOT NULL,
  `command_type` varchar(16) NOT NULL,
  `status` varchar(16) NOT NULL,
  `requested_by` varchar(128) NOT NULL,
  `result_message` varchar(500) DEFAULT NULL,
  `available_at` datetime(3) NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `processing_owner` varchar(128) DEFAULT NULL,
  `processing_token` varchar(36) DEFAULT NULL,
  `processing_fence` bigint DEFAULT NULL,
  `processed_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`request_id`),
  UNIQUE KEY `uk_ifj_runtime_command_processing_token` (`processing_token`),
  KEY `fk_ifj_runtime_command_instance` (`instance_id`),
  KEY `idx_ifj_runtime_command_dispatch` (`trigger_id`,`status`,`available_at`,`expires_at`,`create_time`),
  KEY `idx_ifj_runtime_command_root` (`root_instance_id`,`create_time`),
  KEY `idx_ifj_runtime_command_expiration` (`status`,`expires_at`,`request_id`),
  CONSTRAINT `chk_ifj_runtime_command_processing` CHECK ((((`status` = _utf8mb4'PROCESSING') and (`processing_owner` is not null) and (`processing_token` is not null) and (`processing_fence` is not null)) or ((`status` <> _utf8mb4'PROCESSING') and (`processing_owner` is null) and (`processing_token` is null) and (`processing_fence` is null)))),
  CONSTRAINT `chk_ifj_runtime_command_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'PROCESSING',_utf8mb4'SUCCEEDED',_utf8mb4'REJECTED',_utf8mb4'FAILED',_utf8mb4'EXPIRED'))),
  CONSTRAINT `chk_ifj_runtime_command_type` CHECK ((`command_type` in (_utf8mb4'STOP',_utf8mb4'PAUSE',_utf8mb4'RESUME',_utf8mb4'RETRY')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_runtime_guard` (
  `guard_key` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `revision` bigint NOT NULL,
  PRIMARY KEY (`guard_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `ifj_schedule` (
  `id` bigint NOT NULL,
  `target_type` varchar(32) NOT NULL,
  `target_id` bigint NOT NULL,
  `schedule_type` varchar(32) NOT NULL,
  `cron_expr` varchar(128) DEFAULT NULL,
  `interval_sec` int DEFAULT NULL,
  `calendar_id` bigint DEFAULT NULL,
  `start_time` datetime(3) DEFAULT NULL,
  `end_time` datetime(3) DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `last_fire_time` datetime(3) DEFAULT NULL,
  `next_fire_time` datetime(3) DEFAULT NULL,
  `fire_claim_token` varchar(36) DEFAULT NULL,
  `fire_claimed_at` datetime(3) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `variables` longtext,
  `misfire_policy` varchar(32) DEFAULT NULL,
  `misfire_grace_sec` int DEFAULT NULL,
  `max_catchup_count` int DEFAULT NULL,
  `max_queue_delay_sec` int DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `parameter_form` longtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_schedule_fire_claim_token` (`fire_claim_token`),
  KEY `fk_ifj_schedule_calendar` (`calendar_id`),
  KEY `idx_ifj_schedule_target` (`target_type`,`target_id`),
  KEY `idx_ifj_schedule_fire` (`enabled`,`next_fire_time`),
  KEY `idx_ifj_schedule_due_v24` (`enabled`,`next_fire_time`,`id`),
  CONSTRAINT `chk_ifj_schedule_catchup_count` CHECK (((`max_catchup_count` is null) or (`max_catchup_count` between 1 and 1000))),
  CONSTRAINT `chk_ifj_schedule_fire_claim` CHECK ((((`fire_claim_token` is null) and (`fire_claimed_at` is null)) or ((`fire_claim_token` is not null) and (`fire_claimed_at` is not null)))),
  CONSTRAINT `chk_ifj_schedule_misfire_grace` CHECK (((`misfire_grace_sec` is null) or (`misfire_grace_sec` >= 0))),
  CONSTRAINT `chk_ifj_schedule_misfire_policy` CHECK (((`misfire_policy` is null) or (`misfire_policy` in (_utf8mb4'SKIP',_utf8mb4'FIRE_ONCE',_utf8mb4'CATCH_UP_BOUNDED')))),
  CONSTRAINT `chk_ifj_schedule_queue_delay` CHECK (((`max_queue_delay_sec` is null) or (`max_queue_delay_sec` between 1 and 31536000)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_schedule_misfire_audit` (
  `id` bigint NOT NULL,
  `schedule_id` bigint DEFAULT NULL,
  `fire_claim_token` varchar(36) COLLATE utf8mb4_bin NOT NULL,
  `policy` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `observed_at` datetime(3) NOT NULL,
  `first_due_time` datetime(3) NOT NULL,
  `last_due_time` datetime(3) DEFAULT NULL,
  `due_count` int NOT NULL,
  `count_capped` tinyint(1) NOT NULL,
  `created_count` int NOT NULL,
  `skipped_count` int NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_schedule_misfire_claim` (`fire_claim_token`),
  KEY `idx_ifj_misfire_schedule_v24` (`schedule_id`,`observed_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `ifj_schema_capability` (
  `capability_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `capability_version` int NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`capability_key`),
  CONSTRAINT `chk_ifj_schema_cap_enabled` CHECK ((`enabled` in (0,1))),
  CONSTRAINT `chk_ifj_schema_cap_version` CHECK ((`capability_version` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_task` (
  `id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `plugin_type` varchar(32) NOT NULL,
  `plugin_config` longtext NOT NULL,
  `retry_times` int NOT NULL DEFAULT '0',
  `retry_interval` int NOT NULL DEFAULT '0',
  `timeout` int DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `description` varchar(500) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `current_task_definition_id` bigint DEFAULT NULL,
  `current_revision` bigint DEFAULT NULL,
  `retired` tinyint(1) NOT NULL DEFAULT '0',
  `retired_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ifj_task_enabled_plugin_type` (`enabled`,`plugin_type`),
  KEY `idx_ifj_task_current_definition_id` (`current_task_definition_id`),
  CONSTRAINT `ck_ifj_task_current_pointer_pair` CHECK ((((`current_task_definition_id` is null) and (`current_revision` is null)) or ((`current_task_definition_id` is not null) and (`current_revision` is not null)))),
  CONSTRAINT `ck_ifj_task_current_revision_positive` CHECK (((`current_revision` is null) or (`current_revision` > 0))),
  CONSTRAINT `ck_ifj_task_retired_state` CHECK ((((`retired` = 0) and (`retired_time` is null)) or ((`retired` = 1) and (`retired_time` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_task_concurrency_policy` (
  `task_id` bigint NOT NULL,
  `task_max_concurrency` int DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '1',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`task_id`),
  CONSTRAINT `chk_ifj_task_concurrency_max` CHECK (((`task_max_concurrency` is null) or (`task_max_concurrency` between 1 and 10000))),
  CONSTRAINT `chk_ifj_task_concurrency_version` CHECK ((`version` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `ifj_task_concurrency_resource` (
  `task_id` bigint NOT NULL,
  `resource_id` bigint NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`task_id`,`resource_id`),
  KEY `fk_ifj_task_concurrency_resource_resource` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `ifj_task_definition` (
  `id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `revision` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `plugin_type` varchar(32) NOT NULL,
  `plugin_config` longtext NOT NULL,
  `retry_times` int NOT NULL,
  `retry_interval` int NOT NULL,
  `timeout` int DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_task_definition_task_revision` (`task_id`,`revision`),
  KEY `idx_ifj_task_definition_task_time` (`task_id`,`create_time`,`id`),
  CONSTRAINT `ck_ifj_task_definition_revision` CHECK ((`revision` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_task_definition_group` (
  `definition_id` bigint NOT NULL,
  `group_code` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`definition_id`,`group_code`),
  KEY `idx_ifj_task_def_group_code` (`group_code`,`definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `ifj_trigger` (
  `id` bigint NOT NULL,
  `schedule_id` bigint DEFAULT NULL,
  `target_type` varchar(32) NOT NULL,
  `target_id` bigint NOT NULL,
  `trigger_type` varchar(32) NOT NULL,
  `idempotency_key` varchar(191) NOT NULL,
  `event_code` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `trigger_time` datetime(3) NOT NULL,
  `message` text,
  `error_code` varchar(64) DEFAULT NULL,
  `message_key` varchar(128) DEFAULT NULL,
  `message_args` json DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `definition_id` bigint DEFAULT NULL,
  `task_definition_id` bigint DEFAULT NULL,
  `variables` longtext,
  `event_id` bigint DEFAULT NULL,
  `event_binding_id` bigint DEFAULT NULL,
  `business_date` varchar(10) DEFAULT NULL,
  `business_time` varchar(48) DEFAULT NULL,
  `business_zone_id` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ifj_trigger_idempotency_key` (`idempotency_key`),
  UNIQUE KEY `uk_ifj_trigger_event_binding` (`event_id`,`event_binding_id`),
  KEY `fk_ifj_trigger_schedule` (`schedule_id`),
  KEY `idx_ifj_trigger_status_time` (`status`,`trigger_time`),
  KEY `idx_ifj_trigger_target` (`target_type`,`target_id`),
  KEY `idx_ifj_trigger_definition_status` (`definition_id`,`status`),
  KEY `idx_ifj_trigger_task_definition_status` (`task_definition_id`,`status`),
  KEY `fk_ifj_trigger_event_binding` (`event_binding_id`),
  KEY `idx_ifj_trigger_event` (`event_id`),
  KEY `idx_ifj_trigger_status_update` (`status`,`update_time`,`id`),
  KEY `idx_ifj_trigger_error_update` (`error_code`,`update_time`,`id`),
  KEY `idx_ifj_trigger_history_v25` (`target_type`,`target_id`,`trigger_time`,`id`),
  CONSTRAINT `chk_ifj_trigger_status` CHECK ((`status` in (_utf8mb4'WAITING',_utf8mb4'RUNNING',_utf8mb4'SUCCESS',_utf8mb4'FAILED',_utf8mb4'STOPPED',_utf8mb4'TIMED_OUT',_utf8mb4'SKIPPED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ifj_trigger_work_item` (
  `trigger_id` bigint NOT NULL,
  `root_instance_id` bigint DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `available_at` datetime(3) NOT NULL,
  `expires_at` datetime(3) DEFAULT NULL,
  `lease_owner` varchar(128) DEFAULT NULL,
  `claim_token` varchar(36) DEFAULT NULL,
  `lease_deadline` datetime(3) DEFAULT NULL,
  `fence_token` bigint NOT NULL DEFAULT '0',
  `attempt_count` int NOT NULL DEFAULT '0',
  `max_attempts` int NOT NULL,
  `last_error` varchar(500) DEFAULT NULL,
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`trigger_id`),
  UNIQUE KEY `uk_ifj_trigger_work_item_claim_token` (`claim_token`),
  UNIQUE KEY `uk_ifj_trigger_work_item_root` (`root_instance_id`),
  KEY `idx_ifj_trigger_work_item_dispatch` (`status`,`available_at`,`lease_deadline`,`trigger_id`),
  KEY `idx_ifj_trigger_work_item_owner` (`lease_owner`,`lease_deadline`,`trigger_id`),
  KEY `idx_ifj_work_dispatch_v24` (`status`,`available_at`,`trigger_id`),
  KEY `idx_ifj_work_lease_v24` (`status`,`lease_deadline`,`trigger_id`),
  KEY `idx_ifj_work_expiry_v24` (`status`,`expires_at`,`trigger_id`),
  CONSTRAINT `chk_ifj_trigger_work_item_attempts` CHECK (((`attempt_count` >= 0) and (`max_attempts` between 1 and 100))),
  CONSTRAINT `chk_ifj_trigger_work_item_fence` CHECK ((`fence_token` >= 0)),
  CONSTRAINT `chk_ifj_trigger_work_item_lease` CHECK ((((`status` = _utf8mb4'CLAIMED') and (`lease_owner` is not null) and (`claim_token` is not null) and (`lease_deadline` is not null)) or ((`status` <> _utf8mb4'CLAIMED') and (`lease_owner` is null) and (`claim_token` is null) and (`lease_deadline` is null)))),
  CONSTRAINT `chk_ifj_trigger_work_item_status` CHECK ((`status` in (_utf8mb4'READY',_utf8mb4'CLAIMED',_utf8mb4'SUSPENDED',_utf8mb4'RETRY_WAIT',_utf8mb4'COMPLETED',_utf8mb4'DEAD')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `ifj_calendar_exception` ADD CONSTRAINT `fk_ifj_calendar_exception_calendar` FOREIGN KEY (`calendar_id`) REFERENCES `ifj_calendar` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_dag_gateway_decision` ADD CONSTRAINT `fk_ifj_dag_gateway_decision_job` FOREIGN KEY (`job_instance_id`) REFERENCES `ifj_instance` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_dag_gateway_decision` ADD CONSTRAINT `fk_ifj_dag_gateway_decision_node` FOREIGN KEY (`definition_node_id`) REFERENCES `ifj_job_definition_node` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_event_wait` ADD CONSTRAINT `fk_ifj_event_wait_event` FOREIGN KEY (`matched_event_id`) REFERENCES `ifj_event` (`id`) ON DELETE SET NULL;

ALTER TABLE `ifj_event_wait` ADD CONSTRAINT `fk_ifj_event_wait_instance` FOREIGN KEY (`instance_id`) REFERENCES `ifj_instance` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_event_wait` ADD CONSTRAINT `fk_ifj_event_wait_root` FOREIGN KEY (`root_instance_id`) REFERENCES `ifj_instance` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_event_wait_operator` ADD CONSTRAINT `fk_ifj_wait_operator_wait` FOREIGN KEY (`wait_id`) REFERENCES `ifj_event_wait` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_instance` ADD CONSTRAINT `fk_ifj_instance_definition` FOREIGN KEY (`definition_id`) REFERENCES `ifj_job_definition` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_instance` ADD CONSTRAINT `fk_ifj_instance_definition_node` FOREIGN KEY (`definition_node_id`) REFERENCES `ifj_job_definition_node` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_instance` ADD CONSTRAINT `fk_ifj_instance_parent` FOREIGN KEY (`parent_instance_id`) REFERENCES `ifj_instance` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_instance` ADD CONSTRAINT `fk_ifj_instance_root` FOREIGN KEY (`root_instance_id`) REFERENCES `ifj_instance` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_instance` ADD CONSTRAINT `fk_ifj_instance_task_definition` FOREIGN KEY (`task_definition_id`) REFERENCES `ifj_task_definition` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_instance` ADD CONSTRAINT `fk_ifj_instance_trigger` FOREIGN KEY (`trigger_id`) REFERENCES `ifj_trigger` (`id`) ON DELETE SET NULL;

ALTER TABLE `ifj_instance_log` ADD CONSTRAINT `fk_ifj_instance_log_instance` FOREIGN KEY (`instance_id`) REFERENCES `ifj_instance` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_job_definition` ADD CONSTRAINT `fk_ifj_job_definition_job` FOREIGN KEY (`job_id`) REFERENCES `ifj_job` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_job_definition_edge` ADD CONSTRAINT `fk_ifj_job_definition_edge_definition` FOREIGN KEY (`definition_id`) REFERENCES `ifj_job_definition` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_job_definition_edge` ADD CONSTRAINT `fk_ifj_job_definition_edge_from_node` FOREIGN KEY (`from_node_id`) REFERENCES `ifj_job_definition_node` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_job_definition_edge` ADD CONSTRAINT `fk_ifj_job_definition_edge_to_node` FOREIGN KEY (`to_node_id`) REFERENCES `ifj_job_definition_node` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_job_definition_group` ADD CONSTRAINT `fk_ifj_job_def_group_definition` FOREIGN KEY (`definition_id`) REFERENCES `ifj_job_definition` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_job_definition_node` ADD CONSTRAINT `fk_ifj_job_definition_node_definition` FOREIGN KEY (`definition_id`) REFERENCES `ifj_job_definition` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_job_definition_node` ADD CONSTRAINT `fk_ifj_job_definition_node_ref_job_definition` FOREIGN KEY (`ref_definition_id`) REFERENCES `ifj_job_definition` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_job_definition_node` ADD CONSTRAINT `fk_ifj_job_definition_node_ref_task_definition` FOREIGN KEY (`ref_task_definition_id`) REFERENCES `ifj_task_definition` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_job_definition_node_policy` ADD CONSTRAINT `fk_ifj_job_definition_node_policy_definition` FOREIGN KEY (`definition_id`) REFERENCES `ifj_job_definition` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_job_definition_node_policy` ADD CONSTRAINT `fk_ifj_job_definition_node_policy_node` FOREIGN KEY (`node_id`) REFERENCES `ifj_job_definition_node` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_notify_binding` ADD CONSTRAINT `fk_ifj_notify_binding_config` FOREIGN KEY (`notify_config_id`) REFERENCES `ifj_notify_config` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_parameter_binding` ADD CONSTRAINT `fk_param_binding_template` FOREIGN KEY (`template_id`) REFERENCES `ifj_parameter_template` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_runtime_command` ADD CONSTRAINT `fk_ifj_runtime_command_instance` FOREIGN KEY (`instance_id`) REFERENCES `ifj_instance` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_runtime_command` ADD CONSTRAINT `fk_ifj_runtime_command_root` FOREIGN KEY (`root_instance_id`) REFERENCES `ifj_instance` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_runtime_command` ADD CONSTRAINT `fk_ifj_runtime_command_trigger` FOREIGN KEY (`trigger_id`) REFERENCES `ifj_trigger` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_schedule` ADD CONSTRAINT `fk_ifj_schedule_calendar` FOREIGN KEY (`calendar_id`) REFERENCES `ifj_calendar` (`id`) ON DELETE SET NULL;

ALTER TABLE `ifj_schedule_misfire_audit` ADD CONSTRAINT `fk_ifj_schedule_misfire_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `ifj_schedule` (`id`) ON DELETE SET NULL;

ALTER TABLE `ifj_task_concurrency_policy` ADD CONSTRAINT `fk_ifj_task_concurrency_policy_task` FOREIGN KEY (`task_id`) REFERENCES `ifj_task` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_task_concurrency_resource` ADD CONSTRAINT `fk_ifj_task_concurrency_resource_resource` FOREIGN KEY (`resource_id`) REFERENCES `ifj_concurrency_resource` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_task_concurrency_resource` ADD CONSTRAINT `fk_ifj_task_concurrency_resource_task` FOREIGN KEY (`task_id`) REFERENCES `ifj_task_concurrency_policy` (`task_id`) ON DELETE CASCADE;

ALTER TABLE `ifj_task_definition` ADD CONSTRAINT `fk_ifj_task_definition_task` FOREIGN KEY (`task_id`) REFERENCES `ifj_task` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_task_definition_group` ADD CONSTRAINT `fk_ifj_task_def_group_definition` FOREIGN KEY (`definition_id`) REFERENCES `ifj_task_definition` (`id`) ON DELETE CASCADE;

ALTER TABLE `ifj_trigger` ADD CONSTRAINT `fk_ifj_trigger_definition` FOREIGN KEY (`definition_id`) REFERENCES `ifj_job_definition` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_trigger` ADD CONSTRAINT `fk_ifj_trigger_event` FOREIGN KEY (`event_id`) REFERENCES `ifj_event` (`id`) ON DELETE SET NULL;

ALTER TABLE `ifj_trigger` ADD CONSTRAINT `fk_ifj_trigger_event_binding` FOREIGN KEY (`event_binding_id`) REFERENCES `ifj_event_binding` (`id`) ON DELETE SET NULL;

ALTER TABLE `ifj_trigger` ADD CONSTRAINT `fk_ifj_trigger_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `ifj_schedule` (`id`) ON DELETE SET NULL;

ALTER TABLE `ifj_trigger` ADD CONSTRAINT `fk_ifj_trigger_task_definition` FOREIGN KEY (`task_definition_id`) REFERENCES `ifj_task_definition` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ifj_trigger_work_item` ADD CONSTRAINT `fk_ifj_trigger_work_item_root` FOREIGN KEY (`root_instance_id`) REFERENCES `ifj_instance` (`id`) ON DELETE SET NULL;

ALTER TABLE `ifj_trigger_work_item` ADD CONSTRAINT `fk_ifj_trigger_work_item_trigger` FOREIGN KEY (`trigger_id`) REFERENCES `ifj_trigger` (`id`) ON DELETE CASCADE;

INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_auth_user', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_calendar', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_calendar_exception', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_concurrency_resource', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_dag_gateway_decision', 1, 1000, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_event', 1, 1000, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_event_binding', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_event_wait', 1, 1000, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_instance', 1, 1000, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_instance_log', 1, 1000, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job_definition', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job_definition_edge', 1, 100, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job_definition_node', 1, 100, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_job_definition_node_policy', 1, 100, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_mail_channel', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_notification_outbox', 1, 1000, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_notify_binding', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_notify_config', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_notify_send_log', 1, 1000, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_operation_audit', 1, 1000, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_parameter_template', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_resource', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_schedule', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_schedule_misfire_audit', 1, 1000, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_task', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_task_definition', 1, 1, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('pk:ifj_trigger', 1, 1000, UTC_TIMESTAMP(3));
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time) VALUES ('platform-primary-id', 9223372036854775807, 1000, UTC_TIMESTAMP(3));
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('auth-accounts', 0);
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('concurrency-policy', 1);
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('runtime-lifecycle', 0);
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('composite-task-concurrency', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('conditional-dag', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('event-wait-operator-relation', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('execution-group-routing', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('execution-target-routing', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('legacy-dag-removed', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('parameter-templates', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('per-table-primary-id', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('platform-schema', 38, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('runtime-history-v2', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('schedule-misfire-policy', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time) VALUES ('stable-id-segment-seed', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
CREATE TABLE ifj_release_state (singleton_id INTEGER NOT NULL PRIMARY KEY, state VARCHAR(32) NOT NULL, CONSTRAINT ck_ifj_release_singleton CHECK (singleton_id = 1), CONSTRAINT ck_ifj_release_state CHECK (state IN ('EMPTY','REVIEW_REQUIRED','APPROVED')));
INSERT INTO ifj_release_state (singleton_id, state) VALUES (1, 'EMPTY');
