# Service resources and authentication

[简体中文](../../zh-CN/user-guide/resources-service.md) · [Manual home](../README.md)

## Entry and workflow

Resources → Service resources. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `protocol / endpoint` | Set the HTTP/WebService base endpoint; tasks add relative paths. |
| `authType / headers` | Manage headers for the selected auth type; secret headers have preserve/replace semantics. |
| `oauth2` | Client Credentials: tokenEndpoint, clientId, clientSecret, scopes, audience, auth method and refresh skew. |
| `webService` | WSDL, namespace, serviceName and portName must match the service. |
| `connect/read/write/call Timeout` | Separate connection/read/write/whole-call bounds; follow form units, distinct from task timeout. |

## Use and verify

Start with a dedicated read-only endpoint; keep credentials out of task bodies, URLs and screenshots. OAuth token acquisition does not prove business authorization; remote stop requires its own contract. See [API_CALL](plugins/api-call.md).
