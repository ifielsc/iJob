CREATE TABLE ifj_notify_rate_limit (
    target_key VARCHAR2(64) NOT NULL PRIMARY KEY,
    next_allowed_at NUMBER(19) NOT NULL
);
ALTER TABLE ifj_notification_outbox ADD content_version NUMBER(10) DEFAULT 0 NOT NULL;
ALTER TABLE ifj_notification_outbox ADD http_status NUMBER(10) NULL;
UPDATE ifj_schema_capability SET capability_version=38,update_time=CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) WHERE capability_key='platform-schema';
