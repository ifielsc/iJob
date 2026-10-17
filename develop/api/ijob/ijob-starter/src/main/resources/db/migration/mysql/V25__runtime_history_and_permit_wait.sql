ALTER TABLE ifj_instance
    ADD COLUMN wait_reason VARCHAR(32) NULL AFTER timeout_at,
    ADD COLUMN permit_wait_deadline DATETIME(3) NULL AFTER wait_reason;

CREATE INDEX idx_ifj_trigger_history_v25
    ON ifj_trigger (target_type, target_id, trigger_time, id);
CREATE INDEX idx_ifj_instance_root_history_v25
    ON ifj_instance (parent_instance_id, create_time, id);

UPDATE ifj_schema_capability SET capability_version=25, update_time=UTC_TIMESTAMP(3)
WHERE capability_key='platform-schema';
INSERT INTO ifj_schema_capability
    (capability_key, capability_version, enabled, create_time, update_time)
VALUES ('runtime-history-v2', 1, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
