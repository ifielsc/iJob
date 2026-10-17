-- Reject legacy approval snapshots that cannot be represented losslessly.
SELECT JSON_EXTRACT(
    CASE WHEN EXISTS (
        SELECT 1
        FROM ifj_event_wait
        WHERE wait_type = 'APPROVAL'
          AND CASE
              WHEN allowed_operator_ids IS NULL THEN 1
              WHEN JSON_VALID(allowed_operator_ids) = 0 THEN 1
              WHEN JSON_TYPE(allowed_operator_ids) <> 'ARRAY' THEN 1
              WHEN JSON_LENGTH(allowed_operator_ids) = 0 THEN 1
              ELSE 0
          END = 1
    ) THEN 'invalid approval operator snapshot' ELSE 'null' END,
    '$');

SELECT JSON_EXTRACT(
    CASE WHEN EXISTS (
        SELECT 1
        FROM ifj_event_wait wait_record
        JOIN JSON_TABLE(
            wait_record.allowed_operator_ids,
            '$[*]' COLUMNS (
                operator_id VARCHAR(1024) PATH '$' NULL ON EMPTY
            )
        ) operator_record
        WHERE wait_record.wait_type = 'APPROVAL'
          AND (operator_record.operator_id IS NULL
            OR CHAR_LENGTH(operator_record.operator_id) = 0
            OR CHAR_LENGTH(operator_record.operator_id) > 128)
    ) THEN 'invalid approval operator value' ELSE 'null' END,
    '$');

CREATE TABLE ifj_event_wait_operator (
    wait_id BIGINT NOT NULL,
    operator_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_event_wait_operator PRIMARY KEY (wait_id, operator_id),
    CONSTRAINT fk_ifj_wait_operator_wait FOREIGN KEY (wait_id)
        REFERENCES ifj_event_wait (id) ON DELETE CASCADE,
    CONSTRAINT chk_ifj_wait_operator_id CHECK (CHAR_LENGTH(operator_id) > 0),
    INDEX idx_ifj_wait_operator_lookup (operator_id, wait_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO ifj_event_wait_operator (wait_id, operator_id, create_time)
SELECT DISTINCT wait_record.id, operator_record.operator_id, UTC_TIMESTAMP(3)
FROM ifj_event_wait wait_record
JOIN JSON_TABLE(
    wait_record.allowed_operator_ids,
    '$[*]' COLUMNS (
        operator_id VARCHAR(128) PATH '$' ERROR ON ERROR
    )
) operator_record
WHERE wait_record.wait_type = 'APPROVAL';

CREATE TABLE ifj_schema_capability (
    capability_key VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    capability_version INT NOT NULL,
    enabled TINYINT(1) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_ifj_schema_capability PRIMARY KEY (capability_key),
    CONSTRAINT chk_ifj_schema_cap_version CHECK (capability_version > 0),
    CONSTRAINT chk_ifj_schema_cap_enabled CHECK (enabled IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- This is the final full-table maximum scan. Runtime reservations use only this row after V19.
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'platform-primary-id',
       GREATEST(
           COALESCE((SELECT MAX(id) FROM ifj_calendar), 0),
           COALESCE((SELECT MAX(id) FROM ifj_calendar_exception), 0),
           COALESCE((SELECT MAX(id) FROM ifj_concurrency_policy), 0),
           COALESCE((SELECT MAX(id) FROM ifj_event_binding), 0),
           COALESCE((SELECT MAX(id) FROM ifj_event), 0),
           COALESCE((SELECT MAX(id) FROM ifj_event_wait), 0),
           COALESCE((SELECT MAX(id) FROM ifj_instance), 0),
           COALESCE((SELECT MAX(id) FROM ifj_instance_log), 0),
           COALESCE((SELECT MAX(id) FROM ifj_job), 0),
           COALESCE((SELECT MAX(id) FROM ifj_job_definition), 0),
           COALESCE((SELECT MAX(id) FROM ifj_job_definition_edge), 0),
           COALESCE((SELECT MAX(id) FROM ifj_job_definition_node), 0),
           COALESCE((SELECT MAX(id) FROM ifj_job_definition_node_policy), 0),
           COALESCE((SELECT MAX(id) FROM ifj_job_edge), 0),
           COALESCE((SELECT MAX(id) FROM ifj_job_node), 0),
           COALESCE((SELECT MAX(id) FROM ifj_job_node_policy), 0),
           COALESCE((SELECT MAX(id) FROM ifj_notify_binding), 0),
           COALESCE((SELECT MAX(id) FROM ifj_notify_config), 0),
           COALESCE((SELECT MAX(id) FROM ifj_notification_outbox), 0),
           COALESCE((SELECT MAX(id) FROM ifj_notify_send_log), 0),
           COALESCE((SELECT MAX(id) FROM ifj_resource), 0),
           COALESCE((SELECT MAX(id) FROM ifj_schedule), 0),
           COALESCE((SELECT MAX(id) FROM ifj_task), 0),
           COALESCE((SELECT MAX(id) FROM ifj_task_definition), 0),
           COALESCE((SELECT MAX(id) FROM ifj_trigger), 0)
       ) + 1,
       1000,
       UTC_TIMESTAMP(3)
ON DUPLICATE KEY UPDATE
    next_value = GREATEST(ifj_id_segment.next_value, VALUES(next_value)),
    update_time = UTC_TIMESTAMP(3);

INSERT INTO ifj_schema_capability (
    capability_key, capability_version, enabled, create_time, update_time
) VALUES
    ('platform-schema', 19, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('event-wait-operator-relation', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('stable-id-segment-seed', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
