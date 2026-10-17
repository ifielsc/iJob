-- V27 security persistence foundation. No account or password is seeded by SQL.
-- Stop older application nodes before upgrade. Authentication activation is a separate step.
CREATE TABLE ifj_auth_user (
    id NUMBER(19) NOT NULL,
    login_id VARCHAR2(128 CHAR) NOT NULL,
    username VARCHAR2(128 CHAR) NOT NULL,
    display_name NVARCHAR2(128),
    password_hash VARCHAR2(100 CHAR) NOT NULL,
    role_code VARCHAR2(16 CHAR) DEFAULT 'VIEWER' NOT NULL,
    enabled NUMBER(1) DEFAULT 1 NOT NULL,
    must_change_password NUMBER(1) DEFAULT 1 NOT NULL,
    security_version NUMBER(19) DEFAULT 0 NOT NULL,
    revision NUMBER(19) DEFAULT 0 NOT NULL,
    created_by VARCHAR2(128 CHAR) NOT NULL,
    create_time TIMESTAMP(3) NOT NULL,
    update_time TIMESTAMP(3) NOT NULL,
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
    id NUMBER(19) NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    actor_login_id VARCHAR2(128 CHAR) NOT NULL,
    actor_name NVARCHAR2(128),
    action_code VARCHAR2(64 CHAR) NOT NULL,
    target_type VARCHAR2(32 CHAR) NOT NULL,
    target_id VARCHAR2(128 CHAR),
    result_code VARCHAR2(32 CHAR) NOT NULL,
    trace_id VARCHAR2(32 CHAR),
    request_id VARCHAR2(128 CHAR),
    changed_fields VARCHAR2(1024 CHAR),
    CONSTRAINT pk_ifj_operation_audit PRIMARY KEY (id)
);
CREATE INDEX idx_ifj_audit_time ON ifj_operation_audit (occurred_at, id);
CREATE INDEX idx_ifj_audit_actor ON ifj_operation_audit (actor_login_id, occurred_at, id);
CREATE INDEX idx_ifj_audit_target ON ifj_operation_audit (target_type, target_id, occurred_at, id);

INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('auth-accounts', 0);
UPDATE ifj_schema_capability SET capability_version = 27, update_time = CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
WHERE capability_key = 'platform-schema';
