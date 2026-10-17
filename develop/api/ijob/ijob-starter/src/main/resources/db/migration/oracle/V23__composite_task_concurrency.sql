CREATE TABLE ifj_plugin_concurrency_policy (
    plugin_type VARCHAR2(32 CHAR) NOT NULL,
    max_concurrency NUMBER(10) NOT NULL,
    version NUMBER(19) DEFAULT 1 NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_plugin_concurrency PRIMARY KEY (plugin_type),
    CONSTRAINT chk_ifj_plugin_concurrency_max CHECK (max_concurrency BETWEEN 1 AND 10000),
    CONSTRAINT chk_ifj_plugin_concurrency_ver CHECK (version > 0)
);
CREATE TABLE ifj_concurrency_resource (
    id NUMBER(19) NOT NULL,
    code VARCHAR2(128 CHAR) NOT NULL,
    name VARCHAR2(128 CHAR) NOT NULL,
    max_concurrency NUMBER(10) NOT NULL,
    enabled NUMBER(1) DEFAULT 1 NOT NULL,
    description VARCHAR2(500 CHAR),
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_concurrency_resource PRIMARY KEY (id),
    CONSTRAINT uk_ifj_concurrency_res_code UNIQUE (code),
    CONSTRAINT ck_ifj_concurrency_res_max CHECK (max_concurrency BETWEEN 1 AND 10000),
    CONSTRAINT ck_ifj_concurrency_res_bool CHECK (enabled IN (0,1))
);
CREATE TABLE ifj_task_concurrency_policy (
    task_id NUMBER(19) NOT NULL,
    task_max_concurrency NUMBER(10),
    version NUMBER(19) DEFAULT 1 NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    update_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_task_conc_policy PRIMARY KEY (task_id),
    CONSTRAINT fk_ifj_task_concurrency_task FOREIGN KEY (task_id) REFERENCES ifj_task(id) ON DELETE CASCADE,
    CONSTRAINT ck_ifj_task_conc_max CHECK (task_max_concurrency IS NULL OR task_max_concurrency BETWEEN 1 AND 10000),
    CONSTRAINT ck_ifj_task_conc_ver CHECK (version > 0)
);
CREATE TABLE ifj_task_concurrency_resource (
    task_id NUMBER(19) NOT NULL,
    resource_id NUMBER(19) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_task_conc_res PRIMARY KEY (task_id,resource_id),
    CONSTRAINT fk_ifj_task_conc_res_task FOREIGN KEY (task_id) REFERENCES ifj_task_concurrency_policy(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_task_conc_res_ref FOREIGN KEY (resource_id) REFERENCES ifj_concurrency_resource(id)
);

INSERT INTO ifj_concurrency_resource
    (id, code, name, max_concurrency, enabled, description, create_time, update_time)
SELECT MIN(id), 'legacy.' || TO_CHAR(MIN(id)), 'Legacy resource ' || TO_CHAR(MIN(id)),
       CASE WHEN lock_key IS NULL OR TRIM(lock_key) IS NULL THEN 0
            WHEN MIN(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
                   = MAX(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
            THEN MIN(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
            ELSE 0 END,
       1, 'Migrated from legacy lock key: ' || lock_key,
       CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)),
       CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
FROM ifj_task
WHERE concurrency_type IN ('RESOURCE','RESOURCE_LIMIT')
GROUP BY lock_key;
INSERT INTO ifj_task_concurrency_policy
    (task_id, task_max_concurrency, version, create_time, update_time)
SELECT id, CASE WHEN concurrency_type='TASK_SINGLE' THEN 1 ELSE NULL END, 1,
       CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)),
       CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
FROM ifj_task WHERE concurrency_type IN ('TASK_SINGLE','RESOURCE','RESOURCE_LIMIT');
INSERT INTO ifj_task_concurrency_resource (task_id, resource_id, create_time)
SELECT task_record.id, resource_record.resource_id,
       CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
FROM ifj_task task_record
JOIN (SELECT lock_key, MIN(id) resource_id FROM ifj_task
      WHERE concurrency_type IN ('RESOURCE','RESOURCE_LIMIT') GROUP BY lock_key) resource_record
  ON resource_record.lock_key=task_record.lock_key
WHERE task_record.concurrency_type IN ('RESOURCE','RESOURCE_LIMIT');

ALTER TABLE ifj_concurrency_permit DROP CONSTRAINT uk_ifj_concurrency_pe_9dbccce8;
DROP TABLE ifj_concurrency_policy CASCADE CONSTRAINTS;
ALTER TABLE ifj_job DROP (concurrency_type, lock_key, max_concurrency);
ALTER TABLE ifj_task_definition DROP (concurrency_type, lock_key, max_concurrency);
ALTER TABLE ifj_task DROP (concurrency_type, lock_key, max_concurrency);

UPDATE ifj_schema_capability
SET capability_version=23, update_time=CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
WHERE capability_key='platform-schema';
INSERT INTO ifj_schema_capability
    (capability_key, capability_version, enabled, create_time, update_time)
VALUES ('composite-task-concurrency', 1, 1,
        CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)),
        CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)));
