# Core concepts and execution lifecycle

[简体中文](../zh-CN/concepts.md) · [Manual home](README.md)

Separate the definition of work from a particular execution. In iJob 1.0.0, saving a task publishes a version; workflows use Save and publish. Neither action starts execution.

## From configuration to results

<div class="lifecycle-model" role="group" aria-label="Concept model and execution lifecycle"><div class="life-heading"><span class="life-tag">DEFINE</span><strong>Define what to do</strong><small>Save / Publish ≠ Execute</small></div><div class="definition-lane"><div class="definition-card"><span class="life-index">01</span><h3>Resources & parameters</h3><small>Connections · Input contracts</small><p>Supply access and input definitions. A task may need no external resource.</p></div><div class="definition-card"><span class="life-index">02</span><h3>Task definition & version</h3><small>Configure plugin → Save & publish</small><p>Saving a task publishes its definition; it does not run the task.</p></div><div class="definition-card"><span class="life-index">03</span><h3>Workflow & publication</h3><small>Dependencies · Gateways · Child jobs</small><p>Publication fixes the referenced task and child-job versions.</p></div></div><div class="life-bridge"><span>↓</span>Published definition + Run inputs</div><div class="life-heading"><span class="life-tag">RUN</span><strong>Trace what this run did</strong><small>Select a stage for details</small></div><div class="execution-lane"><button type="button" class="life-node" data-life-step="0" aria-pressed="true" data-title="Record an execution request" data-detail="A trigger records its source, time and inputs. Enabling a schedule permits future triggers; accepting a trigger does not mean execution has started."><span>01</span><strong>Trigger</strong><small>Schedule / Manual / Event binding</small></button><button type="button" class="life-node" data-life-step="1" aria-pressed="false" data-title="From definition to this particular run" data-detail="An instance is created or advanced when execution conditions allow. The root links its task and child instances; concurrency, routing or resources may cause waiting."><span>02</span><strong>Instance</strong><small>Identity and state of this run</small></button><button type="button" class="life-node" data-life-step="2" aria-pressed="false" data-title="Progress follows the graph" data-detail="Upstream results and gateway conditions select the next path. Events or approvals may pause an existing instance and later resume it; failure policies govern downstream handling."><span>03</span><strong>Execute nodes</strong><small>Dependencies / Branches / Waiting</small></button><button type="button" class="life-node" data-life-step="3" aria-pressed="false" data-title="Connect states to actual actions" data-detail="Logs, input snapshots and node outputs are available throughout the run, not only at completion. Locate a node in the graph, then inspect its logs and variables."><span>04</span><strong>Logs & variables</strong><small>Available throughout execution</small></button><button type="button" class="life-node" data-life-step="4" aria-pressed="false" data-title="Verify the target result after SUCCESS" data-detail="SUCCESS is the platform’s outcome under plugin and workflow rules. Also verify target rows, amounts, files or receipts. Stopping or failing does not automatically undo external writes."><span>05</span><strong>Verify results</strong><small>Files / Data / External receipts</small></button></div><div class="life-detail" role="status"><strong>Record an execution request</strong><p>A trigger records its source, time and inputs. Enabling a schedule permits future triggers; accepting a trigger does not mean execution has started.</p></div><div class="life-foot">Resources are referenced as needed · Versions fix definitions · Logs span the run · Results need business checks</div></div>

Tasks reference resources when needed; a task may require no external resource. Publishing a workflow fixes its task and subjob version references. A trigger records its source, time and inputs. Execution also depends on scheduling, concurrency, routing, resources and instance state.

## How the concepts relate

| Concept | Meaning in iJob | Example |
| --- | --- | --- |
| Resource | Connection and access configuration used by tasks | Business PostgreSQL, SSH host, HTTP service |
| Task | One plugin configured for an execution action | Query orders, call an API, run a script |
| Job / workflow | Tasks and subjobs connected by dependencies and gateways | Extract orders → check data → process results |
| Subjob | A published workflow referenced by another workflow | Reusable validation flow |
| Version | Published definition snapshot; history is read-only | A parent still references an older task |
| Publish | Produce an executable or referenceable definition version | Save a task; save and publish a workflow |
| Enabled | An object-specific switch controlling applicable operations | An enabled schedule can create future triggers |
| Trigger | An execution request with source, time and inputs | Manual execution or a scheduled time |
| Instance | Identity and state of a particular execution | Root instance and child/node instances |
| Run | The process of progressing toward a terminal state | Inspect topology, stop or retry |
| Parameter template | Input names, types, constraints and defaults | Required batchDate and integer limit |
| Variable | An input, system context value or node output | limit, instance ID or upstream rows |

Account roles ADMIN/OPERATOR/VIEWER differ from runtime roles API/SCHEDULER/WORKER/MAINTENANCE. See [permissions](user-guide/users-audits.md) and [deployment roles](operations/standalone-cluster.md).

## What each action or state establishes

| Action or state | What it establishes | What still needs checking |
| --- | --- | --- |
| Task saved | A new definition version was published | It did not start automatically |
| Workflow published | The publication transaction fixed current child versions | Preflight checks do not lock those versions |
| Object enabled | Its relevant switch is on | Due time, routing and other conditions |
| Trigger accepted | The execution request was received | Instance creation and actual execution |
| Instance SUCCESS | Plugin/workflow completion rules were satisfied | Target data and business conditions |
| Stop or timeout | The platform handled control or deadline conditions | Remote processes and external effects |

## A version-reference example

1. Save task A to publish a version.
2. Add A to workflow B and publish B.
3. Update and save A to publish a newer version.
4. B's older version keeps its original reference to A.
5. Validate and publish B again to adopt the new task version, then inspect its references.

Product version **1.0.0**, definition versions, the Local JAR protocol and database migration numbers are separate identifiers.

## Two event paths

- **Event binding**: a matching event produces a new execution request.
- **Event or approval wait**: an existing instance waits and continues when a matching event or authorized decision arrives. Waiting is not itself failure.

Approval requires the relevant permission, allowed operator and processable state. Stop, timeout and retry do not automatically undo completed external writes.

## Next steps

[Run your first task](quickstart.md) · [Parameters and variables](user-guide/parameters.md) · [Glossary](glossary.md) · [Subjobs and versions](examples/subjobs-and-versions.md)
