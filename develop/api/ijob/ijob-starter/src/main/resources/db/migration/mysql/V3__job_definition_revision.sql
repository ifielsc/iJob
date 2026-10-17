CREATE TABLE ifj_job_definition (
    id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    timeout INT NULL,
    enabled TINYINT(1) NOT NULL,
    description VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_job_definition PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_job_revision UNIQUE (job_id, revision),
    CONSTRAINT ck_ifj_job_definition_revision CHECK (revision > 0),
    CONSTRAINT fk_ifj_job_definition_job
        FOREIGN KEY (job_id) REFERENCES ifj_job (id) ON DELETE RESTRICT,
    INDEX idx_ifj_job_definition_job_time (job_id, create_time, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ifj_job_definition_node (
    id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    ref_id BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_job_definition_node PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_node_key UNIQUE (definition_id, node_key),
    CONSTRAINT fk_ifj_job_definition_node_definition
        FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    INDEX idx_ifj_job_definition_node_ref (node_type, ref_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ifj_job_definition_node_policy (
    id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    fail_strategy VARCHAR(32) NOT NULL,
    execute_condition VARCHAR(32) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_job_definition_node_policy PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_node_policy_node UNIQUE (node_id),
    CONSTRAINT fk_ifj_job_definition_node_policy_definition
        FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_job_definition_node_policy_node
        FOREIGN KEY (node_id) REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT,
    INDEX idx_ifj_job_definition_node_policy_definition (definition_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ifj_job_definition_edge (
    id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    from_node_id BIGINT NOT NULL,
    to_node_id BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_job_definition_edge PRIMARY KEY (id),
    CONSTRAINT uk_ifj_job_definition_edge_path
        UNIQUE (definition_id, from_node_id, to_node_id),
    CONSTRAINT fk_ifj_job_definition_edge_definition
        FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_job_definition_edge_from_node
        FOREIGN KEY (from_node_id) REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ifj_job_definition_edge_to_node
        FOREIGN KEY (to_node_id) REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT,
    INDEX idx_ifj_job_definition_edge_to_node (to_node_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE ifj_job
    ADD COLUMN current_definition_id BIGINT NULL,
    ADD COLUMN current_revision BIGINT NULL,
    ADD CONSTRAINT ck_ifj_job_current_pointer_pair CHECK ((current_definition_id IS NULL
        AND current_revision IS NULL)
        OR (current_definition_id IS NOT NULL AND current_revision IS NOT NULL)),
    ADD INDEX idx_ifj_job_current_definition_id (current_definition_id);

ALTER TABLE ifj_trigger
    ADD COLUMN definition_id BIGINT NULL,
    ADD CONSTRAINT fk_ifj_trigger_definition
        FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    ADD INDEX idx_ifj_trigger_definition_status (definition_id, status);

ALTER TABLE ifj_instance
    ADD COLUMN definition_id BIGINT NULL,
    ADD COLUMN definition_node_id BIGINT NULL,
    ADD CONSTRAINT fk_ifj_instance_definition
        FOREIGN KEY (definition_id) REFERENCES ifj_job_definition (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_ifj_instance_definition_node
        FOREIGN KEY (definition_node_id)
        REFERENCES ifj_job_definition_node (id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_ifj_instance_parent_definition_node
        UNIQUE (parent_instance_id, definition_node_id),
    ADD INDEX idx_ifj_instance_definition_status (definition_id, status);
