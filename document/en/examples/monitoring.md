# Monitoring collection, thresholds and recovery

[简体中文](../../zh-CN/examples/monitoring.md) · [Manual home](../README.md)

**Verification: tested in this round’s isolated environment within the scope below.**

Fields: [Server monitoring](../user-guide/plugins/server-monitor.md).

## Prepare, configure and run

Deploy an independent Agent, register its token/port and expose only to the test platform. Create/test a monitor resource; initially collect CPU/memory with notifications disabled. Verify collection time, units and missing-data status.

Add thresholds suitable for the test host and distinguish normal, triggered, recovered and unreachable-Agent cases. Do not load business servers to obtain alert screenshots. Verify delivery separately with a dedicated receiver. Stop only this case's Agent and retain logs/identity.

## Scope verified this round

Verified fresh memory metrics from an independent Agent, threshold alarms, invalid-token rejection, offline failure and normal collection after recovery. Alarm mail reached an isolated SMTP sink. Task SUCCESS means collection completed; inspect monitor.healthy and counts for alarms. Recovered collection does not imply a recovery notification or deduplication across runs.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
