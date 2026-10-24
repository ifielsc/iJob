# Workflow nodes, conditions and gateways

[简体中文](../../zh-CN/user-guide/job-nodes.md) · [Manual home](../README.md)

## Entry and workflow

Workflow editor → select node/edge. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `nodeId / name / nodeType / refId` | Stable unique node key, label and TASK/JOB reference. Removing a node affects edges/condition references. |
| `ALL_SUCCESS / ANY_SUCCESS / ALWAYS` | After participating upstreams reach terminal states, require all success, any success or always; SKIPPED counts as success. |
| `STOP / SKIP` | Stop downstream on failure / continue evaluating; continuing preserves the failure result. |
| `routeKey / priority / defaultRoute` | At least 2 routes and exactly 1 default; unique route keys/priorities. Smaller priority is checked first; first match wins. |
| `condition` | Use inputs, direct-upstream outputs and trigger metadata; test missing values with EXISTS. Review advanced JSON after structured editing. |

## Use and verify

Example: amount >100 routes to review, otherwise default finish. Test 101, 100 and a missing value separately. Unselected branches differ from task failure. Revalidate references after renaming keys; a visually correct graph is insufficient. Inspect subjob versions together with the parent snapshot.

## Real page examples

Selected node reference versions, execution condition and failure strategy.

![Selected node reference versions, execution condition and failure strategy.](../images/manual/job-node.png)
