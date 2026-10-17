-- iJob MySQL 8 maintenance leadership and durable root runtime deadlines.

ALTER TABLE ifj_instance
    ADD COLUMN timeout_at DATETIME(3) NULL AFTER start_time,
    ADD INDEX idx_ifj_instance_root_timeout
        (parent_instance_id, status, timeout_at, id);

UPDATE ifj_instance instance_record
LEFT JOIN ifj_task_definition task_definition
  ON instance_record.target_type = 'TASK'
 AND task_definition.id = instance_record.task_definition_id
LEFT JOIN ifj_job_definition job_definition
  ON instance_record.target_type = 'JOB'
 AND job_definition.id = instance_record.definition_id
SET instance_record.timeout_at = TIMESTAMPADD(
        SECOND,
        CASE instance_record.target_type
            WHEN 'TASK' THEN task_definition.timeout
            WHEN 'JOB' THEN job_definition.timeout
            ELSE NULL
        END,
        COALESCE(instance_record.start_time, instance_record.create_time))
WHERE instance_record.parent_instance_id IS NULL
  AND instance_record.status IN ('WAITING', 'RUNNING', 'PAUSED', 'WAITING_EVENT')
  AND instance_record.timeout_at IS NULL
  AND CASE instance_record.target_type
        WHEN 'TASK' THEN task_definition.timeout
        WHEN 'JOB' THEN job_definition.timeout
        ELSE NULL
      END IS NOT NULL;

CREATE TABLE ifj_maintenance_lease (
    lease_name VARCHAR(64) NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_token VARCHAR(36) NULL,
    lease_deadline DATETIME(3) NULL,
    fence_token BIGINT NOT NULL DEFAULT 0,
    last_acquired_at DATETIME(3) NULL,
    last_completed_at DATETIME(3) NULL,
    last_success BOOLEAN NULL,
    last_failure_code VARCHAR(64) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_maintenance_lease PRIMARY KEY (lease_name),
    CONSTRAINT uk_ifj_maintenance_lease_token UNIQUE (lease_token),
    CONSTRAINT chk_ifj_maintenance_lease_fence CHECK (fence_token >= 0),
    CONSTRAINT chk_ifj_maintenance_lease_holder CHECK (
        (lease_owner IS NULL AND lease_token IS NULL AND lease_deadline IS NULL)
        OR
        (lease_owner IS NOT NULL AND lease_token IS NOT NULL
            AND lease_deadline IS NOT NULL)),
    INDEX idx_ifj_maintenance_lease_deadline
        (lease_deadline, lease_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
