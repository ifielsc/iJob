CREATE TABLE ifj_app_run (
    run_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    application_version VARCHAR(64) NULL,
    host_name VARCHAR(255) NULL,
    process_id BIGINT NULL,
    started_at DATETIME(3) NOT NULL,
    heartbeat_at DATETIME(3) NOT NULL,
    stopped_at DATETIME(3) NULL,
    CONSTRAINT pk_ifj_app_run PRIMARY KEY (run_id),
    CONSTRAINT chk_ifj_app_run_status CHECK (status IN (
        'STARTING', 'RUNNING', 'DRAINING', 'STOPPED', 'CRASHED')),
    INDEX idx_ifj_app_run_status_heartbeat (status, heartbeat_at, run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE ifj_trigger
    ADD INDEX idx_ifj_trigger_status_update
        (status, update_time, id);

ALTER TABLE ifj_instance
    ADD INDEX idx_ifj_instance_status_update
        (status, update_time, id);
