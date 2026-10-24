# Node roles, groups and eligible targets

[简体中文](../../zh-CN/configuration/routing.md) · [Manual home](../README.md)

## Location and matching order

Set node values in application.properties and restart. Set task/job groups on the definition and follow publication semantics. Use distinct node names for traceability. API/SCHEDULER/WORKER/MAINTENANCE divide responsibilities; execution claims require WORKER and other required roles must remain covered.

```properties
app.runtime.instance-name=worker-finance-a
app.runtime.groups=finance,nightly
app.runtime.allowed-task-ids=101,102
app.runtime.allowed-job-ids=201
```

Replace illustrative IDs with actual IDs. Groups are trimmed, lowercased and deduplicated: at most 16, each 1–64 characters matching `[a-z0-9][a-z0-9._-]{0,63}`. IDs are positive integers with at most 100 distinct values in each list.

| Condition | Match |
| --- | --- |
| Definition has no groups | No node-group restriction |
| Definition has groups | At least one intersection with node groups |
| Node has no groups | Only unrestricted definitions match |
| Both ID lists empty | No root-target ID restriction |
| Only task list populated | Listed standalone task roots only; no job roots |
| Only job list populated | Listed job roots only; no standalone task roots |
| Both populated | Match the list for the root type, and also satisfy groups |

This controls work claims, not login authorization or a script sandbox. It does not individually authorize every descendant task in a job. Verify with harmless matching and nonmatching tasks, examining trigger/queue state and executing node; saving alone is insufficient. Ensure pending work retains an eligible Worker before changing routing.

## Settings in this topic

These are source/implementation defaults; see the [complete catalog](../configuration-catalog.md) for setup overrides and provenance.

| ID / key | Level / type | Source defaults | Explanation |
| --- | --- | --- | --- |
| C036 / `app.runtime.instance-name` | A / text | ${IJOB_RUNTIME_INSTANCE_NAME:} | Node identity; use distinct names in a cluster for traceability. |
| C037 / `app.runtime.roles` | B / enum-list | ${IJOB_RUNTIME_ROLES:API,SCHEDULER,WORKER,MAINTENANCE} | API/SCHEDULER/WORKER/MAINTENANCE responsibilities; retain coverage of required roles. |
| C038 / `app.runtime.groups` | A / text-list | ${IJOB_RUNTIME_GROUPS:} | Worker groups match published definitions; empty worker groups do not mean all groups. |
| C039 / `app.runtime.allowed-task-ids` | A / id-list | ${IJOB_RUNTIME_ALLOWED_TASK_IDS:} | Task IDs eligible as root trigger targets; jointly restrict scope with the job allowlist. |
| C040 / `app.runtime.allowed-job-ids` | A / id-list | ${IJOB_RUNTIME_ALLOWED_JOB_IDS:} | Job IDs eligible as root trigger targets; not an individual authorization list for every descendant task. |
