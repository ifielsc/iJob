# Event waits, approval and resumption

[简体中文](../../zh-CN/examples/events-and-approval.md) · [Manual home](../README.md)

**Verification: configuration/workflow example; this round did not complete end-to-end acceptance of its external dependencies.**

Fields: [Event publishing, waiting and approval](../user-guide/plugins/event.md).

## Prepare, configure and run

Use dedicated eventCode=docs.order.ready and business key order-001 with explicit timeout/lookback. Trigger the wait, verify WAITING_EVENT, then publish a matching event and verify resumption/output on that instance. Exercise wrong keys, duplicates and timeout separately.

For approval, specify the environment's test operator, inspect the request/source instance and approve/reject with a reason. Unauthorized operators must not act; expired approvals are not new success. For file waits, use an independent resource and validate stability/scan bounds before adding a file, avoiding files still being written.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
