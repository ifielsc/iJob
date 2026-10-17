CREATE TABLE dbo.ifj_job_definition_group (
    definition_id bigint NOT NULL,
    group_code varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    create_time datetime2(3) NOT NULL CONSTRAINT df_ifj_job_def_group_create DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_job_definition_group PRIMARY KEY (definition_id, group_code),
    CONSTRAINT fk_ifj_job_def_group_definition FOREIGN KEY (definition_id)
        REFERENCES dbo.ifj_job_definition (id) ON DELETE CASCADE
);
CREATE INDEX idx_ifj_job_def_group_code
    ON dbo.ifj_job_definition_group (group_code, definition_id);

CREATE TABLE dbo.ifj_task_definition_group (
    definition_id bigint NOT NULL,
    group_code varchar(64) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    create_time datetime2(3) NOT NULL CONSTRAINT df_ifj_task_def_group_create DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_task_definition_group PRIMARY KEY (definition_id, group_code),
    CONSTRAINT fk_ifj_task_def_group_definition FOREIGN KEY (definition_id)
        REFERENCES dbo.ifj_task_definition (id) ON DELETE CASCADE
);
CREATE INDEX idx_ifj_task_def_group_code
    ON dbo.ifj_task_definition_group (group_code, definition_id);

ALTER TABLE dbo.ifj_app_run ADD
    worker_groups varchar(1100) NOT NULL CONSTRAINT df_ifj_app_run_worker_groups DEFAULT '',
    runtime_protocol_version int NOT NULL CONSTRAINT df_ifj_app_run_protocol DEFAULT 1;

UPDATE dbo.ifj_schema_capability
SET capability_version = 20, update_time = SYSUTCDATETIME()
WHERE capability_key = 'platform-schema';

INSERT INTO dbo.ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'execution-group-routing', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME()
);
