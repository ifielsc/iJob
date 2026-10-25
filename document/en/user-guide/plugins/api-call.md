# API calls

[简体中文](../../../zh-CN/user-guide/plugins/api-call.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `API_CALL`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Page field/action | Purpose and rules |
| --- | --- |
| Service resource / Request path / Method | The service supplies base address/authentication; set relative path and method. Default POST; explicitly choose GET for a read example. |
| Headers / Request body / SOAP settings | Set protocol-appropriate headers/body or WebService fields; preserve/replace stored secret headers deliberately. |
| Response limit (bytes) | Defaults to 1 MiB and bounds response reads; do not raise without limits for large files. |
| Response variable extraction / Business success condition | Extractors use variable and JSONPath expression. Review business success conditions separately from HTTP status. |
| Remote stop (optional) | Configure only when the service implements an explicit remote cancellation protocol; stopping local waiting does not guarantee remote cancellation. |

## Minimal example and verification

Prepare the dedicated resources above, fill the form, save, then trigger manually and verify the instance, outputs and target system as described below. Saving alone does not execute.

| Setting | Value to enter |
| --- | --- |
| Service resource | Select a dedicated test HTTP service |
| Request path | `/orders/summary` |
| Method | GET |
| Request body | Leave empty |
| Response extraction → Variable | `total` |
| Response extraction → JSONPath | `$.total` |

Select the actual test service resource in the form. Test connectivity, call a read-only endpoint and check response, extracted variables and business condition. Connectivity is not business success. A timeout can occur after remote commit; use idempotency or query the result before retrying.

Replace example resources, paths and object names with values from your environment. See the [case index](../../examples.md) for actual external-protocol verification status.

## Technical key reference (optional)

These stored keys help developers and troubleshooting; they are not a task JSON blob to paste into the UI. Automatically validated metadata is not a manual form input.

| Page field/group | Stored keys |
| --- | --- |
| Service resource / Request path / Method | `resourceId / path / method` |
| Headers / Request body / SOAP settings | `headers / body / webService` |
| Response limit (bytes) | `maxResponseBytes` |
| Response variable extraction / Business success condition | `extractors / successCondition` |
| Remote stop (optional) | `remoteStop` |
