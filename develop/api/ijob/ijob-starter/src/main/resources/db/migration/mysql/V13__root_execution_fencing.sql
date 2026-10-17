ALTER TABLE ifj_trigger_work_item
    DROP CHECK chk_ifj_trigger_work_item_status,
    ADD COLUMN root_instance_id BIGINT NULL AFTER trigger_id,
    ADD COLUMN fence_token BIGINT NOT NULL DEFAULT 0 AFTER lease_deadline,
    ADD CONSTRAINT chk_ifj_trigger_work_item_status CHECK (
        status IN ('READY', 'CLAIMED', 'SUSPENDED', 'RETRY_WAIT', 'COMPLETED', 'DEAD')),
    ADD CONSTRAINT chk_ifj_trigger_work_item_fence CHECK (fence_token >= 0);

ALTER TABLE ifj_instance
    ADD COLUMN execution_fence BIGINT NOT NULL DEFAULT 0 AFTER root_instance_id,
    ADD CONSTRAINT chk_ifj_instance_execution_fence CHECK (execution_fence >= 0);

UPDATE ifj_trigger_work_item work_item
JOIN (
    SELECT trigger_id, MIN(id) AS root_instance_id
    FROM ifj_instance
    WHERE parent_instance_id IS NULL
      AND trigger_id IS NOT NULL
    GROUP BY trigger_id
) root_instance ON root_instance.trigger_id = work_item.trigger_id
SET work_item.root_instance_id = root_instance.root_instance_id;

UPDATE ifj_instance instance_record
JOIN ifj_trigger_work_item work_item
  ON work_item.trigger_id = instance_record.trigger_id
SET instance_record.execution_fence = work_item.fence_token;

ALTER TABLE ifj_trigger_work_item
    ADD CONSTRAINT uk_ifj_trigger_work_item_root UNIQUE (root_instance_id),
    ADD CONSTRAINT fk_ifj_trigger_work_item_root
        FOREIGN KEY (root_instance_id) REFERENCES ifj_instance (id) ON DELETE SET NULL;
