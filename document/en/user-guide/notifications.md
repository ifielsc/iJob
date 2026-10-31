# Mail channels, rules and delivery records

[简体中文](../../zh-CN/user-guide/notifications.md) · [Manual home](../README.md)

## Entry and workflow

Notification center → Mail channels/Notifications. ADMIN manages mail channels and notification configuration and reads delivery details. OPERATOR and VIEWER lack configuration-write and sensitive-detail permissions, so these actions are unavailable. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

There is no separate notification-template/language selector on this page. Preview uses backend notification language, timezone and actual templates. Bot credentials use retain, replace and clear semantics.

### Mail channel fields

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Channel name | Required | Empty | Always | Name used when selecting a channel in notification configuration. |
| SMTP server | Required | Empty | Always | SMTP host supplied by the mail provider. |
| Port | Required | 465 | Always | 1–65535; must match the selected encryption mode. |
| Encryption | Initialized | SSL / TLS | Always | Choose None, STARTTLS or SSL / TLS. |
| SMTP authentication | Initialized | On | Always | Whether to authenticate with username and authorization code. |
| Login email / username | Required | Empty | SMTP authentication enabled | SMTP login account. |
| Authorization code / app password | Required when creating or changing host/account | Empty | SMTP authentication enabled | Stored value is not returned. Leave empty to retain when host/account are unchanged, or supply a replacement. |
| Default sender address | Required | Empty | Always | Valid sender email address. |
| Enabled | Initialized | On | Always | Disabling the channel also prevents queued notifications using it from being sent. |

### Notification configuration and binding

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Name | Required | Empty | Notification configuration | Business-readable configuration name. |
| Type | Required | Email | Notification configuration | Choose the actual channel; bot types expose corresponding endpoint and mention fields. |
| Mail channel | Required | Empty | Type is Email | Reference a configured sender channel instead of copying SMTP credentials here. |
| To | Required | Empty | Type is Email | One address per line or comma-separated; addresses must be valid and unique. |
| Cc | Optional | Empty | Type is Email | Must not duplicate recipients. |
| Notification config | Required | Empty | Task/job notification binding | Select an existing configuration; a binding is not a PUSH task. |
| Policy | Required | On failure | Task/job notification binding | Never, On success, On failure or Always; failure includes timeout. |

### Actions and outcomes

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Check connection | ADMIN | Mail channel is saved | Connect to SMTP and verify authentication without sending mail. |
| Preview content | ADMIN | Notification draft is valid and preview endpoint available | Render a sample using real sending templates without sending messages; language and timezone follow backend notification settings. |
| Save | ADMIN | Configuration/binding valid; no uncertain write outcome remains | Save configuration or binding without sending immediately; bindings are independent of definition versions. |
| Send records | ADMIN | Sensitive delivery details are readable | Distinguish pending, failed, retrying and sent deliveries. Channel acceptance does not mean the recipient read the message. |

## Use and verify

Use a dedicated test mailbox or local receiver. Preview does not send, and saved configuration does not prove delivery. Without receiver evidence, report configuration/preview verification only. Check channel switch, rule enablement, bindings and errors before retrying.
