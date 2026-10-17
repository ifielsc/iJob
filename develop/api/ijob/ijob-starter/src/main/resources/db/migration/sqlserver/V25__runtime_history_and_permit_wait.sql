ALTER TABLE dbo.ifj_instance ADD
    wait_reason varchar(32) COLLATE Latin1_General_100_BIN2_UTF8 NULL,
    permit_wait_deadline datetime2(3) NULL;
GO

CREATE INDEX idx_ifj_trigger_history_v25
    ON dbo.ifj_trigger (target_type,target_id,trigger_time,id);
CREATE INDEX idx_ifj_instance_root_history_v25
    ON dbo.ifj_instance (parent_instance_id,create_time,id);

UPDATE dbo.ifj_schema_capability SET capability_version=25, update_time=SYSUTCDATETIME()
WHERE capability_key='platform-schema';
INSERT INTO dbo.ifj_schema_capability
    (capability_key,capability_version,enabled,create_time,update_time)
VALUES ('runtime-history-v2',1,1,SYSUTCDATETIME(),SYSUTCDATETIME());
