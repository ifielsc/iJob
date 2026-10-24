# Monitoring collection, thresholds and recovery

[简体中文](../../zh-CN/examples/monitoring.md) · [Manual home](../README.md)

**Verification: configuration/workflow example; this round did not complete end-to-end acceptance of its external dependencies.**

Fields: [Server monitoring](../user-guide/plugins/server-monitor.md).

## Prepare, configure and run

Deploy an independent Agent, register its token/port and expose only to the test platform. Create/test a monitor resource; initially collect CPU/memory with notifications disabled. Verify collection time, units and missing-data status.

Add thresholds suitable for the test host and distinguish normal, triggered, recovered and unreachable-Agent cases. Do not load business servers to obtain alert screenshots. Verify delivery separately with a dedicated receiver. Stop only this case's Agent and retain logs/identity.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
