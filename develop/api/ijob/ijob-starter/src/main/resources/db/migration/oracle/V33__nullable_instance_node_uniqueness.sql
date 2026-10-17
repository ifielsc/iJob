-- Oracle treats partially null composite unique keys as conflicting.
-- Only fully populated legacy/current node identities participate in uniqueness.
-- Create the replacement indexes before removing the original constraints.
CREATE UNIQUE INDEX uk_ifj_inst_legacy_node_v33 ON ifj_instance (
    CASE WHEN parent_instance_id IS NOT NULL AND node_id IS NOT NULL
        THEN parent_instance_id END,
    CASE WHEN parent_instance_id IS NOT NULL AND node_id IS NOT NULL
        THEN node_id END
);

CREATE UNIQUE INDEX uk_ifj_inst_def_node_v33 ON ifj_instance (
    CASE WHEN parent_instance_id IS NOT NULL AND definition_node_id IS NOT NULL
        THEN parent_instance_id END,
    CASE WHEN parent_instance_id IS NOT NULL AND definition_node_id IS NOT NULL
        THEN definition_node_id END
);

ALTER TABLE ifj_instance DROP CONSTRAINT uk_ifj_instance_parent_node;
ALTER TABLE ifj_instance DROP CONSTRAINT uk_ifj_instance_paren_9c382e7e;
