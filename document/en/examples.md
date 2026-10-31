# Scenario tutorials: start with a business goal

[简体中文](../zh-CN/examples.md) · [Manual home](README.md)

Choose a small scenario with a concrete result. Follow configuration and execution steps, then inspect the target system. Times are reading/operation estimates after prerequisites are ready, excluding external-service installation.

## Shared preparation

- Complete the [quick start](quickstart.md) and understand [definitions, triggers and instances](concepts.md).
- Use newly created tutorial data/resources. Keep business and platform databases separate; supply your own paths, ports and accounts.
- Use ADMIN for the complete tutorial, including configuration, publication and logs/variables. OPERATOR can execute configured tasks and use applicable controls. Approval also requires allowed-operator membership; see [permissions](user-guide/users-audits.md).
- A “copy” in these tutorials means creating a newly named object and entering the same example configuration. The task and job lists do not provide a one-click clone action.
- Execute manually before enabling schedules. Plan idempotency for writes; stopping or retrying does not undo completed effects.
- Clean up only objects created for the tutorial. Keep passwords, tokens and keys in resources/private configuration.

## Choose a tutorial

Start with the first group, then choose by business goal. The full order workflow has a separate [environment setup page](examples/order-reconciliation-setup.md) for its data and receiver before the orchestration steps.

Scope identifies the documented version/environment, not proof that every protocol extension or new combined workflow was executed.

### Start with one task

| Scenario | What you achieve | Environment | Difficulty / estimate | Scope |
| --- | --- | --- | --- | --- |
| [Run a script and distinguish success, failure and stop](examples/script-lifecycle.md) | Use four script variants for success, failure, stop and timeout to distinguish trigger acceptance from actual execution results. | Windows PowerShell Worker | Beginner / 10 min | 1.0.0 · Local Worker |
| [Query orders and add a data quality gate](examples/sql-and-validation.md) | Query three orders and use a scalar check to detect an unexpected row count. | PostgreSQL | Beginner / 15 min | 1.0.0 · PostgreSQL |
| [Copy an orders file and verify its contents](examples/file-transfer.md) | Copy a CSV to a separate directory and verify contents, hashes and source preservation. | Two dedicated directories | Beginner / 10 min | 1.0.0 · Two dedicated directories |

### Move data and connect systems

| Scenario | What you achieve | Environment | Difficulty / estimate | Scope |
| --- | --- | --- | --- | --- |
| [Sync incremental orders with equal-watermark handling](examples/etl-full-incremental.md) | Load three orders, append an order at the same timestamp, and keep reruns idempotent. | Source/target PostgreSQL | Intermediate / 25 min | 1.0.0 · Source/target PostgreSQL |
| [Sync two tables and control target names](examples/batch-etl.md) | Synchronize orders and order_items together, checking each mapping and target table. | Source/target PostgreSQL | Intermediate / 20 min | 1.0.0 · Source/target PostgreSQL |
| [Call an orders API and extract variables](examples/api-and-parameters.md) | Pass a date and limit to a read-only API, then extract total and amount for later steps. | Python HTTP example service | Intermediate / 15 min | 1.0.0 · Python HTTP example service |
| [Publish three order messages and configure run notifications separately](examples/push-and-notifications.md) | Verify order messages at a consumer and distinguish them from platform email notifications. | PostgreSQL, Kafka; SMTP for email | Intermediate / 25 min | 1.0.0 · PostgreSQL, Kafka; SMTP for email |

### Orchestrate and control a workflow

| Scenario | What you achieve | Environment | Difficulty / estimate | Scope |
| --- | --- | --- | --- | --- |
| [Build parallel joins and conditional routes](examples/dag-control-flow.md) | Wait for two parallel branches before joining, then choose one route by amount. | Local Worker | Intermediate / 20 min | 1.0.0 · Local Worker |
| [Control which child version a parent job uses](examples/subjobs-and-versions.md) | Observe a parent's pinned child version and adopt a new version by publishing each level. | Local Worker | Intermediate / 15 min | 1.0.0 · Local Worker |
| [Resume a run after an event or approval](examples/events-and-approval.md) | Wait for a matching event or designated approval and observe the same instance continue. | DATABASE accounts | Intermediate / 20 min | 1.0.0 · DATABASE accounts |

### Deploy, operate and extend

| Scenario | What you achieve | Environment | Difficulty / estimate | Scope |
| --- | --- | --- | --- | --- |
| [Run a remote script over SSH](examples/remote-script.md) | Produce a message and variable remotely, then check the remote path and execution result. | Independent SSH host | Intermediate / 15 min | 1.0.0 · Independent SSH host |
| [Collect monitoring metrics and interpret threshold alarms](examples/monitoring.md) | Inspect fresh memory metrics and change a tutorial threshold to observe health-state changes. | Monitor Agent | Intermediate / 15 min | 1.0.0 · Monitor Agent |
| [Preview schedules and understand missed-run recovery](examples/schedules-and-calendars.md) | Inspect timezone/calendar candidates, then compare missed-run policies in a dedicated installation. | An instance you can stop | Intermediate / 25 min | 1.0.0 · An instance you can stop |
| [Route by group and limit shared concurrency](examples/routing-and-concurrency.md) | Route tasks to matching Workers, then serialize two tasks through one shared permit. | Independent Workers/groups | Advanced / 25 min | 1.0.0 · Independent Workers/groups |
| [Deploy a trusted Local JAR and invoke it](examples/local-jar.md) | Use a minimal protocol-1 extension to emit hello-localjar and rows=3. | Java 21, trusted SPI JAR | Advanced / 25 min | 1.0.0 · Java 21, trusted SPI JAR |
| [Transfer configuration to another environment](examples/environment-transfer.md) | Import a harmless task into a separate target and inspect disabled state, mappings and execution. | Two independent instances | Intermediate / 20 min | 1.0.0 · Two independent instances |

### Complete business workflow

| Scenario | What you achieve | Environment | Difficulty / estimate | Scope |
| --- | --- | --- | --- | --- |
| [Daily order reconciliation and report delivery](examples/order-reconciliation.md) | Sync 12 orders, validate and reconcile, then deliver a CSV and receipt through direct or approved routing. | PostgreSQL, local HTTP receiver | Intermediate / 45–60 min | 1.0.0 · Normal, failed quality check, approved delivery |

## Checking results

Inspect platform states and business outcomes together: row counts, totals, file hashes, API responses, consumed messages or post-approval node states. Each tutorial lists concrete expected values. Interpret intentional failure/stop exercises according to their steps.
