# Glossary

[简体中文](../zh-CN/glossary.md) · [Manual home](README.md)

This glossary applies to 1.0.0. Read [core concepts](concepts.md) first for the relationships.

| Term | Technical name | Meaning and distinction |
| --- | --- | --- |
| Task | Task | An action executed by one plugin, not an entire workflow |
| Job / workflow | Job / Workflow | A graph of nodes and dependencies; both names refer to the orchestration object |
| Subjob | Subjob | A workflow version referenced by a parent, not a twelfth plugin |
| Workflow node | Job node | A task, subjob or gateway in a graph, not a server |
| Runtime node | Runtime node | A process with API, scheduling, execution or maintenance roles |
| Gateway | Gateway | Controls dependency or conditional progression |
| Definition version | Definition version | A published configuration snapshot, distinct from product 1.0.0 |
| Publish | Publish | Produce a definition version; saving a task publishes without running |
| Trigger | Trigger | An execution request record, not proof of success |
| Instance | Instance | Identity and state of an execution |
| Root instance | Root instance | Root record connecting execution of a flow |
| Schedule | Schedule | Planned triggering with time zones, calendars and misfire rules |
| Business date | Business date | Business meaning of a trigger's date, not necessarily today's machine date |
| Parameter template | Parameter template | Input types, defaults and constraints, not input values |
| Input snapshot | Input snapshot | Inputs retained for a particular trigger/run |
| Output variable | Output variable | A plugin-produced value available to downstream nodes |
| Built-in variable | Built-in variable | Platform context using the reserved ijob.* prefix |
| Resource | Resource | Connection and access settings for business systems |
| Platform database | Platform database | Stores definitions, history, configuration and coordination |
| Business database | Business database | A source or target accessed by SQL, ETL and other plugins |
| Execution group | Execution group | Task/Worker routing criteria, not account roles |
| Concurrency policy | Concurrency policy | Limits shared execution capacity without publishing a definition |
| Checkpoint / watermark | Checkpoint / watermark | Incremental extraction progress, not a substitute for target idempotency |
| Idempotency | Idempotency | Repeating the same business operation avoids duplicate business effects |
| Retry | Retry | Another execution attempt, not rollback of prior external effects |
| Approval | Approval | An EVENT wait action resolved by allowed accounts |
| Readiness | Readiness | A node can accept relevant work; not every business resource is necessarily healthy |

## Related references

[Variables and expressions](reference/variables.md) · [Nodes and gateways](user-guide/job-nodes.md) · [Schedules](user-guide/schedules.md) · [Account permissions](user-guide/users-audits.md)
