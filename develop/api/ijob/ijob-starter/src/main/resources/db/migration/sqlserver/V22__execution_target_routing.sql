ALTER TABLE dbo.ifj_app_run ADD
    worker_task_ids varchar(2100) NOT NULL
        CONSTRAINT df_ifj_app_run_worker_task_ids DEFAULT '',
    worker_job_ids varchar(2100) NOT NULL
        CONSTRAINT df_ifj_app_run_worker_job_ids DEFAULT '';

UPDATE dbo.ifj_schema_capability
SET capability_version = 22, update_time = SYSUTCDATETIME()
WHERE capability_key = 'platform-schema';

INSERT INTO dbo.ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'execution-target-routing', 1, 1, SYSUTCDATETIME(), SYSUTCDATETIME()
);
