# Business concurrency control

[简体中文](../../zh-CN/user-guide/concurrency.md) · [Manual home](../README.md)

## Entry and workflow

Concurrency; task concurrency policy. ADMIN can create, edit and delete configuration. OPERATOR and VIEWER can read summaries but cannot modify configuration. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

### Concurrency fields

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Name | Required | Empty | Concurrency resource | Resource display name, up to 128 characters. |
| Resource code | Required | Generated; editable | Concurrency resource | Unique code using letters, numbers, dots, underscores and hyphens, up to 128 characters. |
| Maximum concurrency | Required | Empty | Resource/plugin limits, or a task limit is enabled | Integer from 1 to 10000; all configured layers apply together. |
| Enabled | Initialized | On | Concurrency resource | Disabling prevents bound tasks from acquiring permits. |
| Description | Optional | Empty | Concurrency resource | Purpose, up to 500 characters. |
| Task limit mode | Initialized | No task limit when unconfigured | Task concurrency policy | Removes only the task cap; plugin and shared-resource limits still apply. |
| Shared concurrency resources | Optional | None bound | Task concurrency policy | Every selected resource must grant a permit. |

### Page information

| Display | Meaning | Shown when |
| --- | --- | --- |
| Active instances | Includes waiting, running, paused and event-waiting instances; not permit occupancy. | Always |
| Policy version | Used to check concurrent edits on save; conflicts require reviewing current values. | Always |

### Actions

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Save | ADMIN | Valid fields, matching version and passing active-instance/permit checks | Change concurrency configuration without publishing a task. Active resources cannot have limits lowered or be disabled. |
| Remove limit | ADMIN | No active instances or unreleased permits for the plugin policy; version check passes | Remove only the plugin-level limit. |

## Use and verify

Bind two test tasks to one resource with capacity 1 and observe sequential execution after waiting. Disabling a referenced resource blocks permits. Active work restricts lowering limits/policy changes. For uncertain writes, read current state and explicitly reapply before saving.
