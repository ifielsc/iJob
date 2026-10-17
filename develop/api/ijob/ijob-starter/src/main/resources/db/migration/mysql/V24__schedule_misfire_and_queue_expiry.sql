ALTER TABLE ifj_schedule
    ADD COLUMN misfire_policy VARCHAR(32) NULL,
    ADD COLUMN misfire_grace_sec INT NULL,
    ADD COLUMN max_catchup_count INT NULL,
    ADD COLUMN max_queue_delay_sec INT NULL,
    ADD CONSTRAINT chk_ifj_schedule_misfire_policy CHECK (misfire_policy IS NULL OR misfire_policy IN ('SKIP','FIRE_ONCE','CATCH_UP_BOUNDED')),
    ADD CONSTRAINT chk_ifj_schedule_misfire_grace CHECK (misfire_grace_sec IS NULL OR misfire_grace_sec >= 0),
    ADD CONSTRAINT chk_ifj_schedule_catchup_count CHECK (max_catchup_count IS NULL OR max_catchup_count BETWEEN 1 AND 1000),
    ADD CONSTRAINT chk_ifj_schedule_queue_delay CHECK (max_queue_delay_sec IS NULL OR max_queue_delay_sec BETWEEN 1 AND 31536000);

ALTER TABLE ifj_trigger_work_item ADD COLUMN expires_at DATETIME(3) NULL AFTER available_at;

CREATE TABLE ifj_schedule_misfire_audit (
    id BIGINT NOT NULL,
    schedule_id BIGINT NULL,
    fire_claim_token VARCHAR(36) NOT NULL,
    policy VARCHAR(32) NOT NULL,
    observed_at DATETIME(3) NOT NULL,
    first_due_time DATETIME(3) NOT NULL,
    last_due_time DATETIME(3) NULL,
    due_count INT NOT NULL,
    count_capped BOOLEAN NOT NULL,
    created_count INT NOT NULL,
    skipped_count INT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_schedule_misfire_audit PRIMARY KEY (id),
    CONSTRAINT uk_ifj_schedule_misfire_claim UNIQUE (fire_claim_token),
    CONSTRAINT fk_ifj_schedule_misfire_schedule FOREIGN KEY (schedule_id) REFERENCES ifj_schedule(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE INDEX idx_ifj_schedule_due_v24 ON ifj_schedule (enabled, next_fire_time, id);
CREATE INDEX idx_ifj_work_dispatch_v24 ON ifj_trigger_work_item (status, available_at, trigger_id);
CREATE INDEX idx_ifj_work_lease_v24 ON ifj_trigger_work_item (status, lease_deadline, trigger_id);
CREATE INDEX idx_ifj_work_expiry_v24 ON ifj_trigger_work_item (status, expires_at, trigger_id);
CREATE INDEX idx_ifj_event_root_status_v24 ON ifj_event_wait (status, root_instance_id, id);
CREATE INDEX idx_ifj_misfire_schedule_v24 ON ifj_schedule_misfire_audit (schedule_id, observed_at, id);

UPDATE ifj_schema_capability SET capability_version=24, update_time=UTC_TIMESTAMP(3)
WHERE capability_key='platform-schema';
INSERT INTO ifj_schema_capability
    (capability_key, capability_version, enabled, create_time, update_time)
VALUES ('schedule-misfire-policy', 1, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
