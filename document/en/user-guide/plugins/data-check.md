# Data checks

[简体中文](../../../zh-CN/user-guide/plugins/data-check.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `DATA_CHECK`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Page field/action | Purpose and rules |
| --- | --- |
| Database resource / Query timeout (seconds) | Business DB resource; per-query timeout defaults to 30 seconds. |
| Check name / Query SQL / Type | Give each check a name/query; type NORMAL/COUNT/EXISTS/EMPTY selects result semantics. |
| Comparison / Expected value / Lower and upper bounds | EQ/NE/GT/GE/LT/LE/BETWEEN; use expected for ordinary comparisons and bounds for ranges, preserving numeric types. |
| Actual-value variable / Level / Result handlers | Output variable, severity (default ERROR) and handlers; warnings do not repair data. |

## Minimal example and verification

Prepare the dedicated resources above, fill the form, save, then trigger manually and verify the instance, outputs and target system as described below. Saving alone does not execute.

| Setting | Value to enter |
| --- | --- |
| Database resource | Select the test resource containing three orders rows |
| Check name | `three-orders` |
| Query SQL | `SELECT COUNT(*) FROM orders` |
| Type | Value comparison (NORMAL) |
| Comparison | Equal EQ |
| Value type | Number |
| Expected value | `3` |
| Level | Error ERROR |

Add the DB resource and run against three rows, then use an independent two-row copy to verify failure. Inspect individual checks and overall status. Do not confuse COUNT semantics with SQL COUNT(*); the scalar example uses NORMAL. Checking detects problems and does not repair data unless an explicit handler does so.

Replace example resources, paths and object names with values from your environment. See the [case index](../../examples.md) for actual external-protocol verification status.

## Real page examples

NORMAL value comparison with ERROR severity; comparison values are further down the form.

![NORMAL value comparison with ERROR severity; comparison values are further down the form.](../../images/manual/check-editor.png)

## Technical key reference (optional)

These stored keys help developers and troubleshooting; they are not a task JSON blob to paste into the UI. Automatically validated metadata is not a manual form input.

| Page field/group | Stored keys |
| --- | --- |
| Database resource / Query timeout (seconds) | `resourceId / queryTimeoutSeconds` |
| Check name / Query SQL / Type | `checks[].name / sql / type` |
| Comparison / Expected value / Lower and upper bounds | `compareType / expected / minValue / maxValue` |
| Actual-value variable / Level / Result handlers | `variable / level / handlers` |
