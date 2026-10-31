# Event approvals and records

[简体中文](../../zh-CN/user-guide/events.md) · [Manual home](../README.md)

## Entry and workflow

Approvals. Approval requires ADMIN or OPERATOR, membership in the allowed-approver list, and an unprocessed, unexpired wait record. VIEWER cannot approve. Event publication and binding configuration require ADMIN. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

Allowed approvers are maintained in the EVENT task’s approval-wait configuration, not edited on this page. Ordinary event waits have no manual approval actions.

### Approval information

| Display | Meaning | Shown when |
| --- | --- | --- |
| Status | Waiting, approved, rejected, withdrawn and timed-out records have different outcomes. | Always |
| Instance ID | Identify the approval node; verify its origin before acting. | Always |
| Root instance ID | Identify the complete root business run. | Always |
| Wait started | Time when the node started waiting. | Always |
| Deadline | The backend determines expiry and final status; an unset deadline is shown as No limit. | Always |

### Decision field

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Comment (optional) | Optional | Empty | Actionable approval details | Up to 500 characters explaining the decision. |

### Actions

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Approve | ADMIN / OPERATOR | Account is an allowed approver; record is still waiting and unexpired | The approval node succeeds; downstream work follows job policy. |
| Reject | ADMIN / OPERATOR | Account is an allowed approver; record is still waiting and unexpired | The approval node fails; downstream work follows job policy. |
| Refresh status | ADMIN / OPERATOR | The account can access this record | Verify the actual backend outcome before repeating an uncertain decision. |

## Use and verify

Use the [event case](../examples/events-and-approval.md) to establish a dedicated wait, confirm its record, then act and observe resumption. Duplicate or expired decisions are not new successes. Inputs/records are permission-controlled.
