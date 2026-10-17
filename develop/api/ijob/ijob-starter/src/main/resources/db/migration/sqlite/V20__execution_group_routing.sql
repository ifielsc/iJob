CREATE TABLE ifj_job_definition_group (
    definition_id INTEGER NOT NULL,
    group_code TEXT NOT NULL CHECK (length(group_code) BETWEEN 1 AND 64),
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    PRIMARY KEY (definition_id, group_code),
    FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id) ON DELETE CASCADE
) WITHOUT ROWID;
CREATE INDEX idx_ifj_job_def_group_code
    ON ifj_job_definition_group (group_code, definition_id);

CREATE TABLE ifj_task_definition_group (
    definition_id INTEGER NOT NULL,
    group_code TEXT NOT NULL CHECK (length(group_code) BETWEEN 1 AND 64),
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    PRIMARY KEY (definition_id, group_code),
    FOREIGN KEY (definition_id) REFERENCES ifj_task_definition (id) ON DELETE CASCADE
) WITHOUT ROWID;
CREATE INDEX idx_ifj_task_def_group_code
    ON ifj_task_definition_group (group_code, definition_id);

ALTER TABLE ifj_app_run ADD COLUMN worker_groups TEXT NOT NULL DEFAULT '';
ALTER TABLE ifj_app_run ADD COLUMN runtime_protocol_version INTEGER NOT NULL DEFAULT 1;

UPDATE ifj_schema_capability
SET capability_version = 20,
    update_time = strftime('%Y-%m-%d %H:%M:%f','now')
WHERE capability_key = 'platform-schema';

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'execution-group-routing', 1, 1,
    strftime('%Y-%m-%d %H:%M:%f','now'),
    strftime('%Y-%m-%d %H:%M:%f','now')
);
