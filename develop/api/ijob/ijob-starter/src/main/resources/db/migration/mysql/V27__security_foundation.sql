-- V27 security persistence foundation. No account or password is seeded by SQL.
-- Stop older application nodes before upgrade. Authentication activation is a separate step.
CREATE TABLE ifj_auth_user (
    id BIGINT NOT NULL,
    login_id VARCHAR(128) NOT NULL,
    username VARCHAR(128) NOT NULL,
    display_name VARCHAR(128),
    password_hash VARCHAR(100) NOT NULL,
    role_code VARCHAR(16) DEFAULT 'VIEWER' NOT NULL,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    must_change_password BOOLEAN DEFAULT TRUE NOT NULL,
    security_version BIGINT DEFAULT 0 NOT NULL,
    revision BIGINT DEFAULT 0 NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    CONSTRAINT pk_ifj_auth_user PRIMARY KEY (id),
    CONSTRAINT uk_ifj_auth_login UNIQUE (login_id),
    CONSTRAINT uk_ifj_auth_username UNIQUE (username),
    CONSTRAINT ck_ifj_auth_role CHECK (role_code IN ('ADMIN','OPERATOR','VIEWER')),
    CONSTRAINT ck_ifj_auth_version CHECK (security_version >= 0 AND revision >= 0),
    CONSTRAINT ck_ifj_auth_enabled CHECK (enabled IN (0,1)),
    CONSTRAINT ck_ifj_auth_pwd_change CHECK (must_change_password IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE INDEX idx_ifj_auth_role ON ifj_auth_user (role_code, enabled);

CREATE TABLE ifj_operation_audit (
    id BIGINT NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    actor_login_id VARCHAR(128) NOT NULL,
    actor_name VARCHAR(128),
    action_code VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(128),
    result_code VARCHAR(32) NOT NULL,
    trace_id VARCHAR(32),
    request_id VARCHAR(128),
    changed_fields VARCHAR(1024),
    CONSTRAINT pk_ifj_operation_audit PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE INDEX idx_ifj_audit_time ON ifj_operation_audit (occurred_at, id);
CREATE INDEX idx_ifj_audit_actor ON ifj_operation_audit (actor_login_id, occurred_at, id);
CREATE INDEX idx_ifj_audit_target ON ifj_operation_audit (target_type, target_id, occurred_at, id);

INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('auth-accounts', 0);
UPDATE ifj_schema_capability SET capability_version = 27, update_time = UTC_TIMESTAMP(3)
WHERE capability_key = 'platform-schema';
