CREATE TABLE dbo.ifj_plugin_concurrency_policy (
    plugin_type varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    max_concurrency int NOT NULL,
    version bigint NOT NULL CONSTRAINT df_ifj_plugin_concurrency_ver DEFAULT 1,
    create_time datetime2(3) NOT NULL CONSTRAINT df_ifj_plugin_concurrency_ct DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL CONSTRAINT df_ifj_plugin_concurrency_ut DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_plugin_concurrency_policy PRIMARY KEY (plugin_type),
    CONSTRAINT chk_ifj_plugin_concurrency_max CHECK (max_concurrency BETWEEN 1 AND 10000),
    CONSTRAINT chk_ifj_plugin_concurrency_ver CHECK (version > 0)
);
CREATE TABLE dbo.ifj_concurrency_resource (
    id bigint NOT NULL,
    code varchar(128) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    name nvarchar(128) COLLATE Latin1_General_100_CI_AI_SC NOT NULL,
    max_concurrency int NOT NULL,
    enabled bit NOT NULL CONSTRAINT df_ifj_concurrency_resource_en DEFAULT 1,
    description nvarchar(500) COLLATE Latin1_General_100_CI_AI_SC NULL,
    create_time datetime2(3) NOT NULL CONSTRAINT df_ifj_concurrency_resource_ct DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL CONSTRAINT df_ifj_concurrency_resource_ut DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_concurrency_resource PRIMARY KEY (id),
    CONSTRAINT uk_ifj_concurrency_resource_code UNIQUE (code),
    CONSTRAINT chk_ifj_concurrency_resource_max CHECK (max_concurrency BETWEEN 1 AND 10000)
);
CREATE TABLE dbo.ifj_task_concurrency_policy (
    task_id bigint NOT NULL,
    task_max_concurrency int NULL,
    version bigint NOT NULL CONSTRAINT df_ifj_task_concurrency_ver DEFAULT 1,
    create_time datetime2(3) NOT NULL CONSTRAINT df_ifj_task_concurrency_ct DEFAULT SYSUTCDATETIME(),
    update_time datetime2(3) NOT NULL CONSTRAINT df_ifj_task_concurrency_ut DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_task_concurrency_policy PRIMARY KEY (task_id),
    CONSTRAINT fk_ifj_task_concurrency_task FOREIGN KEY (task_id) REFERENCES dbo.ifj_task(id) ON DELETE CASCADE,
    CONSTRAINT chk_ifj_task_concurrency_max CHECK (task_max_concurrency IS NULL OR task_max_concurrency BETWEEN 1 AND 10000),
    CONSTRAINT chk_ifj_task_concurrency_ver CHECK (version > 0)
);
CREATE TABLE dbo.ifj_task_concurrency_resource (
    task_id bigint NOT NULL,
    resource_id bigint NOT NULL,
    create_time datetime2(3) NOT NULL CONSTRAINT df_ifj_task_concurrency_res_ct DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_task_concurrency_resource PRIMARY KEY (task_id,resource_id),
    CONSTRAINT fk_ifj_task_concurrency_res_task FOREIGN KEY (task_id) REFERENCES dbo.ifj_task_concurrency_policy(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_task_concurrency_res_ref FOREIGN KEY (resource_id) REFERENCES dbo.ifj_concurrency_resource(id)
);

INSERT INTO dbo.ifj_concurrency_resource
    (id, code, name, max_concurrency, enabled, description, create_time, update_time)
SELECT MIN(id), CONCAT('legacy.', MIN(id)), CONCAT('Legacy resource ', MIN(id)),
       CASE WHEN lock_key IS NULL OR LTRIM(RTRIM(lock_key)) = '' THEN 0
            WHEN MIN(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
                   = MAX(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
            THEN MIN(CASE WHEN concurrency_type='RESOURCE' THEN 1 ELSE max_concurrency END)
            ELSE 0 END,
       1, CONCAT('Migrated from legacy lock key: ', lock_key), SYSUTCDATETIME(), SYSUTCDATETIME()
FROM dbo.ifj_task
WHERE concurrency_type IN ('RESOURCE','RESOURCE_LIMIT')
GROUP BY lock_key;
INSERT INTO dbo.ifj_task_concurrency_policy
    (task_id, task_max_concurrency, version, create_time, update_time)
SELECT id, CASE WHEN concurrency_type='TASK_SINGLE' THEN 1 ELSE NULL END,
       1, SYSUTCDATETIME(), SYSUTCDATETIME()
FROM dbo.ifj_task WHERE concurrency_type IN ('TASK_SINGLE','RESOURCE','RESOURCE_LIMIT');
INSERT INTO dbo.ifj_task_concurrency_resource (task_id, resource_id, create_time)
SELECT task.id, resource.resource_id, SYSUTCDATETIME()
FROM dbo.ifj_task task
JOIN (SELECT lock_key, MIN(id) resource_id FROM dbo.ifj_task
      WHERE concurrency_type IN ('RESOURCE','RESOURCE_LIMIT') GROUP BY lock_key) resource
  ON resource.lock_key=task.lock_key
WHERE task.concurrency_type IN ('RESOURCE','RESOURCE_LIMIT');

DROP INDEX uk_ifj_concurrency_permit_owner ON dbo.ifj_concurrency_permit;
DROP TABLE dbo.ifj_concurrency_policy;

DECLARE @drop_defaults nvarchar(max) = N'';
SELECT @drop_defaults = @drop_defaults
    + N'ALTER TABLE ' + QUOTENAME(OBJECT_SCHEMA_NAME(dc.parent_object_id))
    + N'.' + QUOTENAME(OBJECT_NAME(dc.parent_object_id))
    + N' DROP CONSTRAINT ' + QUOTENAME(dc.name) + N';'
FROM sys.default_constraints dc
JOIN sys.columns column_info
  ON column_info.object_id = dc.parent_object_id
 AND column_info.column_id = dc.parent_column_id
WHERE dc.parent_object_id IN (
        OBJECT_ID(N'dbo.ifj_job'),
        OBJECT_ID(N'dbo.ifj_task'),
        OBJECT_ID(N'dbo.ifj_task_definition'))
  AND column_info.name IN (N'concurrency_type', N'lock_key', N'max_concurrency');
IF @drop_defaults <> N'' EXEC sys.sp_executesql @drop_defaults;

ALTER TABLE dbo.ifj_job DROP COLUMN concurrency_type, lock_key, max_concurrency;
ALTER TABLE dbo.ifj_task_definition DROP COLUMN concurrency_type, lock_key, max_concurrency;
ALTER TABLE dbo.ifj_task DROP COLUMN concurrency_type, lock_key, max_concurrency;

UPDATE dbo.ifj_schema_capability SET capability_version=23, update_time=SYSUTCDATETIME()
WHERE capability_key='platform-schema';
INSERT INTO dbo.ifj_schema_capability
    (capability_key, capability_version, enabled, create_time, update_time)
VALUES ('composite-task-concurrency', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME());
