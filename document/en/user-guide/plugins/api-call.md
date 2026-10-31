# API calls

[简体中文](../../../zh-CN/user-guide/plugins/api-call.md) · [Manual](../../README.md)

Applies to **1.0.0**. Tasks → New/Edit → plugin `API_CALL`.

## Before you start

Use ADMIN to create, configure and save tasks. ADMIN or OPERATOR can execute published definitions; ADMIN reads detailed logs and input/output variables. Resources must be enabled and reachable from the execution node. See [tasks](../tasks.md) for common timeout, retry and version settings, and the [permission matrix](../users-audits.md).

## Field reference

“Default” means the **initial value of a new 1.0.0 form**. An empty value means the field has not been filled in; required fields still need a value. Visibility follows the selected branch; conditional requirements apply only to that branch.

### Request

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Service resource | Required | None | Always | Current task version |
| Request method | Has a default | POST | HTTP | Current task version |
| Request path (optional) | Optional | Empty | HTTP | Current task version |
| Request headers | Optional | Empty | Always | Current task version |
| Request body (JSON) | Optional | Empty | HTTP except GET/HEAD | Current task version |

- **Service resource**: Protocol, authentication and connection timeout come from the resource.
- **Request method**: GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS。
- **Request path (optional)**: Relative to the service address; blank uses the resource address. Put query parameters in the path.
- **Request headers**: At most 100; follow the form's preserve/replace behavior for existing sensitive headers.
- **Request body (JSON)**: A JSON object, array or scalar; GET/HEAD do not send a body.

### SOAP

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Operation | Conditionally required | Empty | SOAP | Current task version |
| SOAPAction | Optional | Empty | SOAP | Current task version |
| Parameters | Optional | Empty | SOAP | Current task version |

- **Operation**: SOAP 1.1，POST。
- **SOAPAction**: SOAPAction。
- **Parameters**: Named parameters; the client generates XML.

### Response

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Maximum response (bytes) | Has a default | 1048576 | Advanced settings | Current task version |
| Business success condition | Optional | Disabled | HTTP JSON responses | Current task version |
| JSONPath | Conditionally required | Empty | Business success condition enabled | Current task version |
| Operator | Has a default | EQ | Business success condition enabled | Current task version |
| Expected value | Conditionally required | Empty | Business success condition enabled | Current task version |
| Variable | Conditionally required | Empty | Response variable extraction added | Current task version |
| JSONPath | Conditionally required | Empty | Response variable extraction added | Current task version |

- **Maximum response (bytes)**: 1024–16777216 bytes.
- **Business success condition**: The business condition must pass even after an HTTP 2xx response.
- **JSONPath**: JSONPath。
- **Operator**: EQ/NE/GT/LT。
- **Expected value**: Compared with the extracted value.
- **Variable**: Output variable name; at most 64 extractors.
- **JSONPath**: JSONPath。

### Remote stop

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Remote stop (optional) | Optional | Disabled | Advanced settings | Current task version |
| Stop request path | Conditionally required | Empty | HTTP remote stop enabled | Current task version |
| Stop request deadline (seconds) | Has a default | 5 | Remote stop enabled | Current task version |
| Remote stop request body | Optional | Empty | HTTP remote stop enabled | Current task version |

- **Remote stop (optional)**: Use the same protocol as the main request and share its business-operation identifier.
- **Stop request path**: Relative path; no automatic retries.
- **Stop request deadline (seconds)**: 1–30 seconds.
- **Remote stop request body**: Provide values required by the remote cancellation protocol.

## Behavior and boundaries

- Plain-text interpolation produces text; a standalone JSON placeholder can preserve its value type.
- HTTP 503 from the main call does not trigger automatic plugin retries.
- Acceptance of a remote stop request does not guarantee business-operation termination or rollback.

## Typical configuration: Extract an order-summary API response

Fill in the form below, replacing resource names with your enabled resources. This is a configuration guide, not importable JSON. Follow the tutorial linked below for data preparation and execution.

Start the HTTP sample from the linked tutorial and make it reachable from the Worker; /orders/summary defaults to total=3 and amount=600.

| Setting | Value |
| --- | --- |
| Service resource | HTTP service resource started in the tutorial |
| Request method | GET |
| Request path | /orders/summary |
| Response variable 1 | Variable total; JSONPath $.total |
| Response variable 2 | Variable amount; JSONPath $.amount |

**Expected result:** The API returns {"total":3,"amount":600}; output variables are total=3 and amount=600.

[Follow the full tutorial](../../examples/api-and-parameters.md). Save the definition before running it; inspect execution state and business results separately.

## Configuration keys

Use these keys when inspecting imported configuration, API payloads or errors. `[]` denotes a collection item; the form does not require you to enter key names.

| Category / field | Configuration key |
| --- | --- |
| Request / Service resource | `resourceId` |
| Request / Request method | `method` |
| Request / Request path (optional) | `path` |
| Request / Request headers | `headers` |
| Request / Request body (JSON) | `body` |
| SOAP / Operation | `webService.operation` |
| SOAP / SOAPAction | `webService.action` |
| SOAP / Parameters | `webService.parameters` |
| Response / Maximum response (bytes) | `maxResponseBytes` |
| Response / Business success condition | `successCondition` |
| Response / JSONPath | `successCondition.expression` |
| Response / Operator | `successCondition.operator` |
| Response / Expected value | `successCondition.expected` |
| Response / Variable | `extractors[].variable` |
| Response / JSONPath | `extractors[].expression` |
| Remote stop / Remote stop (optional) | `remoteStop` |
| Remote stop / Stop request path | `remoteStop.path` |
| Remote stop / Stop request deadline (seconds) | `remoteStop.timeoutSeconds` |
| Remote stop / Remote stop request body | `remoteStop.body` |

## Related pages

[Tasks and versions](../tasks.md) · [Parameters and expressions](../../reference/variables.md) · [Scenario tutorials](../../examples.md)
