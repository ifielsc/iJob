ALTER TABLE dbo.ifj_job_definition_node ADD
    node_kind varchar(16) NOT NULL
        CONSTRAINT df_ifj_job_def_node_kind DEFAULT 'EXECUTION',
    gateway_mode varchar(16) NULL;
ALTER TABLE dbo.ifj_job_definition_node
    DROP CONSTRAINT chk_ifj_job_definition_node_ref_snapshot;
DROP INDEX idx_ifj_job_definition_node_ref
    ON dbo.ifj_job_definition_node;
ALTER TABLE dbo.ifj_job_definition_node ALTER COLUMN node_type varchar(32) NULL;
ALTER TABLE dbo.ifj_job_definition_node ALTER COLUMN ref_id bigint NULL;
CREATE INDEX idx_ifj_job_definition_node_ref
    ON dbo.ifj_job_definition_node (node_type, ref_id);
ALTER TABLE dbo.ifj_job_definition_node
    ADD CONSTRAINT chk_ifj_job_definition_node_ref_snapshot CHECK (
        (node_type = 'JOB' AND ref_definition_id IS NOT NULL
            AND ref_task_definition_id IS NULL)
        OR
        (node_type = 'TASK' AND ref_definition_id IS NULL
            AND ref_task_definition_id IS NOT NULL)
    );
GO
ALTER TABLE dbo.ifj_job_definition_node ADD CONSTRAINT ck_ifj_job_def_node_kind CHECK (
    (node_kind = 'EXECUTION' AND gateway_mode IS NULL
        AND node_type IN ('TASK', 'JOB') AND ref_id IS NOT NULL)
    OR
    (node_kind = 'GATEWAY' AND gateway_mode = 'EXCLUSIVE'
        AND node_type IS NULL AND ref_id IS NULL
        AND ref_definition_id IS NULL AND ref_task_definition_id IS NULL)
);

ALTER TABLE dbo.ifj_job_definition_edge ADD
    route_key varchar(64) NULL,
    route_priority int NULL,
    default_route bit NOT NULL CONSTRAINT df_ifj_job_def_edge_default DEFAULT 0,
    condition_version int NULL,
    condition_payload varchar(max) NULL;
GO
ALTER TABLE dbo.ifj_job_definition_edge ADD CONSTRAINT ck_ifj_job_def_edge_route CHECK (
    (route_key IS NULL AND route_priority IS NULL AND default_route = 0
        AND condition_version IS NULL AND condition_payload IS NULL)
    OR
    (route_key IS NOT NULL AND route_priority BETWEEN 0 AND 10000
        AND ((default_route = 1 AND condition_version IS NULL
                AND condition_payload IS NULL)
            OR (default_route = 0 AND condition_version = 1
                AND condition_payload IS NOT NULL)))
);

CREATE TABLE dbo.ifj_dag_gateway_decision (
    id bigint NOT NULL,
    job_instance_id bigint NOT NULL,
    definition_node_id bigint NOT NULL,
    evaluation_no int NOT NULL,
    selected_edge_ids varchar(max) NULL,
    context_digest char(64) NOT NULL,
    evaluator_version int NOT NULL,
    status varchar(16) NOT NULL,
    error_code varchar(64) NULL,
    execution_fence bigint NOT NULL,
    create_time datetime2(3) NOT NULL
        CONSTRAINT df_ifj_dag_decision_create DEFAULT SYSUTCDATETIME(),
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
        REFERENCES dbo.ifj_instance (id),
    CONSTRAINT fk_ifj_dag_gateway_decision_node FOREIGN KEY (definition_node_id)
        REFERENCES dbo.ifj_job_definition_node (id)
);
CREATE INDEX idx_ifj_dag_gateway_decision_node
    ON dbo.ifj_dag_gateway_decision (definition_node_id, job_instance_id, evaluation_no);

UPDATE dbo.ifj_schema_capability
SET capability_version = 21, update_time = SYSUTCDATETIME()
WHERE capability_key = 'platform-schema';

INSERT INTO dbo.ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'conditional-dag', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME()
);
