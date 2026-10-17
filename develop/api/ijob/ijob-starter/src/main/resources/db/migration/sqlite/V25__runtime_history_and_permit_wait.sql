ALTER TABLE ifj_instance ADD COLUMN wait_reason TEXT CHECK (wait_reason IS NULL OR length(wait_reason) <= 32);
ALTER TABLE ifj_instance ADD COLUMN permit_wait_deadline TEXT;

CREATE INDEX idx_ifj_trigger_history_v25
    ON ifj_trigger (target_type,target_id,trigger_time,id);
CREATE INDEX idx_ifj_instance_root_history_v25
    ON ifj_instance (parent_instance_id,create_time,id);

UPDATE ifj_schema_capability
SET capability_version=25, update_time=strftime('%Y-%m-%d %H:%M:%f','now')
WHERE capability_key='platform-schema';
INSERT INTO ifj_schema_capability
    (capability_key,capability_version,enabled,create_time,update_time)
VALUES ('runtime-history-v2',1,1,
        strftime('%Y-%m-%d %H:%M:%f','now'),strftime('%Y-%m-%d %H:%M:%f','now'));
