CREATE TABLE ifj_plugin_concurrency_policy (
    plugin_type varchar(32) NOT NULL PRIMARY KEY,
    max_concurrency int NOT NULL CHECK (max_concurrency BETWEEN 1 AND 10000),
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0),
    create_time timestamp(3) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time timestamp(3) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE TABLE ifj_concurrency_resource (
    id bigint NOT NULL PRIMARY KEY,
    code varchar(128) NOT NULL UNIQUE,
    name varchar(128) NOT NULL,
    max_concurrency int NOT NULL CHECK (max_concurrency BETWEEN 1 AND 10000),
    enabled boolean NOT NULL DEFAULT TRUE,
    description varchar(500),
    create_time timestamp(3) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time timestamp(3) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE TABLE ifj_task_concurrency_policy (
    task_id bigint NOT NULL PRIMARY KEY REFERENCES ifj_task(id) ON DELETE CASCADE,
    task_max_concurrency int CHECK (task_max_concurrency IS NULL OR task_max_concurrency BETWEEN 1 AND 10000),
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0),
    create_time timestamp(3) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time timestamp(3) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE TABLE ifj_task_concurrency_resource (
    task_id bigint NOT NULL REFERENCES ifj_task_concurrency_policy(task_id) ON DELETE CASCADE,
    resource_id bigint NOT NULL REFERENCES ifj_concurrency_resource(id) ON DELETE RESTRICT,
    create_time timestamp(3) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (task_id, resource_id)
);

INSERT INTO ifj_concurrency_resource
    (id, code, name, max_concurrency, enabled, description, create_time, update_time)
SELECT MIN(id), CONCAT('legacy.', MIN(id)), CONCAT('Legacy resource ', MIN(id)),
       CASE WHEN lock_key IS NULL OR BTRIM(lock_key) = '' THEN 0
            WHEN MIN(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
                   = MAX(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
            THEN MIN(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
            ELSE 0 END,
       TRUE, CONCAT('Migrated from legacy lock key: ', lock_key), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM ifj_task
WHERE concurrency_type IN ('RESOURCE','RESOURCE_LIMIT')
GROUP BY lock_key;
INSERT INTO ifj_task_concurrency_policy
    (task_id, task_max_concurrency, version, create_time, update_time)
SELECT id, CASE WHEN concurrency_type='TASK_SINGLE' THEN 1 ELSE NULL END,
       1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM ifj_task WHERE concurrency_type IN ('TASK_SINGLE','RESOURCE','RESOURCE_LIMIT');
INSERT INTO ifj_task_concurrency_resource (task_id, resource_id, create_time)
SELECT task.id, resource.resource_id, CURRENT_TIMESTAMP(3)
FROM ifj_task task
JOIN (SELECT lock_key, MIN(id) resource_id FROM ifj_task
      WHERE concurrency_type IN ('RESOURCE','RESOURCE_LIMIT') GROUP BY lock_key) resource
  ON resource.lock_key=task.lock_key
WHERE task.concurrency_type IN ('RESOURCE','RESOURCE_LIMIT');

ALTER TABLE ifj_concurrency_permit DROP CONSTRAINT uk_ifj_concurrency_permit_owner;
DROP TABLE ifj_concurrency_policy;
ALTER TABLE ifj_job DROP COLUMN concurrency_type, DROP COLUMN lock_key, DROP COLUMN max_concurrency;
ALTER TABLE ifj_task_definition DROP COLUMN concurrency_type, DROP COLUMN lock_key, DROP COLUMN max_concurrency;
ALTER TABLE ifj_task DROP COLUMN concurrency_type, DROP COLUMN lock_key, DROP COLUMN max_concurrency;

UPDATE ifj_schema_capability SET capability_version=23, update_time=CURRENT_TIMESTAMP(3)
WHERE capability_key='platform-schema';
INSERT INTO ifj_schema_capability
    (capability_key, capability_version, enabled, create_time, update_time)
VALUES ('composite-task-concurrency', 1, TRUE, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));
