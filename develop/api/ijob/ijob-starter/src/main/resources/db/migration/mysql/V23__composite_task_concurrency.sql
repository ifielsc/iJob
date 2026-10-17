CREATE TABLE ifj_plugin_concurrency_policy (
    plugin_type VARCHAR(32) NOT NULL,
    max_concurrency INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_plugin_concurrency_policy PRIMARY KEY (plugin_type),
    CONSTRAINT chk_ifj_plugin_concurrency_max CHECK (max_concurrency BETWEEN 1 AND 10000),
    CONSTRAINT chk_ifj_plugin_concurrency_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE ifj_concurrency_resource (
    id BIGINT NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    max_concurrency INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_concurrency_resource PRIMARY KEY (id),
    CONSTRAINT uk_ifj_concurrency_resource_code UNIQUE (code),
    CONSTRAINT chk_ifj_concurrency_resource_max CHECK (max_concurrency BETWEEN 1 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE ifj_task_concurrency_policy (
    task_id BIGINT NOT NULL,
    task_max_concurrency INT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_task_concurrency_policy PRIMARY KEY (task_id),
    CONSTRAINT fk_ifj_task_concurrency_policy_task FOREIGN KEY (task_id) REFERENCES ifj_task(id) ON DELETE CASCADE,
    CONSTRAINT chk_ifj_task_concurrency_max CHECK (task_max_concurrency IS NULL OR task_max_concurrency BETWEEN 1 AND 10000),
    CONSTRAINT chk_ifj_task_concurrency_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE ifj_task_concurrency_resource (
    task_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_task_concurrency_resource PRIMARY KEY (task_id, resource_id),
    CONSTRAINT fk_ifj_task_concurrency_resource_task FOREIGN KEY (task_id) REFERENCES ifj_task_concurrency_policy(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_task_concurrency_resource_resource FOREIGN KEY (resource_id) REFERENCES ifj_concurrency_resource(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO ifj_concurrency_resource
    (id, code, name, max_concurrency, enabled, description, create_time, update_time)
SELECT MIN(id), CONCAT('legacy.', MIN(id)), CONCAT('Legacy resource ', MIN(id)),
       CASE WHEN lock_key IS NULL OR TRIM(lock_key) = '' THEN 0
            WHEN MIN(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
                   = MAX(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
            THEN MIN(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
            ELSE 0 END,
       TRUE, CONCAT('Migrated from legacy lock key: ', lock_key), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM ifj_task
WHERE concurrency_type IN ('RESOURCE','RESOURCE_LIMIT')
GROUP BY lock_key;

INSERT INTO ifj_task_concurrency_policy
    (task_id, task_max_concurrency, version, create_time, update_time)
SELECT id, CASE WHEN concurrency_type='TASK_SINGLE' THEN 1 ELSE NULL END,
       1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM ifj_task
WHERE concurrency_type IN ('TASK_SINGLE','RESOURCE','RESOURCE_LIMIT');

INSERT INTO ifj_task_concurrency_resource (task_id, resource_id, create_time)
SELECT task.id, resource.resource_id, UTC_TIMESTAMP(3)
FROM ifj_task task
JOIN (
    SELECT lock_key, MIN(id) AS resource_id
    FROM ifj_task
    WHERE concurrency_type IN ('RESOURCE','RESOURCE_LIMIT')
    GROUP BY lock_key
) resource ON resource.lock_key = task.lock_key
WHERE task.concurrency_type IN ('RESOURCE','RESOURCE_LIMIT');

ALTER TABLE ifj_concurrency_permit DROP INDEX uk_ifj_concurrency_permit_owner;
DROP TABLE ifj_concurrency_policy;
ALTER TABLE ifj_job DROP COLUMN concurrency_type, DROP COLUMN lock_key, DROP COLUMN max_concurrency;
ALTER TABLE ifj_task_definition DROP COLUMN concurrency_type, DROP COLUMN lock_key, DROP COLUMN max_concurrency;
ALTER TABLE ifj_task DROP COLUMN concurrency_type, DROP COLUMN lock_key, DROP COLUMN max_concurrency;

UPDATE ifj_schema_capability
SET capability_version=23, update_time=UTC_TIMESTAMP(3)
WHERE capability_key='platform-schema';
INSERT INTO ifj_schema_capability
    (capability_key, capability_version, enabled, create_time, update_time)
VALUES ('composite-task-concurrency', 1, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
