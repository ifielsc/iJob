# Data checks

[简体中文](../../../zh-CN/user-guide/plugins/data-check.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `DATA_CHECK`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Field/action | Purpose and rules |
| --- | --- |
| `resourceId / queryTimeoutSeconds` | Business DB resource; per-query timeout defaults to 30 seconds. |
| `checks[].name / sql / type` | Give each check a name/query; type NORMAL/COUNT/EXISTS/EMPTY selects result semantics. |
| `compareType / expected / minValue / maxValue` | EQ/NE/GT/GE/LT/LE/BETWEEN; use expected for ordinary comparisons and bounds for ranges, preserving numeric types. |
| `variable / level / handlers` | Output variable, severity (default ERROR) and handlers; warnings do not repair data. |

## Minimal example and verification

```json
{"checks":[{"name":"three-orders","sql":"SELECT COUNT(*) FROM orders","type":"NORMAL","compareType":"EQ","expected":3,"level":"ERROR"}]}
```

Add the DB resource and run against three rows, then use an independent two-row copy to verify failure. Inspect individual checks and overall status. Do not confuse COUNT semantics with SQL COUNT(*); the scalar example uses NORMAL. Checking detects problems and does not repair data unless an explicit handler does so.

Snippets require your resource IDs/paths. See the [case index](../../examples.md) for actual external-protocol verification status.

## Real page examples

NORMAL value comparison with ERROR severity; comparison values are further down the form.

![NORMAL value comparison with ERROR severity; comparison values are further down the form.](../../images/manual/check-editor.png)
