ALTER TABLE ifj_job_definition_node
    ADD COLUMN node_kind VARCHAR(16) NOT NULL DEFAULT 'EXECUTION' AFTER name,
    ADD COLUMN gateway_mode VARCHAR(16) NULL AFTER node_kind,
    MODIFY COLUMN node_type VARCHAR(32) NULL,
    MODIFY COLUMN ref_id BIGINT NULL,
    ADD CONSTRAINT ck_ifj_job_def_node_kind CHECK (
        (node_kind = 'EXECUTION' AND gateway_mode IS NULL
            AND node_type IN ('TASK', 'JOB') AND ref_id IS NOT NULL)
        OR
        (node_kind = 'GATEWAY' AND gateway_mode = 'EXCLUSIVE'
            AND node_type IS NULL AND ref_id IS NULL
            AND ref_definition_id IS NULL AND ref_task_definition_id IS NULL)
    );

ALTER TABLE ifj_job_definition_edge
    ADD COLUMN route_key VARCHAR(64) NULL AFTER to_node_id,
    ADD COLUMN route_priority INT NULL AFTER route_key,
    ADD COLUMN default_route BOOLEAN NOT NULL DEFAULT FALSE AFTER route_priority,
    ADD COLUMN condition_version INT NULL AFTER default_route,
    ADD COLUMN condition_payload LONGTEXT NULL AFTER condition_version,
    ADD CONSTRAINT ck_ifj_job_def_edge_route CHECK (
        (route_key IS NULL AND route_priority IS NULL AND default_route = FALSE
            AND condition_version IS NULL AND condition_payload IS NULL)
        OR
        (route_key IS NOT NULL AND route_priority BETWEEN 0 AND 10000
            AND ((default_route = TRUE AND condition_version IS NULL
                    AND condition_payload IS NULL)
                OR (default_route = FALSE AND condition_version = 1
                    AND condition_payload IS NOT NULL)))
    );

CREATE TABLE ifj_dag_gateway_decision (
    id BIGINT NOT NULL,
    job_instance_id BIGINT NOT NULL,
    definition_node_id BIGINT NOT NULL,
    evaluation_no INT NOT NULL,
    selected_edge_ids LONGTEXT NULL,
    context_digest CHAR(64) NOT NULL,
    evaluator_version INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_code VARCHAR(64) NULL,
    execution_fence BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_dag_gateway_decision PRIMARY KEY (id),
    CONSTRAINT uk_ifj_dag_gateway_decision_eval
        UNIQUE (job_instance_id, definition_node_id, evaluation_no),
    CONSTRAINT ck_ifj_dag_gateway_decision_eval CHECK (evaluation_no > 0),
    CONSTRAINT ck_ifj_dag_gateway_decision_version CHECK (evaluator_version = 1),
    CONSTRAINT ck_ifj_dag_gateway_decision_status CHECK (
        (status = 'SUCCESS' AND selected_edge_ids IS NOT NULL AND error_code IS NULL)
        OR (status = 'FAILED' AND selected_edge_ids IS NULL AND error_code IS NOT NULL)
    ),
    CONSTRAINT fk_ifj_dag_gateway_decision_job FOREIGN KEY (job_instance_id)
        REFERENCES ifj_instance (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_dag_gateway_decision_node FOREIGN KEY (definition_node_id)
        REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT,
    INDEX idx_ifj_dag_gateway_decision_node
        (definition_node_id, job_instance_id, evaluation_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

UPDATE ifj_schema_capability
SET capability_version = 21, update_time = UTC_TIMESTAMP(3)
WHERE capability_key = 'platform-schema';

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'conditional-dag', 1, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
);
