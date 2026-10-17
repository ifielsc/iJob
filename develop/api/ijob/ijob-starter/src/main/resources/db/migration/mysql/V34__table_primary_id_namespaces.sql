-- Stop all old application nodes before migration. Existing IDs are never renumbered.
SELECT JSON_EXTRACT(CASE WHEN NOT EXISTS (SELECT 1 FROM ifj_id_segment WHERE segment_key = 'platform-primary-id') THEN 'Global ID seed is missing; restore migration state before V34' ELSE 'null' END, '$');

UPDATE ifj_id_segment SET next_value = GREATEST(next_value, (SELECT MAX(maximum_id) + 1 FROM (
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_calendar
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_calendar_exception
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_resource
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_task
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_job
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_schedule
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_event_binding
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_notify_config
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_notify_binding
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_mail_channel
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_concurrency_resource
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_auth_user
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_task_definition
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_job_definition
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_job_definition_node
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_job_definition_edge
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_job_definition_node_policy
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_trigger
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_instance
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_instance_log
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_event
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_event_wait
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_dag_gateway_decision
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_notification_outbox
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_notify_send_log
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_schedule_misfire_audit
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_operation_audit
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_job_node
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_job_edge
 UNION ALL
 SELECT COALESCE(MAX(id), 0) AS maximum_id FROM ifj_job_node_policy
) existing_ids)), update_time = UTC_TIMESTAMP(3)
WHERE segment_key = 'platform-primary-id';

INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_calendar', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_calendar_exception', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_resource', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_task', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_job', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_schedule', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_event_binding', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_notify_config', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_notify_binding', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_mail_channel', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_concurrency_resource', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_auth_user', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_task_definition', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_job_definition', next_value, 1, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_job_definition_node', next_value, 100, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_job_definition_edge', next_value, 100, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_job_definition_node_policy', next_value, 100, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_trigger', next_value, 1000, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_instance', next_value, 1000, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_instance_log', next_value, 1000, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_event', next_value, 1000, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_event_wait', next_value, 1000, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_dag_gateway_decision', next_value, 1000, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_notification_outbox', next_value, 1000, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_notify_send_log', next_value, 1000, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_schedule_misfire_audit', next_value, 1000, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_id_segment (segment_key, next_value, segment_size, update_time)
SELECT 'pk:ifj_operation_audit', next_value, 1000, UTC_TIMESTAMP(3)
FROM ifj_id_segment WHERE segment_key = 'platform-primary-id';

-- Exhaust the retired allocator so an old binary cannot reserve overlapping ranges.
UPDATE ifj_id_segment SET next_value = 9223372036854775807, update_time = UTC_TIMESTAMP(3)
WHERE segment_key = 'platform-primary-id';
INSERT INTO ifj_schema_capability (capability_key, capability_version, enabled, create_time, update_time)
VALUES ('per-table-primary-id', 1, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
UPDATE ifj_schema_capability SET capability_version = 34, update_time = UTC_TIMESTAMP(3) WHERE capability_key = 'platform-schema';
