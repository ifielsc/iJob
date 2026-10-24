# Daily operation and maintenance windows

[简体中文](../../zh-CN/operations/daily.md) · [Manual home](../README.md)

## Daily checks

Record program hash, IJOB_HOME, service account, ingress and database. Inspect node online state, freshness, roles, scanning and renewal in the dashboard; inspect queues, long runs, failures and executing nodes in the run center. UNKNOWN/stale/unavailable does not mean zero load. A working web page does not prove Worker/SCHEDULER health.

Application logs explain startup, connectivity and coordination; instance logs explain individual executions. Correlate time, instance/trigger IDs and node names; sanitize exports. Native logs are under home/logs; use Compose logs for Docker. Monitor disk, database connections, file/plugin mounts and clock synchronization too.

## Maintenance window

1. Record schedules/ingress to pause and their original enabled state; pause new business triggers.
2. Review active instances; wait for completion or request stop and verify the result.
3. Shut down gracefully using platform commands, verify process/container state and take a consistent backup.
4. Verify readiness, login, node responsibilities and a separate sample before restoring schedules.

Stopping an instance, disabling a definition, disabling a schedule and stopping the platform have different effects. None reverses committed external side effects. Check the target system before retrying an uncertain result.
