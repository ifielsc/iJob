CREATE TABLE ifj_job_definition_group (
    definition_id BIGINT NOT NULL,
    group_code VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (definition_id, group_code),
    KEY idx_ifj_job_def_group_code (group_code, definition_id),
    CONSTRAINT fk_ifj_job_def_group_definition FOREIGN KEY (definition_id)
        REFERENCES ifj_job_definition (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE ifj_task_definition_group (
    definition_id BIGINT NOT NULL,
    group_code VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (definition_id, group_code),
    KEY idx_ifj_task_def_group_code (group_code, definition_id),
    CONSTRAINT fk_ifj_task_def_group_definition FOREIGN KEY (definition_id)
        REFERENCES ifj_task_definition (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

ALTER TABLE ifj_app_run
    ADD COLUMN worker_groups VARCHAR(1100) NOT NULL DEFAULT '' AFTER roles,
    ADD COLUMN runtime_protocol_version INT NOT NULL DEFAULT 1 AFTER worker_groups;

UPDATE ifj_schema_capability
SET capability_version = 20, update_time = UTC_TIMESTAMP(3)
WHERE capability_key = 'platform-schema';

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'execution-group-routing', 1, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
);
