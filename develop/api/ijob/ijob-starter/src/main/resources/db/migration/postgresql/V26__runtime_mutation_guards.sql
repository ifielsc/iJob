-- Deploy only after stopping all pre-V26 nodes; protocol version 6 is required.
CREATE TABLE ifj_runtime_guard (
    guard_key VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL,
    CONSTRAINT pk_ifj_runtime_guard PRIMARY KEY (guard_key)
);
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('concurrency-policy', 0);
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('runtime-lifecycle', 0);
ALTER TABLE ifj_schedule ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
UPDATE ifj_schema_capability SET capability_version = 26, update_time = CURRENT_TIMESTAMP
WHERE capability_key = 'platform-schema';
