ALTER TABLE ifj_app_run
    ADD COLUMN instance_name VARCHAR(128) NULL AFTER run_id,
    ADD COLUMN runtime_mode VARCHAR(16) NOT NULL DEFAULT 'STANDALONE' AFTER instance_name,
    ADD COLUMN roles VARCHAR(64) NOT NULL DEFAULT 'API,SCHEDULER,WORKER' AFTER runtime_mode,
    ADD CONSTRAINT chk_ifj_app_run_runtime_mode CHECK (
        runtime_mode IN ('DEVELOPMENT', 'STANDALONE')),
    ADD INDEX idx_ifj_app_run_instance_heartbeat
        (instance_name, status, heartbeat_at, run_id);

CREATE TABLE ifj_id_segment (
    segment_key VARCHAR(64) NOT NULL,
    next_value BIGINT NOT NULL,
    segment_size INT NOT NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_id_segment PRIMARY KEY (segment_key),
    CONSTRAINT chk_ifj_id_segment_next_value CHECK (next_value > 0),
    CONSTRAINT chk_ifj_id_segment_size CHECK (segment_size BETWEEN 1 AND 100000)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
