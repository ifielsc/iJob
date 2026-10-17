PRAGMA defer_foreign_keys = ON;
PRAGMA legacy_alter_table = ON;

CREATE TABLE ifj_job_definition_node_v21 (
    id INTEGER NOT NULL,
    definition_id INTEGER NOT NULL,
    node_key TEXT NOT NULL,
    name TEXT NOT NULL,
    node_kind TEXT NOT NULL DEFAULT 'EXECUTION',
    gateway_mode TEXT,
    node_type TEXT,
    ref_id INTEGER,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    ref_definition_id INTEGER DEFAULT NULL,
    ref_task_definition_id INTEGER DEFAULT NULL,
    CONSTRAINT pk_ifj_job_definition_node PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_node_key UNIQUE (definition_id,node_key),
    CONSTRAINT chk_ifj_job_definition_node_kind CHECK (
        (node_kind = 'EXECUTION' AND gateway_mode IS NULL
            AND node_type IN ('TASK','JOB') AND ref_id IS NOT NULL
            AND (((node_type = 'JOB') AND ref_definition_id IS NOT NULL
                    AND ref_task_definition_id IS NULL)
                OR ((node_type = 'TASK') AND ref_definition_id IS NULL
                    AND ref_task_definition_id IS NOT NULL)))
        OR
        (node_kind = 'GATEWAY' AND gateway_mode = 'EXCLUSIVE'
            AND node_type IS NULL AND ref_id IS NULL
            AND ref_definition_id IS NULL AND ref_task_definition_id IS NULL)
    ),
    CONSTRAINT chk_sqlite_ifj_job_def_node_id CHECK (id > 0),
    CONSTRAINT chk_sqlite_ifj_job_def_node_def CHECK (definition_id > 0),
    CONSTRAINT chk_sqlite_ifj_job_def_node_key CHECK (length(node_key) BETWEEN 1 AND 64),
    CONSTRAINT chk_sqlite_ifj_job_def_node_name CHECK (length(name) BETWEEN 1 AND 128),
    CONSTRAINT fk_ifj_job_definition_node_definition FOREIGN KEY (definition_id)
        REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_job_definition_node_ref_job_definition FOREIGN KEY (ref_definition_id)
        REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_job_definition_node_ref_task_definition FOREIGN KEY (ref_task_definition_id)
        REFERENCES ifj_task_definition (id) ON DELETE RESTRICT
) WITHOUT ROWID;

INSERT INTO ifj_job_definition_node_v21 (
    id, definition_id, node_key, name, node_kind, gateway_mode,
    node_type, ref_id, create_time, ref_definition_id, ref_task_definition_id
)
SELECT
    id, definition_id, node_key, name, 'EXECUTION', NULL,
    node_type, ref_id, create_time, ref_definition_id, ref_task_definition_id
FROM ifj_job_definition_node;

DROP TABLE ifj_job_definition_node;
ALTER TABLE ifj_job_definition_node_v21 RENAME TO ifj_job_definition_node;

CREATE INDEX idx_ifj_job_definition_node_ref
    ON ifj_job_definition_node (node_type, ref_id);
CREATE INDEX idx_ifj_job_definition_node_ref_job_definition
    ON ifj_job_definition_node (ref_definition_id);
CREATE INDEX idx_ifj_job_definition_node_ref_task_definition
    ON ifj_job_definition_node (ref_task_definition_id);

CREATE TABLE ifj_job_definition_edge_v21 (
    id INTEGER NOT NULL,
    definition_id INTEGER NOT NULL,
    from_node_id INTEGER NOT NULL,
    to_node_id INTEGER NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    route_key TEXT,
    route_priority INTEGER,
    default_route INTEGER NOT NULL DEFAULT 0 CHECK (default_route IN (0,1)),
    condition_version INTEGER,
    condition_payload TEXT,
    CONSTRAINT pk_ifj_job_definition_edge PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_edge_path
        UNIQUE (definition_id, from_node_id, to_node_id),
    CONSTRAINT chk_ifj_job_definition_edge_route CHECK (
        (route_key IS NULL AND route_priority IS NULL AND default_route = 0
            AND condition_version IS NULL AND condition_payload IS NULL)
        OR
        (route_key IS NOT NULL AND length(route_key) BETWEEN 1 AND 64
            AND route_priority BETWEEN 0 AND 10000
            AND ((default_route = 1 AND condition_version IS NULL
                    AND condition_payload IS NULL)
                OR (default_route = 0 AND condition_version = 1
                    AND condition_payload IS NOT NULL
                    AND length(condition_payload) BETWEEN 1 AND 16384)))
    ),
    CONSTRAINT fk_ifj_job_definition_edge_definition FOREIGN KEY (definition_id)
        REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_job_definition_edge_from_node FOREIGN KEY (from_node_id)
        REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_job_definition_edge_to_node FOREIGN KEY (to_node_id)
        REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT
) WITHOUT ROWID;

INSERT INTO ifj_job_definition_edge_v21 (
    id, definition_id, from_node_id, to_node_id, create_time,
    route_key, route_priority, default_route, condition_version, condition_payload
)
SELECT
    id, definition_id, from_node_id, to_node_id, create_time,
    NULL, NULL, 0, NULL, NULL
FROM ifj_job_definition_edge;

DROP TABLE ifj_job_definition_edge;
ALTER TABLE ifj_job_definition_edge_v21 RENAME TO ifj_job_definition_edge;

CREATE INDEX idx_ifj_job_definition_edge_to_node
    ON ifj_job_definition_edge (to_node_id);

PRAGMA legacy_alter_table = OFF;

CREATE TABLE ifj_dag_gateway_decision (
    id INTEGER NOT NULL,
    job_instance_id INTEGER NOT NULL,
    definition_node_id INTEGER NOT NULL,
    evaluation_no INTEGER NOT NULL CHECK (evaluation_no > 0),
    selected_edge_ids TEXT,
    context_digest TEXT NOT NULL CHECK (length(context_digest) = 64),
    evaluator_version INTEGER NOT NULL CHECK (evaluator_version = 1),
    status TEXT NOT NULL CHECK (status IN ('SUCCESS','FAILED')),
    error_code TEXT,
    execution_fence INTEGER NOT NULL CHECK (execution_fence >= 0),
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    CONSTRAINT pk_ifj_dag_gateway_decision PRIMARY KEY (id),
    CONSTRAINT uk_ifj_dag_gateway_decision_eval
        UNIQUE (job_instance_id, definition_node_id, evaluation_no),
    CONSTRAINT chk_ifj_dag_gateway_decision_status CHECK (
        (status = 'SUCCESS' AND selected_edge_ids IS NOT NULL AND error_code IS NULL)
        OR (status = 'FAILED' AND selected_edge_ids IS NULL AND error_code IS NOT NULL)
    ),
    CONSTRAINT fk_ifj_dag_gateway_decision_job FOREIGN KEY (job_instance_id)
        REFERENCES ifj_instance (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_dag_gateway_decision_node FOREIGN KEY (definition_node_id)
        REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT
) WITHOUT ROWID;
CREATE INDEX idx_ifj_dag_gateway_decision_node
    ON ifj_dag_gateway_decision (definition_node_id, job_instance_id, evaluation_no);

UPDATE ifj_schema_capability
SET capability_version = 21,
    update_time = strftime('%Y-%m-%d %H:%M:%f','now')
WHERE capability_key = 'platform-schema';

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'conditional-dag', 1, 1,
    strftime('%Y-%m-%d %H:%M:%f','now'),
    strftime('%Y-%m-%d %H:%M:%f','now')
);
