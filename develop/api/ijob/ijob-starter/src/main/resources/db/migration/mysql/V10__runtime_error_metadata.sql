ALTER TABLE ifj_trigger
    ADD COLUMN error_code VARCHAR(64) NULL AFTER message,
    ADD COLUMN message_key VARCHAR(128) NULL AFTER error_code,
    ADD COLUMN message_args JSON NULL AFTER message_key,
    ADD INDEX idx_ifj_trigger_error_update
        (error_code, update_time, id);

ALTER TABLE ifj_instance
    ADD COLUMN error_code VARCHAR(64) NULL AFTER message,
    ADD COLUMN message_key VARCHAR(128) NULL AFTER error_code,
    ADD COLUMN message_args JSON NULL AFTER message_key,
    ADD INDEX idx_ifj_instance_error_update
        (error_code, update_time, id);
