# Event approvals and records

[简体中文](../../zh-CN/user-guide/events.md) · [Manual home](../README.md)

## Entry and workflow

Approvals. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `待处理/已处理` | Locate waits by state, business association and time; check the source instance first. |
| `允许操作人` | Only identities satisfying permissions and allowedOperatorIds may act. |
| `批准/拒绝与说明` | Actions affect real waits/downstream dependencies; record a reason and verify the terminal state. |

## Use and verify

Use the [event case](../examples/events-and-approval.md) to establish a dedicated wait, confirm its record, then act and observe resumption. Duplicate or expired decisions are not new successes. Inputs/records are permission-controlled.
