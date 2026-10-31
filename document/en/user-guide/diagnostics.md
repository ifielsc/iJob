# Runtime diagnostics and health

[简体中文](../../zh-CN/user-guide/diagnostics.md) · [Manual home](../README.md)

## Entry and workflow

Diagnostics; workspace node details. All three roles can read diagnostic summaries. Diagnostic switches are deployment settings; viewing this page does not grant server configuration access. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

Runtime diagnostics and node status are separate pages. Diagnostic switches are deployment configuration; viewing these pages does not grant server-configuration access.

### Runtime diagnostic information

| Display | Meaning | Shown when |
| --- | --- | --- |
| Expired leases | A work item remains CLAIMED and its lease deadline is at/before database observation time. | Always |
| Queue delays | READY/RETRY_WAIT items exceed the configured wait threshold; a match does not establish an eligible Worker exists. | Always |
| Root deadlines | A root exceeds its deadline while nonterminal; children and terminal runs are excluded. | Always |
| Unsettled commands | A command expired while still PENDING/PROCESSING. | Always |
| Observed condition | Correlate state, target and time; this alone does not prove Worker failure. | Always |
| Node and component state | On node/runtime status pages inspect roles, online state and sample freshness; unknown is not zero. | Node/runtime status pages |
| Queue and capacity | Interpret available waiting, in-flight, thread and connection metrics; missing data do not mean no load. | Node/runtime status pages |

### Actions

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Refresh checks | ADMIN / OPERATOR / VIEWER | Summary access and valid check conditions | Read current diagnostic facts without automatically stopping or retrying tasks. |
| View evidence | ADMIN / OPERATOR / VIEWER | The check contains a matching record | Inspect timestamps and related objects; open run details for logs, which require ADMIN. |
| View root run | ADMIN / OPERATOR / VIEWER | Evidence includes a root-instance reference | Verify live state and changes; a diagnostic snapshot does not replace the run outcome. |

## Use and verify

Establish a baseline with docs-hello, then inspect the intentional failure. Diagnostics support investigation but do not replace target-system verification. Follow [Troubleshooting](../operations/troubleshooting.md) after database recovery/node changes.
