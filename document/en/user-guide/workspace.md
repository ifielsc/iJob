# Workspace, preferences and nodes

[简体中文](../../zh-CN/user-guide/workspace.md) · [Manual home](../README.md)

## Entry and workflow

Workspace/Overview. ADMIN, OPERATOR and VIEWER can read runtime summaries. Configuration changes require ADMIN; execution controls require ADMIN or OPERATOR. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

### Personal display settings

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Language | Initialized | Current personal preference | Header settings | Switch between Simplified Chinese and English without changing business objects. |
| Theme | Initialized | Current personal preference | Header settings | Switch light/dark appearance while preserving editor drafts. |
| Display timezone | Initialized | Current account time mode | Header settings | Changes display only, not scheduling business timezone or query instants. |

### Workbench information

| Display | Meaning | Shown when |
| --- | --- | --- |
| Root runs | Counts roots in the selected window; triggers without roots are excluded. | Always |
| Success rate | Success / (success + failed + timed out); stopped, skipped and unfinished runs are excluded. | Always |
| My pending approvals | Current actionable backlog, independent of the statistics window. | Always |
| This API node | Local roles and scheduler settings do not establish cluster Worker health. Unknown or stale data are not zero. | Always |

### Entry and identity

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Sign in | ADMIN / OPERATOR / VIEWER | Valid installation account; complete a required initial password change | Use current-role permissions; changing a URL does not grant additional access. |
| Tasks | ADMIN / OPERATOR / VIEWER | Definition lists are readable | Start with a single task; creation and editing require ADMIN. |
| Job orchestration | ADMIN / OPERATOR / VIEWER | Definition lists are readable | Open workflows; editing and publication require ADMIN. |
| Run center | ADMIN / OPERATOR / VIEWER | Run summaries are readable | Track triggers and instances. Control requires ADMIN/OPERATOR; logs and variable details require ADMIN. |

## Use and verify

Choose language on first login and review overview/nodes. An available API page without an eligible Worker may accept triggers without executing them. See [Routing](../configuration/routing.md) for node scope.
