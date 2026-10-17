ALTER TABLE dbo.ifj_schedule ADD
    misfire_policy varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NULL,
    misfire_grace_sec int NULL,
    max_catchup_count int NULL,
    max_queue_delay_sec int NULL;
GO
ALTER TABLE dbo.ifj_schedule ADD
    CONSTRAINT chk_ifj_schedule_misfire_v24 CHECK (misfire_policy IS NULL OR misfire_policy IN ('SKIP','FIRE_ONCE','CATCH_UP_BOUNDED')),
    CONSTRAINT chk_ifj_schedule_grace_v24 CHECK (misfire_grace_sec IS NULL OR misfire_grace_sec >= 0),
    CONSTRAINT chk_ifj_schedule_catchup_v24 CHECK (max_catchup_count IS NULL OR max_catchup_count BETWEEN 1 AND 1000),
    CONSTRAINT chk_ifj_schedule_delay_v24 CHECK (max_queue_delay_sec IS NULL OR max_queue_delay_sec BETWEEN 1 AND 31536000);
ALTER TABLE dbo.ifj_trigger_work_item ADD expires_at datetime2(3) NULL;
GO

CREATE TABLE dbo.ifj_schedule_misfire_audit (
    id bigint NOT NULL,
    schedule_id bigint NULL,
    fire_claim_token varchar(36) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    policy varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NOT NULL,
    observed_at datetime2(3) NOT NULL,
    first_due_time datetime2(3) NOT NULL,
    last_due_time datetime2(3) NULL,
    due_count int NOT NULL,
    count_capped bit NOT NULL,
    created_count int NOT NULL,
    skipped_count int NOT NULL,
    create_time datetime2(3) NOT NULL CONSTRAINT df_ifj_misfire_audit_ct DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_ifj_schedule_misfire_audit PRIMARY KEY (id),
    CONSTRAINT uk_ifj_schedule_misfire_claim UNIQUE (fire_claim_token),
    CONSTRAINT fk_ifj_schedule_misfire_sched FOREIGN KEY (schedule_id) REFERENCES dbo.ifj_schedule(id) ON DELETE SET NULL
);
GO
CREATE INDEX idx_ifj_schedule_due_v24 ON dbo.ifj_schedule (enabled,next_fire_time,id);
CREATE INDEX idx_ifj_work_dispatch_v24 ON dbo.ifj_trigger_work_item (status,available_at,trigger_id);
CREATE INDEX idx_ifj_work_lease_v24 ON dbo.ifj_trigger_work_item (status,lease_deadline,trigger_id);
CREATE INDEX idx_ifj_work_expiry_v24 ON dbo.ifj_trigger_work_item (status,expires_at,trigger_id);
CREATE INDEX idx_ifj_event_root_status_v24 ON dbo.ifj_event_wait (status,root_instance_id,id);
CREATE INDEX idx_ifj_misfire_schedule_v24 ON dbo.ifj_schedule_misfire_audit (schedule_id,observed_at,id);

UPDATE dbo.ifj_schema_capability SET capability_version=24, update_time=SYSUTCDATETIME()
WHERE capability_key='platform-schema';
INSERT INTO dbo.ifj_schema_capability
    (capability_key,capability_version,enabled,create_time,update_time)
VALUES ('schedule-misfire-policy',1,1,SYSUTCDATETIME(),SYSUTCDATETIME());
