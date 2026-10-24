# Users, permissions and audits

[简体中文](../../zh-CN/user-guide/users-audits.md) · [Manual home](../README.md)

## Entry and workflow

System management → Users/Operation audit. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `ADMIN / OPERATOR / VIEWER` | Business account roles, distinct from API/WORKER node roles; page/API permissions govern actions. |
| `账号状态与密码` | Manage users/status/passwords in DATABASE mode; retain an enabled administrator. STATIC reads private configuration. |
| `审计过滤` | Filter actor/action/target/time/result; sensitive information follows redaction rules. |

## Use and verify

Read [Account modes](../operations/accounts.md) to determine available user management. Create an ordinary test user as administrator, verify scope in a separate session and inspect audits. An audit records platform activity, not external commit/rollback completion.
