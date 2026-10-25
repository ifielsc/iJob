# Serial, parallel, joins and conditional routes

[简体中文](../../zh-CN/examples/dag-control-flow.md) · [Manual home](../README.md)

**Verification: tested in this round’s isolated environment within the scope below.**

Fields: [Page reference](../user-guide/README.md).

## Prepare, configure and run

Verified baseline: docs-flow contains hello→check referencing docs-hello/docs-check with ALL_SUCCESS/STOP. Publish/run and verify both SUCCESS plus actual reference versions.

Extension 1: A→B/C→D with D=ALL_SUCCESS; verify D waits for both upstream terminal states. Extension 2: exclusive gateway amount >100 to review, default finish, unique priorities and exactly one default; test 101/100/missing. Extension 3: fail B and compare STOP/SKIP with D=ALWAYS, retaining failure outcomes when evaluation continues.

Record separate run graphs for extensions; the serial screenshot does not prove all branch variants. See [Node reference](../user-guide/job-nodes.md).

## Scope verified this round

This round verified A→B/C→D parallel join and exclusive gateway routes for amounts 101/100. Conflicting values for the same output key caused the join to fail; distinct output keys succeeded. A missing amount failed condition evaluation rather than selecting the default route. STOP blocked downstream execution, SKIP + ALL_SUCCESS skipped it, and SKIP + ALWAYS executed it; all three retained a FAILED root result. Explicit guards for missing values remain separate.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.

## Real page examples

Verified serial baseline; branch extensions need separate validation.

![Verified serial baseline; branch extensions need separate validation.](../images/manual/job-run.png)
