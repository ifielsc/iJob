-- V27 security persistence foundation. No account or password is seeded by SQL.
-- Stop older application nodes before upgrade. Authentication activation is a separate step.
CREATE TABLE ifj_auth_user (
    id BIGINT NOT NULL,
    login_id NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    username NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    display_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2,
    password_hash NVARCHAR(100) COLLATE Latin1_General_100_BIN2 NOT NULL,
    role_code NVARCHAR(16) COLLATE Latin1_General_100_BIN2 DEFAULT 'VIEWER' NOT NULL,
    enabled BIT DEFAULT 1 NOT NULL,
    must_change_password BIT DEFAULT 1 NOT NULL,
    security_version BIGINT DEFAULT 0 NOT NULL,
    revision BIGINT DEFAULT 0 NOT NULL,
    created_by NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    create_time DATETIME2(3) NOT NULL,
    update_time DATETIME2(3) NOT NULL,
    CONSTRAINT pk_ifj_auth_user PRIMARY KEY (id),
    CONSTRAINT uk_ifj_auth_login UNIQUE (login_id),
    CONSTRAINT uk_ifj_auth_username UNIQUE (username),
    CONSTRAINT ck_ifj_auth_role CHECK (role_code IN ('ADMIN','OPERATOR','VIEWER')),
    CONSTRAINT ck_ifj_auth_version CHECK (security_version >= 0 AND revision >= 0),
    CONSTRAINT ck_ifj_auth_enabled CHECK (enabled IN (0,1)),
    CONSTRAINT ck_ifj_auth_pwd_change CHECK (must_change_password IN (0,1))
);
CREATE INDEX idx_ifj_auth_role ON ifj_auth_user (role_code, enabled);

CREATE TABLE ifj_operation_audit (
    id BIGINT NOT NULL,
    occurred_at DATETIME2(3) NOT NULL,
    actor_login_id NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    actor_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2,
    action_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    target_type NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    target_id NVARCHAR(128) COLLATE Latin1_General_100_BIN2,
    result_code NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    trace_id NVARCHAR(32) COLLATE Latin1_General_100_BIN2,
    request_id NVARCHAR(128) COLLATE Latin1_General_100_BIN2,
    changed_fields NVARCHAR(1024) COLLATE Latin1_General_100_BIN2,
    CONSTRAINT pk_ifj_operation_audit PRIMARY KEY (id)
);
CREATE INDEX idx_ifj_audit_time ON ifj_operation_audit (occurred_at, id);
CREATE INDEX idx_ifj_audit_actor ON ifj_operation_audit (actor_login_id, occurred_at, id);
CREATE INDEX idx_ifj_audit_target ON ifj_operation_audit (target_type, target_id, occurred_at, id);

INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('auth-accounts', 0);
UPDATE ifj_schema_capability SET capability_version = 27, update_time = SYSUTCDATETIME()
WHERE capability_key = 'platform-schema';
