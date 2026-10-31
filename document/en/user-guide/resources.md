# Resources and secret fields

[简体中文](../../zh-CN/user-guide/resources.md) · [Manual home](../README.md)

## Entry and workflow

Resources → resource type. Creating, changing, deleting or testing resources and reading sensitive details require ADMIN. All three roles can read permitted summaries. A disabled resource or one unreachable from the execution node remains unusable by the task. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

Connection fields are documented by type: [database](resources-database.md), [service](resources-service.md), [file](resources-file.md), [SSH](resources-ssh.md), [messaging](resources-messaging.md) and [monitoring](resources-monitor.md). Initial values describe a new resource form, not defaults on the target service.

### Common resource fields

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Name (`name`) | Required | Empty | Always | Up to 128 characters, used in lists, searches and task selectors. |
| Resource code (`code`) | Required | Generated as `res-` plus a UUID; editable | Always | Unique; up to 128 letters, digits, dots, underscores or hyphens. Tasks store resource ID references. |
| Enable (`enabled`) | Initialized | On | Always | A disabled resource is unavailable for execution and list connection tests. Save to apply changes. |
| Description (`description`) | Optional | Empty | Always; advanced settings for database and service resources | Up to 500 characters describing purpose and environment, without secrets. |

### Common resource actions

The following permissions apply to built-in roles. The account must also be available and any required password change completed. While a request is running or a write outcome is unknown, check its result before continuing.

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Search | ADMIN, OPERATOR, VIEWER | Resource summary access is available | Filter by name and enabled state without returning secret plaintext. |
| View details | ADMIN | Resource exists and sensitive-detail permission is available | Show configuration and whether secrets are configured, without revealing saved secrets. |
| New resource | ADMIN | Resource configuration can be written | Open a new form; this neither saves nor connects to the target. |
| Edit | ADMIN | Resource exists and can be written | Load the form, keeping saved secrets by default. |
| Save | ADMIN | Required fields, formats and authentication constraints are valid; an existing resource has changes | Create or update the resource without running tasks or automatically testing the connection. |
| Delete | ADMIN | Confirmed and the resource is not referenced | Delete the resource; references prevent deletion. |
| Delete selected | ADMIN | Resources selected and confirmed; list is not loading or in error | Attempt each deletion and report successes and failures separately. |
| Test connection | ADMIN | Resource is saved and enabled | Access the target from the current API node; see type-specific checks below. Success does not prove Worker reachability or all business read/write privileges. |
| Keep existing | ADMIN | Editing an existing secret field | Preserve its stored value without re-entering it. |
| Replace | ADMIN | Field is editable and the replacement is valid | Replace it on save; the database password control is labeled “Change password”. |
| Clear | ADMIN | A Clear control is available and authentication remains valid | Explicitly remove the value on save; a blank display or empty replacement is not a clear operation. |
| Show entered password | ADMIN | Entering a secret with a visibility toggle | Show only the current input, never the saved secret. |

Required secrets, including the RabbitMQ password, S3 access credentials and OAuth client secret, cannot be cleared directly. SSH and SFTP must retain a password or private key file path; a key passphrase requires a key path. Remove a header by deleting its row; a blank display does not remove it.

## Use and verify

Create an independent resource before selecting it in tasks. Shared endpoint changes/disabling affect all consumers; inspect references first. See database, SSH, service, file, messaging and monitor chapters. Exports exclude credentials; fill them in the target environment.
