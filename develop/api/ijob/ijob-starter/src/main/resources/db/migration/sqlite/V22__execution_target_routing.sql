ALTER TABLE ifj_app_run ADD COLUMN worker_task_ids TEXT NOT NULL DEFAULT '';
ALTER TABLE ifj_app_run ADD COLUMN worker_job_ids TEXT NOT NULL DEFAULT '';

UPDATE ifj_schema_capability
SET capability_version = 22,
    update_time = strftime('%Y-%m-%d %H:%M:%f','now')
WHERE capability_key = 'platform-schema';

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'execution-target-routing', 1, 1,
    strftime('%Y-%m-%d %H:%M:%f','now'),
    strftime('%Y-%m-%d %H:%M:%f','now')
);
