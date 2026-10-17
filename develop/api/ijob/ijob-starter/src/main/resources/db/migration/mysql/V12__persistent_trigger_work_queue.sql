ALTER TABLE ifj_trigger
    ADD COLUMN idempotency_key VARCHAR(191) NULL AFTER trigger_type;

UPDATE ifj_trigger
SET idempotency_key = CONCAT('LEGACY:', id)
WHERE idempotency_key IS NULL;

ALTER TABLE ifj_trigger
    MODIFY COLUMN idempotency_key VARCHAR(191) NOT NULL,
    ADD CONSTRAINT uk_ifj_trigger_idempotency_key UNIQUE (idempotency_key);

CREATE TABLE ifj_trigger_work_item (
    trigger_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    available_at DATETIME(3) NOT NULL,
    lease_owner VARCHAR(128) NULL,
    claim_token VARCHAR(36) NULL,
    lease_deadline DATETIME(3) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    last_error VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_trigger_work_item PRIMARY KEY (trigger_id),
    CONSTRAINT fk_ifj_trigger_work_item_trigger
        FOREIGN KEY (trigger_id) REFERENCES ifj_trigger (id) ON DELETE CASCADE,
    CONSTRAINT uk_ifj_trigger_work_item_claim_token UNIQUE (claim_token),
    CONSTRAINT chk_ifj_trigger_work_item_status CHECK (
        status IN ('READY', 'CLAIMED', 'RETRY_WAIT', 'COMPLETED', 'DEAD')),
    CONSTRAINT chk_ifj_trigger_work_item_attempts CHECK (
        attempt_count >= 0 AND max_attempts BETWEEN 1 AND 100),
    CONSTRAINT chk_ifj_trigger_work_item_lease CHECK (
        (status = 'CLAIMED' AND lease_owner IS NOT NULL
            AND claim_token IS NOT NULL AND lease_deadline IS NOT NULL)
        OR
        (status <> 'CLAIMED' AND lease_owner IS NULL
            AND claim_token IS NULL AND lease_deadline IS NULL)),
    INDEX idx_ifj_trigger_work_item_dispatch
        (status, available_at, lease_deadline, trigger_id),
    INDEX idx_ifj_trigger_work_item_owner
        (lease_owner, lease_deadline, trigger_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO ifj_trigger_work_item (
    trigger_id, status, available_at, attempt_count, max_attempts
)
SELECT id, 'READY', UTC_TIMESTAMP(3), 0, 5
FROM ifj_trigger
WHERE status IN ('WAITING', 'RUNNING');
