CREATE TABLE ifj_event (
    id BIGINT NOT NULL,
    event_code VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_key VARCHAR(128) NOT NULL,
    business_key VARCHAR(256) NULL,
    correlation_key VARCHAR(256) NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    arrived_at DATETIME(3) NOT NULL,
    summary VARCHAR(500) NULL,
    payload LONGTEXT NULL,
    payload_digest CHAR(64) NULL,
    payload_bytes INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_event PRIMARY KEY (id),
    CONSTRAINT uk_ifj_event_source_idempotency
        UNIQUE (source_key, idempotency_key),
    CONSTRAINT chk_ifj_event_payload_bytes CHECK (payload_bytes >= 0),
    INDEX idx_ifj_event_code_business
        (event_code, business_key, arrived_at, id),
    INDEX idx_ifj_event_code_correlation
        (event_code, correlation_key, arrived_at, id),
    INDEX idx_ifj_event_arrived (arrived_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ifj_event_wait (
    id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    root_instance_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    wait_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    event_code VARCHAR(128) NULL,
    business_key VARCHAR(256) NULL,
    correlation_key VARCHAR(256) NULL,
    accept_after DATETIME(3) NOT NULL,
    timeout_at DATETIME(3) NULL,
    matched_event_id BIGINT NULL,
    allowed_operator_ids LONGTEXT NULL,
    decision_by VARCHAR(128) NULL,
    decision_comment VARCHAR(500) NULL,
    resolved_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_event_wait PRIMARY KEY (id),
    CONSTRAINT uk_ifj_event_wait_attempt UNIQUE (instance_id, attempt_no),
    CONSTRAINT fk_ifj_event_wait_instance FOREIGN KEY (instance_id)
        REFERENCES ifj_instance (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_event_wait_root FOREIGN KEY (root_instance_id)
        REFERENCES ifj_instance (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_event_wait_event FOREIGN KEY (matched_event_id)
        REFERENCES ifj_event (id) ON DELETE SET NULL,
    CONSTRAINT chk_ifj_event_wait_attempt CHECK (attempt_no >= 0),
    CONSTRAINT chk_ifj_event_wait_type CHECK (wait_type IN ('EVENT', 'APPROVAL')),
    CONSTRAINT chk_ifj_event_wait_status CHECK (status IN (
        'WAITING', 'MATCHED', 'APPROVED', 'REJECTED',
        'TIMED_OUT', 'WITHDRAWN', 'STOPPED')),
    INDEX idx_ifj_event_wait_match_business
        (status, event_code, business_key, accept_after, id),
    INDEX idx_ifj_event_wait_match_correlation
        (status, event_code, correlation_key, accept_after, id),
    INDEX idx_ifj_event_wait_root_status (root_instance_id, status),
    INDEX idx_ifj_event_wait_timeout (status, timeout_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE ifj_trigger
    ADD COLUMN event_id BIGINT NULL,
    ADD COLUMN event_binding_id BIGINT NULL,
    ADD CONSTRAINT fk_ifj_trigger_event FOREIGN KEY (event_id)
        REFERENCES ifj_event (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_ifj_trigger_event_binding FOREIGN KEY (event_binding_id)
        REFERENCES ifj_event_binding (id) ON DELETE SET NULL,
    ADD CONSTRAINT uk_ifj_trigger_event_binding
        UNIQUE (event_id, event_binding_id),
    ADD INDEX idx_ifj_trigger_event (event_id);

ALTER TABLE ifj_instance
    DROP CHECK chk_ifj_instance_status,
    ADD CONSTRAINT chk_ifj_instance_status CHECK (status IN (
        'WAITING', 'RUNNING', 'PAUSED', 'WAITING_EVENT',
        'SUCCESS', 'FAILED', 'STOPPED', 'TIMED_OUT', 'SKIPPED'));

ALTER TABLE ifj_job_definition_node
    ADD COLUMN ref_definition_id BIGINT NULL,
    ADD COLUMN ref_task_definition_id BIGINT NULL,
    ADD CONSTRAINT fk_ifj_job_definition_node_ref_job_definition
        FOREIGN KEY (ref_definition_id)
        REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_ifj_job_definition_node_ref_task_definition
        FOREIGN KEY (ref_task_definition_id)
        REFERENCES ifj_task_definition (id) ON DELETE RESTRICT,
    ADD INDEX idx_ifj_job_definition_node_ref_job_definition
        (ref_definition_id),
    ADD INDEX idx_ifj_job_definition_node_ref_task_definition
        (ref_task_definition_id);

UPDATE ifj_job_definition_node n
JOIN ifj_job j ON n.node_type = 'JOB' AND n.ref_id = j.id
SET n.ref_definition_id = j.current_definition_id;

UPDATE ifj_job_definition_node n
JOIN ifj_task t ON n.node_type = 'TASK' AND n.ref_id = t.id
SET n.ref_task_definition_id = t.current_task_definition_id;

ALTER TABLE ifj_job_definition_node
    ADD CONSTRAINT chk_ifj_job_definition_node_ref_snapshot CHECK (
        (node_type = 'JOB' AND ref_definition_id IS NOT NULL
            AND ref_task_definition_id IS NULL)
        OR
        (node_type = 'TASK' AND ref_definition_id IS NULL
            AND ref_task_definition_id IS NOT NULL));
