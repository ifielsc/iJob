# Serial, parallel, joins and conditional routes

[简体中文](../../zh-CN/examples/dag-control-flow.md) · [Manual home](../README.md)

**Verification: the baseline path was exercised in isolation; extensions do not inherit that result.**

Fields: [Page reference](../user-guide/README.md).

## Prepare, configure and run

Verified baseline: docs-flow contains hello→check referencing docs-hello/docs-check with ALL_SUCCESS/STOP. Publish/run and verify both SUCCESS plus actual reference versions.

Extension 1: A→B/C→D with D=ALL_SUCCESS; verify D waits for both upstream terminal states. Extension 2: exclusive gateway amount >100 to review, default finish, unique priorities and exactly one default; test 101/100/missing. Extension 3: fail B and compare STOP/SKIP with D=ALWAYS, retaining failure outcomes when evaluation continues.

Record separate run graphs for extensions; the serial screenshot does not prove all branch variants. See [Node reference](../user-guide/job-nodes.md).

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.

## Real page examples

Verified serial baseline; branch extensions need separate validation.

![Verified serial baseline; branch extensions need separate validation.](../images/manual/job-run.png)
