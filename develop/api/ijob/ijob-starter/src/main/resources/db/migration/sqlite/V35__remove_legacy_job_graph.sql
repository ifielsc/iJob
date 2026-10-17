-- No current writer uses these tables. Reject any unreconciled legacy data before DDL.
CREATE TEMP TABLE ifj_v35_legacy_guard (empty_legacy INTEGER CONSTRAINT legacy_dag_must_be_archived CHECK (empty_legacy = 1));
INSERT INTO ifj_v35_legacy_guard VALUES (CASE WHEN EXISTS (SELECT 1 FROM ifj_job_node) OR EXISTS (SELECT 1 FROM ifj_job_edge) OR EXISTS (SELECT 1 FROM ifj_job_node_policy) OR EXISTS (SELECT 1 FROM ifj_instance WHERE node_id IS NOT NULL) THEN 0 ELSE 1 END);
DROP TABLE ifj_v35_legacy_guard;

PRAGMA foreign_keys = OFF;
SAVEPOINT ifj_v35;
CREATE TABLE ifj_instance_v35 (
    id INTEGER NOT NULL,
    node_id INTEGER DEFAULT NULL,
    parent_instance_id INTEGER DEFAULT NULL,
    root_instance_id INTEGER DEFAULT NULL,
    execution_fence INTEGER NOT NULL DEFAULT 0,
    target_type TEXT NOT NULL,
    target_id INTEGER NOT NULL,
    trigger_type TEXT DEFAULT NULL,
    trigger_id INTEGER DEFAULT NULL,
    status TEXT NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    retry_count INTEGER NOT NULL DEFAULT 0,
    start_time TEXT DEFAULT NULL,
    timeout_at TEXT DEFAULT NULL,
    end_time TEXT DEFAULT NULL,
    message text,
    error_code TEXT DEFAULT NULL,
    message_key TEXT DEFAULT NULL,
    message_args text DEFAULT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    definition_id INTEGER DEFAULT NULL,
    definition_node_id INTEGER DEFAULT NULL,
    task_definition_id INTEGER DEFAULT NULL,
    input_variables text,
    output_variables text, wait_reason TEXT CHECK (wait_reason IS NULL OR length(wait_reason) <= 32), permit_wait_deadline TEXT,
    CONSTRAINT pk_ifj_instance PRIMARY KEY (id),
    CONSTRAINT uk_ifj_instance_parent_node UNIQUE (parent_instance_id,node_id),
    CONSTRAINT uk_ifj_instance_parent_definition_node UNIQUE (parent_instance_id,definition_node_id),
    CONSTRAINT chk_ifj_instance_execution_fence CHECK ((execution_fence >= 0)),
    CONSTRAINT chk_ifj_instance_progress CHECK ((progress between 0 and 100)),
    CONSTRAINT chk_ifj_instance_retry_count CHECK ((retry_count >= 0)),
    CONSTRAINT chk_ifj_instance_status CHECK ((status in ('WAITING','RUNNING','PAUSED','WAITING_EVENT','SUCCESS','FAILED','STOPPED','TIMED_OUT','SKIPPED'))),
    CONSTRAINT chk_sqlite_ifj_instance_id_id CHECK (id IS NULL OR (id > 0 AND id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_node_id_id CHECK (node_id IS NULL OR (node_id > 0 AND node_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_parent_instance_id_id CHECK (parent_instance_id IS NULL OR (parent_instance_id > 0 AND parent_instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_root_instance_id_id CHECK (root_instance_id IS NULL OR (root_instance_id > 0 AND root_instance_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_target_type_len CHECK (target_type IS NULL OR length(target_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_instance_target_id_id CHECK (target_id IS NULL OR (target_id > 0 AND target_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_trigger_type_len CHECK (trigger_type IS NULL OR length(trigger_type) <= 32),
    CONSTRAINT chk_sqlite_ifj_instance_trigger_id_id CHECK (trigger_id IS NULL OR (trigger_id > 0 AND trigger_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_status_len CHECK (status IS NULL OR length(status) <= 32),
    CONSTRAINT chk_sqlite_ifj_instance_start_time_timestamp CHECK (start_time IS NULL OR (length(start_time) = 23 AND start_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_instance_timeout_at_timestamp CHECK (timeout_at IS NULL OR (length(timeout_at) = 23 AND timeout_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_instance_end_time_timestamp CHECK (end_time IS NULL OR (length(end_time) = 23 AND end_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_instance_error_code_len CHECK (error_code IS NULL OR length(error_code) <= 64),
    CONSTRAINT chk_sqlite_ifj_instance_message_key_len CHECK (message_key IS NULL OR length(message_key) <= 128),
    CONSTRAINT chk_sqlite_ifj_instance_create_time_timestamp CHECK (create_time IS NULL OR (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_instance_update_time_timestamp CHECK (update_time IS NULL OR (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]')),
    CONSTRAINT chk_sqlite_ifj_instance_definition_id_id CHECK (definition_id IS NULL OR (definition_id > 0 AND definition_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_definition_node_id_id CHECK (definition_node_id IS NULL OR (definition_node_id > 0 AND definition_node_id <= 9223372036854775807)),
    CONSTRAINT chk_sqlite_ifj_instance_task_definition_id_id CHECK (task_definition_id IS NULL OR (task_definition_id > 0 AND task_definition_id <= 9223372036854775807)),
    CONSTRAINT fk_ifj_instance_definition FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_instance_definition_node FOREIGN KEY (definition_node_id) REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_instance_parent FOREIGN KEY (parent_instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_instance_root FOREIGN KEY (root_instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_instance_task_definition FOREIGN KEY (task_definition_id) REFERENCES ifj_task_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_instance_trigger FOREIGN KEY (trigger_id) REFERENCES ifj_trigger (id) ON DELETE SET NULL
) WITHOUT ROWID;
INSERT INTO ifj_instance_v35 (id, node_id, parent_instance_id, root_instance_id, execution_fence, target_type, target_id, trigger_type, trigger_id, status, progress, retry_count, start_time, timeout_at, end_time, message, error_code, message_key, message_args, create_time, update_time, definition_id, definition_node_id, task_definition_id, input_variables, output_variables, wait_reason, permit_wait_deadline) SELECT id, node_id, parent_instance_id, root_instance_id, execution_fence, target_type, target_id, trigger_type, trigger_id, status, progress, retry_count, start_time, timeout_at, end_time, message, error_code, message_key, message_args, create_time, update_time, definition_id, definition_node_id, task_definition_id, input_variables, output_variables, wait_reason, permit_wait_deadline FROM ifj_instance;
DROP TABLE ifj_instance;
ALTER TABLE ifj_instance_v35 RENAME TO ifj_instance;
CREATE INDEX idx_ifj_instance_root_status ON ifj_instance (root_instance_id,status);
CREATE INDEX idx_ifj_instance_target ON ifj_instance (target_type,target_id);
CREATE INDEX idx_ifj_instance_trigger_id ON ifj_instance (trigger_id);
CREATE INDEX fk_ifj_instance_definition_node ON ifj_instance (definition_node_id);
CREATE INDEX idx_ifj_instance_definition_status ON ifj_instance (definition_id,status);
CREATE INDEX idx_ifj_instance_task_definition_status ON ifj_instance (task_definition_id,status);
CREATE INDEX idx_ifj_instance_status_update ON ifj_instance (status,update_time,id);
CREATE INDEX idx_ifj_instance_error_update ON ifj_instance (error_code,update_time,id);
CREATE INDEX idx_ifj_instance_root_timeout ON ifj_instance (parent_instance_id,status,timeout_at,id);
CREATE INDEX idx_ifj_instance_root_history_v25
    ON ifj_instance (parent_instance_id,create_time,id);
CREATE TRIGGER trg_ifj_instance_touch AFTER UPDATE ON ifj_instance
FOR EACH ROW WHEN NEW.update_time = OLD.update_time
BEGIN
    UPDATE ifj_instance SET update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE id = NEW.id;
END;
DROP TABLE ifj_job_edge;
DROP TABLE ifj_job_node_policy;
DROP TABLE ifj_job_node;
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time)
VALUES ('legacy-dag-removed', 1, 1, strftime('%Y-%m-%d %H:%M:%f','now'), strftime('%Y-%m-%d %H:%M:%f','now'));
UPDATE ifj_schema_capability SET capability_version = 35, update_time = strftime('%Y-%m-%d %H:%M:%f','now') WHERE capability_key = 'platform-schema';
RELEASE SAVEPOINT ifj_v35;
PRAGMA foreign_keys = ON;
