ALTER TABLE ifj_trigger
    ADD CONSTRAINT chk_ifj_trigger_status
        CHECK (status IN (
            'WAITING', 'RUNNING', 'SUCCESS', 'FAILED',
            'STOPPED', 'TIMED_OUT', 'SKIPPED'
        ));

ALTER TABLE ifj_instance
    ADD CONSTRAINT chk_ifj_instance_status
        CHECK (status IN (
            'WAITING', 'RUNNING', 'PAUSED', 'SUCCESS', 'FAILED',
            'STOPPED', 'TIMED_OUT', 'SKIPPED'
        )),
    ADD CONSTRAINT chk_ifj_instance_progress
        CHECK (progress BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_ifj_instance_retry_count
        CHECK (retry_count >= 0);
