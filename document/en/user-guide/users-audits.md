# Users, permissions and audit

[简体中文](../../zh-CN/user-guide/users-audits.md) · [Manual home](../README.md)

Account roles control page and API actions; runtime roles control server responsibilities. These are independent. This page describes the 1.0.0 role policy. First confirm the [account mode](../operations/accounts.md).

## Role and action matrix

Allowed actions still require applicable object-state and business conditions.

| Action | ADMIN | OPERATOR | VIEWER |
| --- | --- | --- | --- |
| Read summaries and definition lists | Yes | Yes | Yes |
| Create/edit tasks or workflows | Yes | No | No |
| Publish definition versions | Yes | No | No |
| Manually execute | Yes | Yes | No |
| Stop, pause, resume, retry | Yes | Yes | No |
| Decide approvals | Conditional | Conditional | No |
| Manage resources and test connections | Yes | No | No |
| Read sensitive definition details | Yes | No | No |
| Read logs and input/output details | Yes | No | No |
| Publish inbound events | Yes | No | No |
| Import/export configuration packages | Yes | No | No |
| Manage users | DATABASE mode | No | No |
| Read operation audit | Yes | No | No |
| Change own password | Account-mode dependent | Account-mode dependent | Account-mode dependent |

## Why an action is unavailable

| Situation | Condition | Suggested action |
| --- | --- | --- |
| Create/edit/publish unavailable | CONFIGURATION_WRITE is ADMIN-only | Use an administrator for configuration; delegate execution to OPERATOR |
| Execution/control unavailable | EXECUTION_CONTROL and a compatible instance state are required | Use ADMIN/OPERATOR and inspect the current state |
| Approval unavailable | APPROVAL_DECIDE, allowed-operator membership and processable wait state are required | Check the current account is allowed; expired/resolved records are not new approvals |
| Details unavailable | SENSITIVE_READ differs from summary access | Use ADMIN when details are needed; list visibility does not grant field access |
| User management unavailable | STATIC reads configuration; DATABASE supports user administration | Confirm the mode; do not switch modes as a password recovery shortcut |
| Most actions denied after login | Initial password change is required | Complete the current account's password change |

## Secrets and resource configuration

SENSITIVE_READ does not mean arbitrary plaintext secret disclosure. Resource views mask fields or indicate that a value is configured. Editing uses preserve, replace or clear semantics. A hidden existing password is not an empty password. Grant access according to the sensitivity of configuration, scripts and business logs.

## User administration and audit

1. In DATABASE mode, use ADMIN to open System management → Users and create an account with the required role.
2. Retain at least one enabled administrator. Complete initial password changes as prompted.
3. Use a separate session to inspect the account's visible and allowed actions.
4. As ADMIN, open the operation audit and filter by actor, action, target, time and result.

STATIC accounts are maintained through controlled private configuration. NONE/DEVELOPMENT are not production authentication schemes. An audit proves a platform operation, not rollback or completion of an external business transaction.

[Account sources](../operations/accounts.md) · [Approvals](events.md) · [Import/export](import-export.md) · [Core concepts](../concepts.md)
