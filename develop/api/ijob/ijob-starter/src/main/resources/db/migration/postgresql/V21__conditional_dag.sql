ALTER TABLE ifj_job_definition_node
    ADD COLUMN node_kind varchar(16) NOT NULL DEFAULT 'EXECUTION';
ALTER TABLE ifj_job_definition_node ADD COLUMN gateway_mode varchar(16);
ALTER TABLE ifj_job_definition_node ALTER COLUMN node_type DROP NOT NULL;
ALTER TABLE ifj_job_definition_node ALTER COLUMN ref_id DROP NOT NULL;
ALTER TABLE ifj_job_definition_node ADD CONSTRAINT ck_ifj_job_def_node_kind CHECK (
    (node_kind = 'EXECUTION' AND gateway_mode IS NULL
        AND node_type IN ('TASK', 'JOB') AND ref_id IS NOT NULL)
    OR
    (node_kind = 'GATEWAY' AND gateway_mode = 'EXCLUSIVE'
        AND node_type IS NULL AND ref_id IS NULL
        AND ref_definition_id IS NULL AND ref_task_definition_id IS NULL)
);

ALTER TABLE ifj_job_definition_edge ADD COLUMN route_key varchar(64);
ALTER TABLE ifj_job_definition_edge ADD COLUMN route_priority int;
ALTER TABLE ifj_job_definition_edge
    ADD COLUMN default_route boolean NOT NULL DEFAULT FALSE;
ALTER TABLE ifj_job_definition_edge ADD COLUMN condition_version int;
ALTER TABLE ifj_job_definition_edge ADD COLUMN condition_payload text;
ALTER TABLE ifj_job_definition_edge ADD CONSTRAINT ck_ifj_job_def_edge_route CHECK (
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
    id bigint NOT NULL,
    job_instance_id bigint NOT NULL,
    definition_node_id bigint NOT NULL,
    evaluation_no int NOT NULL,
    selected_edge_ids text,
    context_digest char(64) NOT NULL,
    evaluator_version int NOT NULL,
    status varchar(16) NOT NULL,
    error_code varchar(64),
    execution_fence bigint NOT NULL,
    create_time timestamp(3) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
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
        REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT
);
CREATE INDEX idx_ifj_dag_gateway_decision_node
    ON ifj_dag_gateway_decision (definition_node_id, job_instance_id, evaluation_no);

UPDATE ifj_schema_capability
SET capability_version = 21, update_time = CURRENT_TIMESTAMP(3)
WHERE capability_key = 'platform-schema';

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'conditional-dag', 1, TRUE, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
);
