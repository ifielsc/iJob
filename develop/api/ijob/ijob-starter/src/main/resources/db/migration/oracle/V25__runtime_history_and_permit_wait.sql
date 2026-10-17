ALTER TABLE ifj_instance ADD (
    wait_reason VARCHAR2(32 CHAR),
    permit_wait_deadline TIMESTAMP(3)
);

CREATE INDEX idx_ifj_trigger_hist_v25
    ON ifj_trigger (target_type,target_id,trigger_time,id);
CREATE INDEX idx_ifj_instance_root_hist_v25
    ON ifj_instance (parent_instance_id,create_time,id);

UPDATE ifj_schema_capability
SET capability_version=25,
    update_time=CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
WHERE capability_key='platform-schema';
INSERT INTO ifj_schema_capability
    (capability_key,capability_version,enabled,create_time,update_time)
VALUES ('runtime-history-v2',1,1,
        CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)),
        CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)));
