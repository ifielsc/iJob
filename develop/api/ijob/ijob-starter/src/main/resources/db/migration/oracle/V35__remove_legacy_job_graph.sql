-- No current writer uses these tables. Reject any unreconciled legacy data before DDL.
DECLARE legacy_count NUMBER;
BEGIN
 SELECT (SELECT COUNT(*) FROM ifj_job_node) + (SELECT COUNT(*) FROM ifj_job_edge) + (SELECT COUNT(*) FROM ifj_job_node_policy) + (SELECT COUNT(*) FROM ifj_instance WHERE node_id IS NOT NULL) INTO legacy_count FROM dual;
 IF legacy_count <> 0 THEN RAISE_APPLICATION_ERROR(-20035, 'Legacy DAG data must be reconciled and archived before V35'); END IF;
END;
/
ALTER TABLE ifj_instance DROP CONSTRAINT fk_ifj_instance_node;
DROP INDEX fk_ifj_instance_node;
DROP TABLE ifj_job_edge;
DROP TABLE ifj_job_node_policy;
DROP TABLE ifj_job_node;
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time)
VALUES ('legacy-dag-removed', 1, 1, CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)), CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)));
UPDATE ifj_schema_capability SET capability_version = 35, update_time = CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP(3)) WHERE capability_key = 'platform-schema';
