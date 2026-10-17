CREATE TABLE ifj_runtime_command (
    request_id VARCHAR(191) NOT NULL,
    instance_id BIGINT NOT NULL,
    root_instance_id BIGINT NOT NULL,
    trigger_id BIGINT NOT NULL,
    command_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    result_message VARCHAR(500) NULL,
    available_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    processing_owner VARCHAR(128) NULL,
    processing_token VARCHAR(36) NULL,
    processing_fence BIGINT NULL,
    processed_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_runtime_command PRIMARY KEY (request_id),
    CONSTRAINT fk_ifj_runtime_command_instance
        FOREIGN KEY (instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_runtime_command_root
        FOREIGN KEY (root_instance_id) REFERENCES ifj_instance (id) ON DELETE CASCADE,
    CONSTRAINT fk_ifj_runtime_command_trigger
        FOREIGN KEY (trigger_id) REFERENCES ifj_trigger (id) ON DELETE CASCADE,
    CONSTRAINT uk_ifj_runtime_command_processing_token UNIQUE (processing_token),
    CONSTRAINT chk_ifj_runtime_command_type CHECK (
        command_type IN ('STOP', 'PAUSE', 'RESUME', 'RETRY')),
    CONSTRAINT chk_ifj_runtime_command_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'REJECTED', 'FAILED', 'EXPIRED')),
    CONSTRAINT chk_ifj_runtime_command_processing CHECK (
        (status = 'PROCESSING' AND processing_owner IS NOT NULL
            AND processing_token IS NOT NULL AND processing_fence IS NOT NULL)
        OR
        (status <> 'PROCESSING' AND processing_owner IS NULL
            AND processing_token IS NULL AND processing_fence IS NULL)),
    INDEX idx_ifj_runtime_command_dispatch
        (trigger_id, status, available_at, expires_at, create_time),
    INDEX idx_ifj_runtime_command_root
        (root_instance_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ifj_concurrency_permit (
    scope_key VARCHAR(191) NOT NULL,
    slot_no INT NOT NULL,
    owner_instance_id BIGINT NULL,
    owner_root_instance_id BIGINT NULL,
    owner_trigger_id BIGINT NULL,
    lease_owner VARCHAR(128) NULL,
    permit_token VARCHAR(36) NULL,
    permit_fence BIGINT NOT NULL DEFAULT 0,
    execution_fence BIGINT NULL,
    lease_deadline DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_concurrency_permit PRIMARY KEY (scope_key, slot_no),
    CONSTRAINT uk_ifj_concurrency_permit_token UNIQUE (permit_token),
    CONSTRAINT uk_ifj_concurrency_permit_owner UNIQUE (owner_instance_id),
    CONSTRAINT chk_ifj_concurrency_permit_slot CHECK (slot_no > 0),
    CONSTRAINT chk_ifj_concurrency_permit_fence CHECK (permit_fence >= 0),
    CONSTRAINT chk_ifj_concurrency_permit_lease CHECK (
        (owner_instance_id IS NULL AND owner_root_instance_id IS NULL
            AND owner_trigger_id IS NULL AND lease_owner IS NULL
            AND permit_token IS NULL AND execution_fence IS NULL
            AND lease_deadline IS NULL)
        OR
        (owner_instance_id IS NOT NULL AND owner_root_instance_id IS NOT NULL
            AND owner_trigger_id IS NOT NULL AND lease_owner IS NOT NULL
            AND permit_token IS NOT NULL AND execution_fence IS NOT NULL
            AND lease_deadline IS NOT NULL)),
    INDEX idx_ifj_concurrency_permit_lease (lease_deadline, scope_key, slot_no),
    INDEX idx_ifj_concurrency_permit_root (owner_root_instance_id, lease_deadline)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
