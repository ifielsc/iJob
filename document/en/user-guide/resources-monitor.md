# Monitor servers and Agent

[简体中文](../../zh-CN/user-guide/resources-monitor.md) · [Manual home](../README.md)

## Entry and workflow

Resources → Monitor servers. Creating, changing, deleting or testing resources and reading sensitive details require ADMIN. All three roles can read permitted summaries. A disabled resource or one unreachable from the execution node remains unusable by the task. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

See [common resource fields](resources.md#common-resource-fields) for Name, Resource code, Enable and Description, and [common resource actions](resources.md#common-resource-actions) for saving, deletion and secret preservation. Initial values below apply to new resources; editing loads saved non-secret values and keeps existing secrets by default.

### Agent connection

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Protocol (`protocol`) | Initialized | HTTP | Always | HTTP or HTTPS; must match the Agent URL scheme. |
| Agent URL (`endpoint`) | Required | Empty | Always | Up to 2048 characters, with an optional base path. No username, password, query parameters, fragment or surrounding whitespace. |
| Bearer Token (`token`) | Form allows empty; must match when Agent authentication is enabled | Empty on creation; keep existing on edit | Always | Up to 4096 characters, without newline or null characters. A configured Token overrides the Authorization header. Explicit replacement and clearing are supported. |
| Connect timeout (ms) (`connectTimeout`) | Required; initialized | 5000 | Always | Integer 100–300000, in milliseconds. |
| Read timeout (ms) (`readTimeout`) | Required; initialized | 10000 | Always | Integer 100–300000, in milliseconds; the monitoring task collection deadline is configured separately. |

### Custom headers

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Name (`headers` key) | Required per row | No headers; empty in a new row | Custom headers table | Up to 50 rows; valid HTTP header names up to 256 characters, unique ignoring case. |
| Value (`headers` value) | Enter or keep per row | Empty in a new row; keep existing for saved rows | A row exists in the custom headers table | Up to 4096 characters without control characters. Saved values are hidden; delete the whole row to remove a header. |

### Server information

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Operating system (`osType`) | Optional | Not selected | Always | LINUX, WINDOWS or MACOS; may be cleared. Record the actual target OS; this label does not prove Agent installation or compatibility. |
| Agent version (`agentVersion`) | Optional | Empty | Always | Up to 64 characters; record the real version. This is not a substitute for a connection check. |

### Monitoring resource actions

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Add header | ADMIN | Editor writable | Add a Name and Value row; save to apply. |
| Remove | ADMIN | Header row exists and editor writable | Delete the row; saving removes the header. |
| Test saved configuration | ADMIN | Editing an existing enabled resource; no request in progress or unknown write outcome | Ping the Agent from the API node with saved settings without collecting metrics. Draft changes are not tested. |
| Test connection | ADMIN | Resource saved and enabled in the list | Perform the same saved-configuration ping check; this does not prove a monitoring task can collect metrics. |

## Use and verify

Deploy the [independent Agent](../configuration/deployment-agent.md), test connectivity/collection and check metric timestamps/status before [SERVER_MONITOR](plugins/server-monitor.md) rules. The Agent is not a scheduling Worker.
