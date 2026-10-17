-- Deploy only after stopping all pre-V26 nodes; protocol version 6 is required.
CREATE TABLE ifj_runtime_guard (
    guard_key VARCHAR(64) NOT NULL,
    revision NUMBER(19) NOT NULL,
    CONSTRAINT pk_ifj_runtime_guard PRIMARY KEY (guard_key)
);
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('concurrency-policy', 0);
INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('runtime-lifecycle', 0);
ALTER TABLE ifj_schedule ADD (revision NUMBER(19) DEFAULT 0 NOT NULL);
UPDATE ifj_schema_capability SET capability_version = 26, update_time = SYS_EXTRACT_UTC(SYSTIMESTAMP)
WHERE capability_key = 'platform-schema';
