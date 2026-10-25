# Event waits, approval and resumption

[简体中文](../../zh-CN/examples/events-and-approval.md) · [Manual home](../README.md)

**Verification: tested in this round’s isolated environment within the scope below.**

Fields: [Event publishing, waiting and approval](../user-guide/plugins/event.md).

## Prepare, configure and run

Use dedicated eventCode=docs.order.ready and business key order-001 with explicit timeout/lookback. Trigger the wait, verify WAITING_EVENT, then publish a matching event and verify resumption/output on that instance. Exercise wrong keys, duplicates and timeout separately.

For approval, specify the environment's test operator, inspect the request/source instance and approve/reject with a reason. Unauthorized operators must not act; expired approvals are not new success. For file waits, use an independent resource and validate stability/scan bounds before adding a file, avoiding files still being written.

## Scope verified this round

Verified matching/nonmatching events, wait timeout, manual stop and resuming the same wait after a service restart; approval, rejection and withdrawal used database accounts. Repeated file probes created one event. HTTP ingress returned the same event for an identical idempotent submission, rejected changed payload with 409 and triggered the bound job once. Approvals require DATABASE authentication and completion of the initial password change; STATIC mode cannot configure approvers.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.

## Real execution pages

These event-ingress workflow and approved-task results were captured from an isolated instance on 2026-10-26 using the actual API. The instance tree and details refer to the same execution; IDs belong only to this fixture.

![Successful event-ingress workflow with its actual instance tree.](../images/manual/event-run.png)

![The same waiting task instance completed after approval.](../images/manual/approval-run.png)
