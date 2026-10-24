# Mail channels, rules and delivery records

[简体中文](../../zh-CN/user-guide/notifications.md) · [Manual home](../README.md)

## Entry and workflow

Notification center → Mail channels/Notifications. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `邮件通道` | Channels own SMTP host/port/authentication/TLS and protected credentials. |
| `通知规则与模板` | Select channel, recipients, event conditions and template/language; preview rendering first. |
| `任务/作业绑定` | Bindings select which run events use a rule, separate from PUSH tasks. |
| `发送记录` | Distinguish queued, failed, retried and successful sends; success is bounded by channel response semantics. |

## Use and verify

Use a dedicated test mailbox or local receiver. Preview does not send, and saved configuration does not prove delivery. Without receiver evidence, report configuration/preview verification only. Check channel switch, rule enablement, bindings and errors before retrying.
