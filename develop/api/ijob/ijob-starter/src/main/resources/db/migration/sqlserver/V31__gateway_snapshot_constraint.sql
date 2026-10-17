ALTER TABLE dbo.ifj_job_definition_node
    DROP CONSTRAINT chk_ifj_job_definition_node_ref_snapshot;
ALTER TABLE dbo.ifj_job_definition_node
    ADD CONSTRAINT chk_ifj_job_definition_node_ref_snapshot CHECK (
        (node_kind = 'GATEWAY' AND ref_definition_id IS NULL
            AND ref_task_definition_id IS NULL)
        OR
        (node_kind = 'EXECUTION' AND (
            (node_type = 'JOB' AND ref_definition_id IS NOT NULL
                AND ref_task_definition_id IS NULL)
            OR
            (node_type = 'TASK' AND ref_definition_id IS NULL
                AND ref_task_definition_id IS NOT NULL)))
    );
