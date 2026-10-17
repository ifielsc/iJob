CREATE TABLE ifj_notify_rate_limit (
    target_key VARCHAR(64) NOT NULL PRIMARY KEY,
    next_allowed_at BIGINT NOT NULL
) WITHOUT ROWID;
ALTER TABLE ifj_notification_outbox ADD COLUMN content_version INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE ifj_notification_outbox ADD COLUMN http_status INTEGER NULL;
UPDATE ifj_schema_capability SET capability_version=38,update_time=strftime('%Y-%m-%d %H:%M:%f','now') WHERE capability_key='platform-schema';
