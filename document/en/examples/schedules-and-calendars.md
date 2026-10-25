# Time zones, business days and catch-up

[简体中文](../../zh-CN/examples/schedules-and-calendars.md) · [Manual home](../README.md)

**Verification: tested in this round’s isolated environment within the scope below.**

Fields: [Page reference](../user-guide/README.md).

## Prepare, configure and run

Create a disabled docs-hello schedule with INTERVAL=3600 and preview five future candidates. Attach a calendar excluding a future date and check allowed/excluded candidates. Header display zone only changes presentation; backend scheduling settings determine business time.

For actual automatic firing, enable a harmless one-time schedule in a controlled window and inspect planned time, trigger and instance. Test catch-up in a separate installation with a controlled outage, comparing SKIP/FIRE_ONCE/CATCH_UP_BOUNDED and queue deadlines. Retain grace/window/count settings and actual fires. Disable practice schedules afterward. Preview screenshots prove calculation, not execution.

## Scope verified this round

Calendar preview verified the excluded date. During controlled downtime an isolated instance missed three bounded interval firings. Recovery produced SKIP=0, FIRE_ONCE=1 and CATCH_UP_BOUNDED=2 (limit 2); accepted triggers and executions succeeded with Asia/Shanghai business time. All exercise schedules were then disabled. An unmatched scheduled trigger also expired as SKIPPED without creating an execution instance. Other zones and daylight-saving transitions remain separate.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.

## Real page examples

Real candidate preview; automatic firing/catch-up was not executed.

![Real candidate preview; automatic firing/catch-up was not executed.](../images/manual/schedule-preview.png)
