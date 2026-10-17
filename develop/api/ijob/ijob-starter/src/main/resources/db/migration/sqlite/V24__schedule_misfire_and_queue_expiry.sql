ALTER TABLE ifj_schedule ADD COLUMN misfire_policy TEXT CHECK (misfire_policy IS NULL OR misfire_policy IN ('SKIP','FIRE_ONCE','CATCH_UP_BOUNDED'));
ALTER TABLE ifj_schedule ADD COLUMN misfire_grace_sec INTEGER CHECK (misfire_grace_sec IS NULL OR misfire_grace_sec >= 0);
ALTER TABLE ifj_schedule ADD COLUMN max_catchup_count INTEGER CHECK (max_catchup_count IS NULL OR max_catchup_count BETWEEN 1 AND 1000);
ALTER TABLE ifj_schedule ADD COLUMN max_queue_delay_sec INTEGER CHECK (max_queue_delay_sec IS NULL OR max_queue_delay_sec BETWEEN 1 AND 31536000);
ALTER TABLE ifj_trigger_work_item ADD COLUMN expires_at TEXT;

CREATE TABLE ifj_schedule_misfire_audit (
    id INTEGER NOT NULL PRIMARY KEY,
    schedule_id INTEGER,
    fire_claim_token TEXT NOT NULL UNIQUE CHECK (length(fire_claim_token) <= 36),
    policy TEXT NOT NULL CHECK (policy IN ('SKIP','FIRE_ONCE','CATCH_UP_BOUNDED')),
    observed_at TEXT NOT NULL,
    first_due_time TEXT NOT NULL,
    last_due_time TEXT,
    due_count INTEGER NOT NULL CHECK (due_count >= 0),
    count_capped INTEGER NOT NULL CHECK (count_capped IN (0,1)),
    created_count INTEGER NOT NULL CHECK (created_count >= 0),
    skipped_count INTEGER NOT NULL CHECK (skipped_count >= 0),
    create_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now')),
    FOREIGN KEY (schedule_id) REFERENCES ifj_schedule(id) ON DELETE SET NULL
) WITHOUT ROWID;
CREATE INDEX idx_ifj_schedule_due_v24 ON ifj_schedule (enabled,next_fire_time,id);
CREATE INDEX idx_ifj_work_dispatch_v24 ON ifj_trigger_work_item (status,available_at,trigger_id);
CREATE INDEX idx_ifj_work_lease_v24 ON ifj_trigger_work_item (status,lease_deadline,trigger_id);
CREATE INDEX idx_ifj_work_expiry_v24 ON ifj_trigger_work_item (status,expires_at,trigger_id);
CREATE INDEX idx_ifj_event_root_status_v24 ON ifj_event_wait (status,root_instance_id,id);
CREATE INDEX idx_ifj_misfire_schedule_v24 ON ifj_schedule_misfire_audit (schedule_id,observed_at,id);

UPDATE ifj_schema_capability
SET capability_version=24, update_time=strftime('%Y-%m-%d %H:%M:%f','now')
WHERE capability_key='platform-schema';
INSERT INTO ifj_schema_capability
    (capability_key,capability_version,enabled,create_time,update_time)
VALUES ('schedule-misfire-policy',1,1,
        strftime('%Y-%m-%d %H:%M:%f','now'),strftime('%Y-%m-%d %H:%M:%f','now'));
