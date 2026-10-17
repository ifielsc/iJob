-- No current writer uses these tables. Reject any unreconciled legacy data before DDL.
SELECT JSON_EXTRACT(CASE WHEN EXISTS (SELECT 1 FROM ifj_job_node) OR EXISTS (SELECT 1 FROM ifj_job_edge) OR EXISTS (SELECT 1 FROM ifj_job_node_policy) OR EXISTS (SELECT 1 FROM ifj_instance WHERE node_id IS NOT NULL) THEN 'Legacy DAG data must be reconciled and archived before V35' ELSE 'null' END, '$');
ALTER TABLE ifj_instance DROP FOREIGN KEY fk_ifj_instance_node;
DROP INDEX fk_ifj_instance_node ON ifj_instance;
DROP TABLE ifj_job_edge;
DROP TABLE ifj_job_node_policy;
DROP TABLE ifj_job_node;
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time)
VALUES ('legacy-dag-removed', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
UPDATE ifj_schema_capability SET capability_version = 35, update_time = UTC_TIMESTAMP(3) WHERE capability_key = 'platform-schema';
