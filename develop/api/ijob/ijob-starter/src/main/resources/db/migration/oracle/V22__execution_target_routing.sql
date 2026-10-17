ALTER TABLE ifj_app_run ADD (
    worker_task_ids VARCHAR2(2100 CHAR),
    worker_job_ids VARCHAR2(2100 CHAR)
);

UPDATE ifj_schema_capability
SET capability_version = 22,
    update_time = CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
WHERE capability_key = 'platform-schema';

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'execution-target-routing', 1, 1,
    CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)),
    CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
);
