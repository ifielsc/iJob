CREATE TABLE ifj_notify_rate_limit (
    target_key VARCHAR(64) NOT NULL PRIMARY KEY,
    next_allowed_at BIGINT NOT NULL
);
ALTER TABLE ifj_notification_outbox ADD content_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ifj_notification_outbox ADD http_status INTEGER NULL;
UPDATE ifj_schema_capability SET capability_version=38,update_time=SYSUTCDATETIME() WHERE capability_key='platform-schema';
