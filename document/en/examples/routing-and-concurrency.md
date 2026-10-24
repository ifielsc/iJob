# Node routing and shared concurrency

[简体中文](../../zh-CN/examples/routing-and-concurrency.md) · [Manual home](../README.md)

**Verification: configuration/workflow example; this round did not complete end-to-end acceptance of its external dependencies.**

Fields: [Page reference](../user-guide/README.md).

## Prepare, configure and run

Set node groups=finance, task A group finance and task B group other using harmless scripts. Verify A is eligible and B waits, then exercise task/job allowlist combinations from the [scope matrix](../configuration/routing.md). Empty node groups match only unrestricted definitions.

Create a shared concurrency resource of capacity 1 and bind two long tasks. Trigger together and observe sequential permits. Plugin/task/shared-resource limits all apply; a configuration explanation reserves no permits. Restore test settings and resolve pending work afterward. This needs a dedicated Worker exercise; serial single-node screenshots do not prove multi-node routing.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
