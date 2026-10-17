CREATE TABLE ifj_etl_checkpoint (
    task_id BIGINT NOT NULL,
    checkpoint_key VARCHAR(128) NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    watermark_type VARCHAR(32) NOT NULL,
    watermark_value LONGTEXT NULL,
    tie_breaker_value LONGTEXT NULL,
    last_instance_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_etl_checkpoint
        PRIMARY KEY (task_id, checkpoint_key),
    CONSTRAINT ck_ifj_etl_checkpoint_task CHECK (task_id > 0),
    CONSTRAINT ck_ifj_etl_checkpoint_version CHECK (version >= 0),
    INDEX idx_ifj_etl_checkpoint_instance (last_instance_id)
);
