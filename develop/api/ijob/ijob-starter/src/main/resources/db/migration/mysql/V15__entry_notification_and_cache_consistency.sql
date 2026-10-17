-- iJob MySQL 8 multi-instance entry and notification outbox coordination.

ALTER TABLE ifj_schedule
    ADD COLUMN fire_claim_token VARCHAR(36) NULL AFTER next_fire_time,
    ADD COLUMN fire_claimed_at DATETIME(3) NULL AFTER fire_claim_token,
    ADD CONSTRAINT uk_ifj_schedule_fire_claim_token UNIQUE (fire_claim_token),
    ADD CONSTRAINT chk_ifj_schedule_fire_claim CHECK (
        (fire_claim_token IS NULL AND fire_claimed_at IS NULL)
        OR
        (fire_claim_token IS NOT NULL AND fire_claimed_at IS NOT NULL));

CREATE TABLE ifj_notification_outbox (
    id BIGINT NOT NULL,
    deduplication_key VARCHAR(191) NOT NULL,
    notify_config_id BIGINT NOT NULL,
    notify_type VARCHAR(32) NULL,
    config_snapshot LONGTEXT NULL,
    config_enabled BOOLEAN NULL,
    title VARCHAR(255) NULL,
    subject VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    correlation_id VARCHAR(64) NULL,
    operator_id VARCHAR(128) NULL,
    target_type VARCHAR(32) NULL,
    target_id BIGINT NULL,
    instance_id BIGINT NULL,
    notify_binding_id BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    available_at DATETIME(3) NOT NULL,
    lease_owner VARCHAR(128) NULL,
    claim_token VARCHAR(36) NULL,
    lease_deadline DATETIME(3) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    last_failure_code VARCHAR(64) NULL,
    last_failure_summary VARCHAR(500) NULL,
    delivered_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_notification_outbox PRIMARY KEY (id),
    CONSTRAINT uk_ifj_notification_outbox_deduplication UNIQUE (deduplication_key),
    CONSTRAINT uk_ifj_notification_outbox_claim_token UNIQUE (claim_token),
    CONSTRAINT chk_ifj_notification_outbox_status CHECK (
        status IN ('READY', 'CLAIMED', 'RETRY_WAIT', 'DELIVERED', 'DEAD')),
    CONSTRAINT chk_ifj_notification_outbox_attempts CHECK (
        attempt_count >= 0 AND max_attempts BETWEEN 1 AND 100),
    CONSTRAINT chk_ifj_notification_outbox_config_snapshot CHECK (
        (notify_type IS NULL AND config_snapshot IS NULL AND config_enabled IS NULL)
        OR
        (notify_type IS NOT NULL AND config_snapshot IS NOT NULL
            AND config_enabled IS NOT NULL)),
    CONSTRAINT chk_ifj_notification_outbox_lease CHECK (
        (status = 'CLAIMED' AND lease_owner IS NOT NULL
            AND claim_token IS NOT NULL AND lease_deadline IS NOT NULL)
        OR
        (status <> 'CLAIMED' AND lease_owner IS NULL
            AND claim_token IS NULL AND lease_deadline IS NULL)),
    CONSTRAINT chk_ifj_notification_outbox_delivery CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR
        (status <> 'DELIVERED' AND delivered_at IS NULL)),
    INDEX idx_ifj_notification_outbox_dispatch
        (status, available_at, id),
    INDEX idx_ifj_notification_outbox_lease
        (status, lease_deadline, id),
    INDEX idx_ifj_notification_outbox_instance
        (instance_id, status, id),
    INDEX idx_ifj_notification_outbox_created
        (create_time, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
