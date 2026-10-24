# API calls

[简体中文](../../../zh-CN/user-guide/plugins/api-call.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `API_CALL`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Field/action | Purpose and rules |
| --- | --- |
| `resourceId / path / method` | The service supplies base address/authentication; set relative path and method. Default POST; explicitly choose GET for a read example. |
| `headers / body / webService` | Set protocol-appropriate headers/body or WebService fields; preserve/replace stored secret headers deliberately. |
| `maxResponseBytes` | Defaults to 1 MiB and bounds response reads; do not raise without limits for large files. |
| `extractors / successCondition` | Extractors use variable and JSONPath expression. Review business success conditions separately from HTTP status. |
| `remoteStop` | Configure only when the service implements an explicit remote cancellation protocol; stopping local waiting does not guarantee remote cancellation. |

## Minimal example and verification

```json
{"path":"/orders/summary","method":"GET","extractors":[{"variable":"total","expression":"$.total"}]}
```

Add a real service resource ID to this fragment. Test connectivity, call a read-only endpoint and check response, extracted variables and business condition. Connectivity is not business success. A timeout can occur after remote commit; use idempotency or query the result before retrying.

Snippets require your resource IDs/paths. See the [case index](../../examples.md) for actual external-protocol verification status.
