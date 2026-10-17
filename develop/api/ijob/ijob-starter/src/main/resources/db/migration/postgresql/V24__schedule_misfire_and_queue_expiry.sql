ALTER TABLE ifj_schedule
    ADD COLUMN misfire_policy varchar(32),
    ADD COLUMN misfire_grace_sec int CHECK (misfire_grace_sec IS NULL OR misfire_grace_sec >= 0),
    ADD COLUMN max_catchup_count int CHECK (max_catchup_count IS NULL OR max_catchup_count BETWEEN 1 AND 1000),
    ADD COLUMN max_queue_delay_sec int CHECK (max_queue_delay_sec IS NULL OR max_queue_delay_sec BETWEEN 1 AND 31536000),
    ADD CONSTRAINT chk_ifj_schedule_misfire_policy CHECK (misfire_policy IS NULL OR misfire_policy IN ('SKIP','FIRE_ONCE','CATCH_UP_BOUNDED'));
ALTER TABLE ifj_trigger_work_item ADD COLUMN expires_at timestamp(3) without time zone;

CREATE TABLE ifj_schedule_misfire_audit (
    id bigint PRIMARY KEY,
    schedule_id bigint REFERENCES ifj_schedule(id) ON DELETE SET NULL,
    fire_claim_token varchar(36) NOT NULL UNIQUE,
    policy varchar(32) NOT NULL CHECK (policy IN ('SKIP','FIRE_ONCE','CATCH_UP_BOUNDED')),
    observed_at timestamp(3) without time zone NOT NULL,
    first_due_time timestamp(3) without time zone NOT NULL,
    last_due_time timestamp(3) without time zone,
    due_count int NOT NULL CHECK (due_count >= 0),
    count_capped boolean NOT NULL,
    created_count int NOT NULL CHECK (created_count >= 0),
    skipped_count int NOT NULL CHECK (skipped_count >= 0),
    create_time timestamp(3) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE INDEX idx_ifj_schedule_due_v24 ON ifj_schedule (enabled,next_fire_time,id);
CREATE INDEX idx_ifj_work_dispatch_v24 ON ifj_trigger_work_item (status,available_at,trigger_id);
CREATE INDEX idx_ifj_work_lease_v24 ON ifj_trigger_work_item (status,lease_deadline,trigger_id);
CREATE INDEX idx_ifj_work_expiry_v24 ON ifj_trigger_work_item (status,expires_at,trigger_id);
CREATE INDEX idx_ifj_event_root_status_v24 ON ifj_event_wait (status,root_instance_id,id);
CREATE INDEX idx_ifj_misfire_schedule_v24 ON ifj_schedule_misfire_audit (schedule_id,observed_at,id);

UPDATE ifj_schema_capability SET capability_version=24, update_time=CURRENT_TIMESTAMP(3)
WHERE capability_key='platform-schema';
INSERT INTO ifj_schema_capability
    (capability_key,capability_version,enabled,create_time,update_time)
VALUES ('schedule-misfire-policy',1,TRUE,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));
