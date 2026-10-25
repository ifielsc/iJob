# Node routing and shared concurrency

[简体中文](../../zh-CN/examples/routing-and-concurrency.md) · [Manual home](../README.md)

**Verification: tested in this round’s isolated environment within the scope below.**

Fields: [Page reference](../user-guide/README.md).

## Prepare, configure and run

Set node groups=finance, task A group finance and task B group other using harmless scripts. Verify A is eligible and B waits, then exercise task/job allowlist combinations from the [scope matrix](../configuration/routing.md). Empty node groups match only unrestricted definitions.

Create a shared concurrency resource of capacity 1 and bind two long tasks. Trigger together and observe sequential permits. Plugin/task/shared-resource limits all apply; a configuration explanation reserves no permits. Restore test settings and resolve pending work afterward. This needs a dedicated Worker exercise; serial single-node screenshots do not prove multi-node routing.

## Scope verified this round

This round created a new PostgreSQL platform database and two CLUSTER nodes on the same VM. finance/other tasks ran under distinct system accounts; execution intervals overlapped without a shared limit and became mutually exclusive with capacity 1. An unmatched trigger waited until an eligible Worker joined; the original node configuration was then restored. The empty, task-only, job-only and combined ID allowlists were verified with real root triggers. An allowed job executed an unlisted descendant, while a direct trigger of that task waited. Restoring the original scope drained all synthetic queued triggers. Failover remains separate. Two processes on one VM do not establish physical high availability.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
