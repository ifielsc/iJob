ALTER TABLE ifj_schedule ADD (
    misfire_policy VARCHAR2(32 CHAR),
    misfire_grace_sec NUMBER(10),
    max_catchup_count NUMBER(10),
    max_queue_delay_sec NUMBER(10),
    CONSTRAINT ck_ifj_sched_misfire_v24 CHECK (misfire_policy IS NULL OR misfire_policy IN ('SKIP','FIRE_ONCE','CATCH_UP_BOUNDED')),
    CONSTRAINT ck_ifj_sched_grace_v24 CHECK (misfire_grace_sec IS NULL OR misfire_grace_sec >= 0),
    CONSTRAINT ck_ifj_sched_catchup_v24 CHECK (max_catchup_count IS NULL OR max_catchup_count BETWEEN 1 AND 1000),
    CONSTRAINT ck_ifj_sched_delay_v24 CHECK (max_queue_delay_sec IS NULL OR max_queue_delay_sec BETWEEN 1 AND 31536000)
);
ALTER TABLE ifj_trigger_work_item ADD (expires_at TIMESTAMP(3));

CREATE TABLE ifj_schedule_misfire_audit (
    id NUMBER(19) NOT NULL,
    schedule_id NUMBER(19),
    fire_claim_token VARCHAR2(36 CHAR) NOT NULL,
    policy VARCHAR2(32 CHAR) NOT NULL,
    observed_at TIMESTAMP(3) NOT NULL,
    first_due_time TIMESTAMP(3) NOT NULL,
    last_due_time TIMESTAMP(3),
    due_count NUMBER(10) NOT NULL,
    count_capped NUMBER(1) NOT NULL,
    created_count NUMBER(10) NOT NULL,
    skipped_count NUMBER(10) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_sched_misfire_audit PRIMARY KEY (id),
    CONSTRAINT uk_ifj_sched_misfire_claim UNIQUE (fire_claim_token),
    CONSTRAINT fk_ifj_sched_misfire_sched FOREIGN KEY (schedule_id) REFERENCES ifj_schedule(id) ON DELETE SET NULL,
    CONSTRAINT ck_ifj_sched_misfire_pol CHECK (policy IN ('SKIP','FIRE_ONCE','CATCH_UP_BOUNDED')),
    CONSTRAINT ck_ifj_sched_misfire_bool CHECK (count_capped IN (0,1))
);
CREATE INDEX idx_ifj_schedule_due_v24 ON ifj_schedule (enabled,next_fire_time,id);
CREATE INDEX idx_ifj_work_dispatch_v24 ON ifj_trigger_work_item (status,available_at,trigger_id);
CREATE INDEX idx_ifj_work_lease_v24 ON ifj_trigger_work_item (status,lease_deadline,trigger_id);
CREATE INDEX idx_ifj_work_expiry_v24 ON ifj_trigger_work_item (status,expires_at,trigger_id);
CREATE INDEX idx_ifj_event_root_v24 ON ifj_event_wait (status,root_instance_id,id);
CREATE INDEX idx_ifj_misfire_sched_v24 ON ifj_schedule_misfire_audit (schedule_id,observed_at,id);

UPDATE ifj_schema_capability
SET capability_version=24, update_time=CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
WHERE capability_key='platform-schema';
INSERT INTO ifj_schema_capability
    (capability_key,capability_version,enabled,create_time,update_time)
VALUES ('schedule-misfire-policy',1,1,
        CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)),
        CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)));
