CREATE TABLE ifj_job_definition_group (
    definition_id NUMBER(19) NOT NULL,
    group_code VARCHAR2(64 CHAR) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_job_definition_group PRIMARY KEY (definition_id, group_code),
    CONSTRAINT fk_ifj_job_group_def FOREIGN KEY (definition_id)
        REFERENCES ifj_job_definition (id) ON DELETE CASCADE
);
CREATE INDEX idx_ifj_job_def_group_code
    ON ifj_job_definition_group (group_code, definition_id);

CREATE TABLE ifj_task_definition_group (
    definition_id NUMBER(19) NOT NULL,
    group_code VARCHAR2(64 CHAR) NOT NULL,
    create_time TIMESTAMP(3) DEFAULT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) NOT NULL,
    CONSTRAINT pk_ifj_task_definition_group PRIMARY KEY (definition_id, group_code),
    CONSTRAINT fk_ifj_task_group_def FOREIGN KEY (definition_id)
        REFERENCES ifj_task_definition (id) ON DELETE CASCADE
);
CREATE INDEX idx_ifj_task_def_group_code
    ON ifj_task_definition_group (group_code, definition_id);

ALTER TABLE ifj_app_run ADD (
    worker_groups VARCHAR2(1100 CHAR),
    runtime_protocol_version NUMBER(10) DEFAULT 1 NOT NULL
);

UPDATE ifj_schema_capability
SET capability_version = 20,
    update_time = CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
WHERE capability_key = 'platform-schema';

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES (
    'execution-group-routing', 1, 1,
    CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)),
    CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3))
);
