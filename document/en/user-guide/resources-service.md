# Service resources and authentication

[简体中文](../../zh-CN/user-guide/resources-service.md) · [Manual home](../README.md)

## Entry and workflow

Resources → Service resources. Creating, changing, deleting or testing resources and reading sensitive details require ADMIN. All three roles can read permitted summaries. A disabled resource or one unreachable from the execution node remains unusable by the task. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

See [common resource fields](resources.md#common-resource-fields) for Name, Resource code, Enable and Description, and [common resource actions](resources.md#common-resource-actions) for saving, deletion and secret preservation. Initial values below apply to new resources; editing loads saved non-secret values and keeps existing secrets by default.

### Connection and authentication

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Protocol (`protocol`) | Required | HTTP | Always | HTTP or SOAP; SOAP uses the technical value WEB_SERVICE. |
| Service address (`endpoint`) | Required | Empty | Always | Full HTTP(S) URL, up to 2048 characters, without URL credentials or sensitive query parameters. HTTP tasks configure their paths against this address. |
| Authentication (`authType`) | Initialized | No separate authentication (NONE) | HTTP | May select OAuth 2.0 client credentials (OAUTH2_CLIENT_CREDENTIALS). This selector is not shown for SOAP. |

### OAuth 2.0 client credentials

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Token endpoint (`oauth2.tokenEndpoint`) | Required | Empty | HTTP with OAuth 2.0 client credentials | Full HTTP(S) URL, up to 2048 characters; the service-address restrictions on secret parameters also apply. |
| Client ID (`oauth2.clientId`) | Required | Empty | HTTP with OAuth 2.0 client credentials | Up to 512 characters, provided by the authorization service. |
| Client secret (`oauth2.clientSecret`) | Required; existing value may be kept | Empty on creation; keep existing on edit | HTTP with OAuth 2.0 client credentials | Up to 4096 characters. The old value is hidden; enter the complete new secret when replacing it. |
| Scopes (`oauth2.scopes`) | Optional | Empty | OAuth options expanded | Scopes required by the authorization service, up to 2048 characters. |
| Audience (`oauth2.audience`) | Optional | Empty | OAuth options expanded | Audience required by the authorization service, up to 2048 characters. |
| Client authentication (`oauth2.clientAuthenticationMethod`) | Initialized | CLIENT_SECRET_BASIC | OAuth options expanded | CLIENT_SECRET_BASIC or CLIENT_SECRET_POST; match the Token service. |
| Refresh skew (seconds) (`oauth2.refreshSkewSeconds`) | Required; initialized | 30 | OAuth options expanded | Integer 0–300; refresh before the Token expires by this amount. |

### SOAP service description

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| WSDL address (`webService.wsdl`) | Required | Empty | SOAP | Full HTTP(S) URL pointing to the real service WSDL. |
| Namespace (`webService.namespace`) | Required | Empty | SOAP | Match the service namespace in the WSDL. |
| Service name (`webService.serviceName`) | Required | Empty | SOAP | Match the WSDL service name. |
| Port name (`webService.portName`) | Required | Empty | SOAP | Match the WSDL port name; this is not a TCP port number. |

### Default headers

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Name (`headers` key) | Required per row | No headers; empty in a new row | Default headers table for HTTP and SOAP | Up to 50 rows; valid HTTP names up to 128 characters, unique ignoring case. OAuth mode does not allow a manually configured Authorization header. |
| Value (`headers` value) | Enter or keep per row | Empty in a new row; keep existing for saved rows | A row exists in the default headers table | Up to 4096 characters without control characters; saved values remain hidden. Delete the entire row to remove a header. |

### Timeouts

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Connect timeout (seconds) (`connectTimeout`) | Required; initialized | 10 | Advanced settings expanded | Integer 1–300; bounds connection establishment. |
| Read timeout (seconds) (`readTimeout`) | Required; initialized | 30 | Advanced settings expanded | Integer 1–300; bounds read waits. |
| Write timeout (seconds) (`writeTimeout`) | Required; initialized | 30 | Advanced settings expanded | Integer 1–300; bounds write waits. |
| Call timeout (seconds) (`callTimeout`) | Required; initialized | 60 | Advanced settings expanded | Integer 1–900; bounds the whole call, separately from the task timeout. |

### Service resource actions

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Add header | ADMIN | Editor is writable | Add a Name and Value row; save to apply. |
| Remove | ADMIN | Header row exists and editor is writable | Delete the row; saving removes that header. |
| Test connection | ADMIN | Resource saved and enabled | HTTP sends HEAD and may obtain an OAuth Token; SOAP reads the WSDL. A response does not prove authentication or business success. |

## Use and verify

Start with a dedicated read-only endpoint; keep credentials out of task bodies, URLs and screenshots. OAuth token acquisition does not prove business authorization; remote stop requires its own contract. See [API_CALL](plugins/api-call.md).
