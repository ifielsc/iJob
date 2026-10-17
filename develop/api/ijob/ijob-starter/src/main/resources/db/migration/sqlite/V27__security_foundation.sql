-- V27 security persistence foundation. No account or password is seeded by SQL.
-- Stop older application nodes before upgrade. Authentication activation is a separate step.
CREATE TABLE ifj_auth_user (
    id INTEGER NOT NULL,
    login_id TEXT NOT NULL,
    username TEXT NOT NULL,
    display_name TEXT,
    password_hash TEXT NOT NULL,
    role_code TEXT DEFAULT 'VIEWER' NOT NULL,
    enabled INTEGER DEFAULT 1 NOT NULL,
    must_change_password INTEGER DEFAULT 1 NOT NULL,
    security_version INTEGER DEFAULT 0 NOT NULL,
    revision INTEGER DEFAULT 0 NOT NULL,
    created_by TEXT NOT NULL,
    create_time TEXT NOT NULL,
    update_time TEXT NOT NULL,
    CONSTRAINT ck_s27_user_login_id CHECK (login_id IS NULL OR length(login_id) <= 128),
    CONSTRAINT ck_s27_user_username CHECK (username IS NULL OR length(username) <= 128),
    CONSTRAINT ck_s27_user_display_name CHECK (display_name IS NULL OR length(display_name) <= 128),
    CONSTRAINT ck_s27_user_password_hash CHECK (password_hash IS NULL OR length(password_hash) <= 100),
    CONSTRAINT ck_s27_user_created_by CHECK (created_by IS NULL OR length(created_by) <= 128),
    CONSTRAINT ck_s27_user_create_time CHECK (length(create_time) = 23 AND create_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'),
    CONSTRAINT ck_s27_user_update_time CHECK (length(update_time) = 23 AND update_time GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'),
    CONSTRAINT ck_s27_user_id CHECK (typeof(id) = 'integer' AND id > 0),
    CONSTRAINT ck_s27_user_revision CHECK (typeof(revision) = 'integer' AND typeof(security_version) = 'integer'),
    CONSTRAINT pk_ifj_auth_user PRIMARY KEY (id),
    CONSTRAINT uk_ifj_auth_login UNIQUE (login_id),
    CONSTRAINT uk_ifj_auth_username UNIQUE (username),
    CONSTRAINT ck_ifj_auth_role CHECK (role_code IN ('ADMIN','OPERATOR','VIEWER')),
    CONSTRAINT ck_ifj_auth_version CHECK (security_version >= 0 AND revision >= 0),
    CONSTRAINT ck_ifj_auth_enabled CHECK (enabled IN (0,1)),
    CONSTRAINT ck_ifj_auth_pwd_change CHECK (must_change_password IN (0,1))
) WITHOUT ROWID;
CREATE INDEX idx_ifj_auth_role ON ifj_auth_user (role_code, enabled);

CREATE TABLE ifj_operation_audit (
    id INTEGER NOT NULL,
    occurred_at TEXT NOT NULL,
    actor_login_id TEXT NOT NULL,
    actor_name TEXT,
    action_code TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT,
    result_code TEXT NOT NULL,
    trace_id TEXT,
    request_id TEXT,
    changed_fields TEXT,
    CONSTRAINT ck_s27_audit_actor_login_id CHECK (actor_login_id IS NULL OR length(actor_login_id) <= 128),
    CONSTRAINT ck_s27_audit_actor_name CHECK (actor_name IS NULL OR length(actor_name) <= 128),
    CONSTRAINT ck_s27_audit_action_code CHECK (action_code IS NULL OR length(action_code) <= 64),
    CONSTRAINT ck_s27_audit_target_type CHECK (target_type IS NULL OR length(target_type) <= 32),
    CONSTRAINT ck_s27_audit_target_id CHECK (target_id IS NULL OR length(target_id) <= 128),
    CONSTRAINT ck_s27_audit_result_code CHECK (result_code IS NULL OR length(result_code) <= 32),
    CONSTRAINT ck_s27_audit_trace_id CHECK (trace_id IS NULL OR length(trace_id) <= 32),
    CONSTRAINT ck_s27_audit_request_id CHECK (request_id IS NULL OR length(request_id) <= 128),
    CONSTRAINT ck_s27_audit_changed_fields CHECK (changed_fields IS NULL OR length(changed_fields) <= 1024),
    CONSTRAINT ck_s27_audit_time CHECK (length(occurred_at) = 23 AND occurred_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]'),
    CONSTRAINT ck_s27_audit_id CHECK (typeof(id) = 'integer' AND id > 0),
    CONSTRAINT pk_ifj_operation_audit PRIMARY KEY (id)
) WITHOUT ROWID;
CREATE INDEX idx_ifj_audit_time ON ifj_operation_audit (occurred_at, id);
CREATE INDEX idx_ifj_audit_actor ON ifj_operation_audit (actor_login_id, occurred_at, id);
CREATE INDEX idx_ifj_audit_target ON ifj_operation_audit (target_type, target_id, occurred_at, id);

INSERT INTO ifj_runtime_guard (guard_key, revision) VALUES ('auth-accounts', 0);
UPDATE ifj_schema_capability SET capability_version = 27, update_time = strftime('%Y-%m-%d %H:%M:%f','now')
WHERE capability_key = 'platform-schema';
