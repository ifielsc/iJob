ALTER TABLE ifj_runtime_command
    ADD INDEX idx_ifj_runtime_command_expiration (status, expires_at, request_id);
