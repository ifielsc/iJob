ALTER TABLE ifj_app_run
    ADD COLUMN worker_task_ids VARCHAR(2100) NOT NULL DEFAULT '' AFTER worker_groups,
    ADD COLUMN worker_job_ids VARCHAR(2100) NOT NULL DEFAULT '' AFTER worker_task_ids;

UPDATE ifj_schema_capability
SET capability_version = 22, update_time = UTC_TIMESTAMP(3)
WHERE capability_key = 'platform-schema';

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'execution-target-routing', 1, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
);
