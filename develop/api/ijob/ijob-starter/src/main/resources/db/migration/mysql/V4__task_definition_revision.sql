CREATE TABLE ifj_task_definition (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    plugin_type VARCHAR(32) NOT NULL,
    plugin_config LONGTEXT NOT NULL,
    retry_times INT NOT NULL,
    retry_interval INT NOT NULL,
    timeout INT NULL,
    enabled TINYINT(1) NOT NULL,
    description VARCHAR(500) NULL,
    concurrency_type VARCHAR(32) NOT NULL,
    lock_key VARCHAR(255) NULL,
    max_concurrency INT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_task_definition PRIMARY KEY (id),
    CONSTRAINT uk_ifj_task_definition_task_revision UNIQUE (task_id, revision),
    CONSTRAINT ck_ifj_task_definition_revision CHECK (revision > 0),
    CONSTRAINT fk_ifj_task_definition_task
        FOREIGN KEY (task_id) REFERENCES ifj_task (id) ON DELETE RESTRICT,
    INDEX idx_ifj_task_definition_task_time (task_id, create_time, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE ifj_job
    ADD COLUMN retired TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN retired_time DATETIME(3) NULL,
    ADD CONSTRAINT ck_ifj_job_retired_state CHECK ((retired = 0 AND retired_time IS NULL)
        OR (retired = 1 AND retired_time IS NOT NULL));

ALTER TABLE ifj_task
    ADD COLUMN current_task_definition_id BIGINT NULL,
    ADD COLUMN current_revision BIGINT NULL,
    ADD COLUMN retired TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN retired_time DATETIME(3) NULL,
    ADD CONSTRAINT ck_ifj_task_current_pointer_pair CHECK ((current_task_definition_id IS NULL
        AND current_revision IS NULL)
        OR (current_task_definition_id IS NOT NULL AND current_revision IS NOT NULL)),
    ADD CONSTRAINT ck_ifj_task_current_revision_positive
        CHECK (current_revision IS NULL OR current_revision > 0),
    ADD CONSTRAINT ck_ifj_task_retired_state CHECK ((retired = 0 AND retired_time IS NULL)
        OR (retired = 1 AND retired_time IS NOT NULL)),
    ADD INDEX idx_ifj_task_current_definition_id (current_task_definition_id);

ALTER TABLE ifj_trigger
    ADD COLUMN task_definition_id BIGINT NULL,
    ADD CONSTRAINT fk_ifj_trigger_task_definition
        FOREIGN KEY (task_definition_id)
        REFERENCES ifj_task_definition (id) ON DELETE RESTRICT,
    ADD INDEX idx_ifj_trigger_task_definition_status (task_definition_id, status);

ALTER TABLE ifj_instance
    ADD COLUMN task_definition_id BIGINT NULL,
    ADD CONSTRAINT fk_ifj_instance_task_definition
        FOREIGN KEY (task_definition_id)
        REFERENCES ifj_task_definition (id) ON DELETE RESTRICT,
    ADD INDEX idx_ifj_instance_task_definition_status (task_definition_id, status);
