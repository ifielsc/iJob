ALTER TABLE ifj_job_definition_node
    DROP CONSTRAINT chk_ifj_job_definitio_0419c6da;
ALTER TABLE ifj_job_definition_node
    ADD CONSTRAINT chk_ifj_job_definitio_0419c6da CHECK (
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
