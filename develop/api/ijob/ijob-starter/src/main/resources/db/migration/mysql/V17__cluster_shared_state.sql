-- iJob MySQL 8 shared cluster state for cross-node authentication and compatibility.

ALTER TABLE ifj_app_run
    DROP CHECK chk_ifj_app_run_runtime_mode,
    ADD CONSTRAINT chk_ifj_app_run_runtime_mode CHECK (
        runtime_mode IN ('DEVELOPMENT', 'STANDALONE', 'CLUSTER'));

CREATE TABLE ifj_cluster_state (
    state_key VARCHAR(512) NOT NULL,
    state_value LONGTEXT NOT NULL,
    expires_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_cluster_state PRIMARY KEY (state_key),
    INDEX idx_ifj_cluster_state_expiry (expires_at, state_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
