CREATE TABLE ifj_job_definition_group (
    definition_id bigint NOT NULL,
    group_code varchar(64) NOT NULL,
    create_time timestamp(3) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_job_definition_group PRIMARY KEY (definition_id, group_code),
    CONSTRAINT fk_ifj_job_def_group_definition FOREIGN KEY (definition_id)
        REFERENCES ifj_job_definition (id) ON DELETE CASCADE
);
CREATE INDEX idx_ifj_job_def_group_code
    ON ifj_job_definition_group (group_code, definition_id);

CREATE TABLE ifj_task_definition_group (
    definition_id bigint NOT NULL,
    group_code varchar(64) NOT NULL,
    create_time timestamp(3) without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_task_definition_group PRIMARY KEY (definition_id, group_code),
    CONSTRAINT fk_ifj_task_def_group_definition FOREIGN KEY (definition_id)
        REFERENCES ifj_task_definition (id) ON DELETE CASCADE
);
CREATE INDEX idx_ifj_task_def_group_code
    ON ifj_task_definition_group (group_code, definition_id);

ALTER TABLE ifj_app_run ADD COLUMN worker_groups varchar(1100) NOT NULL DEFAULT '';
ALTER TABLE ifj_app_run ADD COLUMN runtime_protocol_version int NOT NULL DEFAULT 1;

UPDATE ifj_schema_capability
SET capability_version = 20, update_time = CURRENT_TIMESTAMP(3)
WHERE capability_key = 'platform-schema';

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'execution-group-routing', 1, TRUE, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
);
