PRAGMA defer_foreign_keys = ON;
PRAGMA legacy_alter_table = ON;

CREATE TABLE ifj_plugin_concurrency_policy (
    plugin_type TEXT NOT NULL PRIMARY KEY,
    max_concurrency INTEGER NOT NULL CHECK (max_concurrency BETWEEN 1 AND 10000),
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now'))
) WITHOUT ROWID;
CREATE TABLE ifj_concurrency_resource (
    id INTEGER NOT NULL PRIMARY KEY,
    code TEXT NOT NULL UNIQUE CHECK (length(code) BETWEEN 1 AND 128),
    name TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 128),
    max_concurrency INTEGER NOT NULL CHECK (max_concurrency BETWEEN 1 AND 10000),
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0,1)),
    description TEXT CHECK (description IS NULL OR length(description) <= 500),
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now'))
) WITHOUT ROWID;
CREATE TABLE ifj_task_concurrency_policy (
    task_id INTEGER NOT NULL PRIMARY KEY,
    task_max_concurrency INTEGER CHECK (task_max_concurrency IS NULL OR task_max_concurrency BETWEEN 1 AND 10000),
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    FOREIGN KEY (task_id) REFERENCES ifj_task(id) ON DELETE CASCADE
) WITHOUT ROWID;
CREATE TABLE ifj_task_concurrency_resource (
    task_id INTEGER NOT NULL,
    resource_id INTEGER NOT NULL,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    PRIMARY KEY (task_id,resource_id),
    FOREIGN KEY (task_id) REFERENCES ifj_task_concurrency_policy(task_id) ON DELETE CASCADE,
    FOREIGN KEY (resource_id) REFERENCES ifj_concurrency_resource(id) ON DELETE RESTRICT
) WITHOUT ROWID;

INSERT INTO ifj_concurrency_resource
    (id, code, name, max_concurrency, enabled, description, create_time, update_time)
SELECT MIN(id), 'legacy.' || MIN(id), 'Legacy resource ' || MIN(id),
       CASE WHEN lock_key IS NULL OR TRIM(lock_key) = '' THEN 0
            WHEN MIN(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
                   = MAX(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
            THEN MIN(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
            ELSE 0 END,
       1, 'Migrated from legacy lock key: ' || lock_key,
       strftime('%Y-%m-%d %H:%M:%f','now'), strftime('%Y-%m-%d %H:%M:%f','now')
FROM ifj_task WHERE concurrency_type IN ('RESOURCE','RESOURCE_LIMIT') GROUP BY lock_key;
INSERT INTO ifj_task_concurrency_policy
    (task_id, task_max_concurrency, version, create_time, update_time)
SELECT id, CASE WHEN concurrency_type='TASK_SINGLE' THEN 1 ELSE NULL END, 1,
       strftime('%Y-%m-%d %H:%M:%f','now'), strftime('%Y-%m-%d %H:%M:%f','now')
FROM ifj_task WHERE concurrency_type IN ('TASK_SINGLE','RESOURCE','RESOURCE_LIMIT');
INSERT INTO ifj_task_concurrency_resource (task_id, resource_id, create_time)
SELECT task.id, resource.resource_id, strftime('%Y-%m-%d %H:%M:%f','now')
FROM ifj_task task
JOIN (SELECT lock_key, MIN(id) resource_id FROM ifj_task
      WHERE concurrency_type IN ('RESOURCE','RESOURCE_LIMIT') GROUP BY lock_key) resource
  ON resource.lock_key=task.lock_key
WHERE task.concurrency_type IN ('RESOURCE','RESOURCE_LIMIT');

CREATE TABLE ifj_concurrency_permit_v23 (
    scope_key TEXT NOT NULL,
    slot_no INTEGER NOT NULL,
    owner_instance_id INTEGER,
    owner_root_instance_id INTEGER,
    owner_trigger_id INTEGER,
    lease_owner TEXT,
    permit_token TEXT UNIQUE,
    permit_fence INTEGER NOT NULL DEFAULT 0 CHECK (permit_fence >= 0),
    execution_fence INTEGER,
    lease_deadline TEXT,
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    PRIMARY KEY (scope_key,slot_no),
    CHECK (slot_no > 0),
    CHECK (length(scope_key) <= 191),
    CHECK ((owner_instance_id IS NULL AND owner_root_instance_id IS NULL AND owner_trigger_id IS NULL
            AND lease_owner IS NULL AND permit_token IS NULL AND execution_fence IS NULL AND lease_deadline IS NULL)
        OR (owner_instance_id IS NOT NULL AND owner_root_instance_id IS NOT NULL AND owner_trigger_id IS NOT NULL
            AND lease_owner IS NOT NULL AND permit_token IS NOT NULL AND execution_fence IS NOT NULL AND lease_deadline IS NOT NULL))
) WITHOUT ROWID;
INSERT INTO ifj_concurrency_permit_v23 SELECT * FROM ifj_concurrency_permit;
DROP TABLE ifj_concurrency_permit;
ALTER TABLE ifj_concurrency_permit_v23 RENAME TO ifj_concurrency_permit;
CREATE INDEX idx_ifj_concurrency_permit_lease ON ifj_concurrency_permit (lease_deadline,scope_key,slot_no);
CREATE INDEX idx_ifj_concurrency_permit_root ON ifj_concurrency_permit (owner_root_instance_id,lease_deadline);

CREATE TABLE ifj_job_v23 (
    id INTEGER NOT NULL PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(name) <= 128),
    timeout INTEGER,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0,1)),
    description TEXT CHECK (description IS NULL OR length(description) <= 500),
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    current_definition_id INTEGER,
    current_revision INTEGER,
    retired INTEGER NOT NULL DEFAULT 0 CHECK (retired IN (0,1)),
    retired_time TEXT,
    CHECK ((current_definition_id IS NULL AND current_revision IS NULL) OR (current_definition_id IS NOT NULL AND current_revision IS NOT NULL)),
    CHECK ((retired=0 AND retired_time IS NULL) OR (retired=1 AND retired_time IS NOT NULL))
) WITHOUT ROWID;
INSERT INTO ifj_job_v23
    (id,name,timeout,enabled,description,create_time,update_time,current_definition_id,current_revision,retired,retired_time)
SELECT id,name,timeout,enabled,description,create_time,update_time,current_definition_id,current_revision,retired,retired_time FROM ifj_job;
DROP TABLE ifj_job;
ALTER TABLE ifj_job_v23 RENAME TO ifj_job;
CREATE INDEX idx_ifj_job_enabled ON ifj_job(enabled);
CREATE INDEX idx_ifj_job_current_definition_id ON ifj_job(current_definition_id);
CREATE TRIGGER trg_ifj_job_touch AFTER UPDATE ON ifj_job
FOR EACH ROW WHEN NEW.update_time=OLD.update_time BEGIN
    UPDATE ifj_job SET update_time=strftime('%Y-%m-%d %H:%M:%f','now') WHERE id=NEW.id;
END;

CREATE TABLE ifj_task_v23 (
    id INTEGER NOT NULL PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(name) <= 128),
    plugin_type TEXT NOT NULL CHECK (length(plugin_type) <= 32),
    plugin_config TEXT NOT NULL,
    retry_times INTEGER NOT NULL DEFAULT 0,
    retry_interval INTEGER NOT NULL DEFAULT 0,
    timeout INTEGER,
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0,1)),
    description TEXT CHECK (description IS NULL OR length(description) <= 500),
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    current_task_definition_id INTEGER,
    current_revision INTEGER,
    retired INTEGER NOT NULL DEFAULT 0 CHECK (retired IN (0,1)),
    retired_time TEXT,
    CHECK ((current_task_definition_id IS NULL AND current_revision IS NULL) OR (current_task_definition_id IS NOT NULL AND current_revision IS NOT NULL)),
    CHECK (current_revision IS NULL OR current_revision > 0),
    CHECK ((retired=0 AND retired_time IS NULL) OR (retired=1 AND retired_time IS NOT NULL))
) WITHOUT ROWID;
INSERT INTO ifj_task_v23
    (id,name,plugin_type,plugin_config,retry_times,retry_interval,timeout,enabled,description,create_time,update_time,current_task_definition_id,current_revision,retired,retired_time)
SELECT id,name,plugin_type,plugin_config,retry_times,retry_interval,timeout,enabled,description,create_time,update_time,current_task_definition_id,current_revision,retired,retired_time FROM ifj_task;
DROP TABLE ifj_task;
ALTER TABLE ifj_task_v23 RENAME TO ifj_task;
CREATE INDEX idx_ifj_task_enabled_plugin_type ON ifj_task(enabled,plugin_type);
CREATE INDEX idx_ifj_task_current_definition_id ON ifj_task(current_task_definition_id);
CREATE TRIGGER trg_ifj_task_touch AFTER UPDATE ON ifj_task
FOR EACH ROW WHEN NEW.update_time=OLD.update_time BEGIN
    UPDATE ifj_task SET update_time=strftime('%Y-%m-%d %H:%M:%f','now') WHERE id=NEW.id;
END;

CREATE TABLE ifj_task_definition_v23 (
    id INTEGER NOT NULL PRIMARY KEY,
    task_id INTEGER NOT NULL,
    revision INTEGER NOT NULL CHECK (revision > 0),
    name TEXT NOT NULL CHECK (length(name) <= 128),
    plugin_type TEXT NOT NULL CHECK (length(plugin_type) <= 32),
    plugin_config TEXT NOT NULL,
    retry_times INTEGER NOT NULL,
    retry_interval INTEGER NOT NULL,
    timeout INTEGER,
    enabled INTEGER NOT NULL CHECK (enabled IN (0,1)),
    description TEXT CHECK (description IS NULL OR length(description) <= 500),
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    UNIQUE (task_id,revision),
    FOREIGN KEY (task_id) REFERENCES ifj_task(id) ON DELETE RESTRICT
) WITHOUT ROWID;
INSERT INTO ifj_task_definition_v23
    (id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,timeout,enabled,description,create_time)
SELECT id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,timeout,enabled,description,create_time FROM ifj_task_definition;
DROP TABLE ifj_task_definition;
ALTER TABLE ifj_task_definition_v23 RENAME TO ifj_task_definition;
CREATE INDEX idx_ifj_task_definition_task_time ON ifj_task_definition(task_id,create_time,id);

DROP TABLE ifj_concurrency_policy;
PRAGMA legacy_alter_table = OFF;

UPDATE ifj_schema_capability
SET capability_version=23, update_time=strftime('%Y-%m-%d %H:%M:%f','now')
WHERE capability_key='platform-schema';
INSERT INTO ifj_schema_capability
    (capability_key,capability_version,enabled,create_time,update_time)
VALUES ('composite-task-concurrency',1,1,
        strftime('%Y-%m-%d %H:%M:%f','now'),strftime('%Y-%m-%d %H:%M:%f','now'));
