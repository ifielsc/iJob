# Scenarios and verification scope

[简体中文](../zh-CN/examples.md) · [Manual home](README.md)

Sixteen groups progress from harmless runs to writes, automatic triggers and recovery. Screenshots use an isolated real instance. Form screenshots do not prove delivery, consumption or every protocol.

| Scenario | This round |
| --- | --- |
| [Script success, failure, stop and retry](examples/script-lifecycle.md) | Baseline exercised; extensions separate |
| [Remote SSH script](examples/remote-script.md) | Configuration/workflow example; E2E pending |
| [HTTP, parameters and response extraction](examples/api-and-parameters.md) | Configuration/workflow example; E2E pending |
| [SQL and data-quality checks](examples/sql-and-validation.md) | Baseline exercised; extensions separate |
| [Local and remote file transfer](examples/file-transfer.md) | Baseline exercised; extensions separate |
| [Full/incremental ETL and checkpoints](examples/etl-full-incremental.md) | Baseline exercised; extensions separate |
| [Multi-table ETL and target naming](examples/batch-etl.md) | Configuration/workflow example; E2E pending |
| [Serial, parallel, joins and conditional routes](examples/dag-control-flow.md) | Baseline exercised; extensions separate |
| [Subjobs and published references](examples/subjobs-and-versions.md) | Configuration/workflow example; E2E pending |
| [Time zones, business days and catch-up](examples/schedules-and-calendars.md) | Configuration/workflow example; E2E pending |
| [Event waits, approval and resumption](examples/events-and-approval.md) | Configuration/workflow example; E2E pending |
| [Business messages and run notifications](examples/push-and-notifications.md) | Configuration/workflow example; E2E pending |
| [Monitoring collection, thresholds and recovery](examples/monitoring.md) | Configuration/workflow example; E2E pending |
| [Deploy and invoke a trusted extension](examples/local-jar.md) | Configuration/workflow example; E2E pending |
| [Node routing and shared concurrency](examples/routing-and-concurrency.md) | Configuration/workflow example; E2E pending |
| [Transfer configuration between environments](examples/environment-transfer.md) | Configuration/workflow example; E2E pending |

Baseline data: three orders with amounts 100/200/300. Business examples use independent PostgreSQL; the platform uses independent SQLite. File examples use independent LOCAL directories. Intentional failure/stop outcomes are expected exercises, not successful business tasks.
