ALTER TABLE ifj_job_definition_node ADD (
    node_kind VARCHAR2(16 CHAR) DEFAULT 'EXECUTION' NOT NULL,
    gateway_mode VARCHAR2(16 CHAR)
);
ALTER TABLE ifj_job_definition_node MODIFY (
    node_type NULL,
    ref_id NULL
);
ALTER TABLE ifj_job_definition_node ADD CONSTRAINT ck_ifj_job_def_node_kind CHECK (
    (node_kind = 'EXECUTION' AND gateway_mode IS NULL
        AND node_type IN ('TASK', 'JOB') AND ref_id IS NOT NULL)
    OR
    (node_kind = 'GATEWAY' AND gateway_mode = 'EXCLUSIVE'
        AND node_type IS NULL AND ref_id IS NULL
        AND ref_definition_id IS NULL AND ref_task_definition_id IS NULL)
);

ALTER TABLE ifj_job_definition_edge ADD (
    route_key VARCHAR2(64 CHAR),
    route_priority NUMBER(10),
    default_route NUMBER(1) DEFAULT 0 NOT NULL,
    condition_version NUMBER(10),
    condition_payload CLOB
);
ALTER TABLE ifj_job_definition_edge ADD CONSTRAINT ck_ifj_job_def_edge_route CHECK (
    (route_key IS NULL AND route_priority IS NULL AND default_route = 0
        AND condition_version IS NULL AND condition_payload IS NULL)
    OR
    (route_key IS NOT NULL AND route_priority BETWEEN 0 AND 10000
        AND ((default_route = 1 AND condition_version IS NULL
                AND condition_payload IS NULL)
            OR (default_route = 0 AND condition_version = 1
                AND condition_payload IS NOT NULL)))
);

CREATE TABLE ifj_dag_gateway_decision (
    id NUMBER(19) NOT NULL,
    job_instance_id NUMBER(19) NOT NULL,
    definition_node_id NUMBER(19) NOT NULL,
    evaluation_no NUMBER(10) NOT NULL,
    selected_edge_ids CLOB,
    context_digest CHAR(64 CHAR) NOT NULL,
    evaluator_version NUMBER(10) NOT NULL,
    status VARCHAR2(16 CHAR) NOT NULL,
    error_code VARCHAR2(64 CHAR),
    execution_fence NUMBER(19) NOT NULL,
    create_time TIMESTAMP(3)
        DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_dag_gateway_decision PRIMARY KEY (id),
    CONSTRAINT uk_ifj_dag_gateway_dec_eval
        UNIQUE (job_instance_id, definition_node_id, evaluation_no),
    CONSTRAINT ck_ifj_dag_gateway_dec_eval CHECK (evaluation_no > 0),
    CONSTRAINT ck_ifj_dag_gateway_dec_ver CHECK (evaluator_version = 1),
    CONSTRAINT ck_ifj_dag_gateway_dec_stat CHECK (
        (status = 'SUCCESS' AND selected_edge_ids IS NOT NULL AND error_code IS NULL)
        OR (status = 'FAILED' AND selected_edge_ids IS NULL AND error_code IS NOT NULL)
    ),
    CONSTRAINT fk_ifj_dag_gateway_dec_job FOREIGN KEY (job_instance_id)
        REFERENCES ifj_instance (id),
    CONSTRAINT fk_ifj_dag_gateway_dec_node FOREIGN KEY (definition_node_id)
        REFERENCES ifj_job_definition_node (id)
);
CREATE INDEX idx_ifj_dag_gateway_dec_node
    ON ifj_dag_gateway_decision (definition_node_id, job_instance_id, evaluation_no);

UPDATE ifj_schema_capability
SET capability_version = 21,
    update_time = CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
WHERE capability_key = 'platform-schema';

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'conditional-dag', 1, 1,
    CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)),
    CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
);
