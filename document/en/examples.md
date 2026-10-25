# Scenarios and verification scope

[简体中文](../zh-CN/examples.md) · [Manual home](README.md)

Sixteen groups progress from harmless runs to writes, automatic triggers and recovery. Screenshots use an isolated real instance. Form screenshots do not prove delivery, consumption or every protocol.

| Scenario | This round |
| --- | --- |
| [Script success, failure, stop and retry](examples/script-lifecycle.md) | Baseline exercised; extensions separate |
| [Remote SSH script](examples/remote-script.md) | Isolated checks completed; see case scope and remaining checks |
| [HTTP, parameters and response extraction](examples/api-and-parameters.md) | Isolated checks completed; see case scope and remaining checks |
| [SQL and data-quality checks](examples/sql-and-validation.md) | Baseline exercised; extensions separate |
| [Local and remote file transfer](examples/file-transfer.md) | Isolated checks completed; see case scope and remaining checks |
| [Full/incremental ETL and checkpoints](examples/etl-full-incremental.md) | Isolated checks completed; see case scope and remaining checks |
| [Multi-table ETL and target naming](examples/batch-etl.md) | Isolated checks completed; see case scope and remaining checks |
| [Serial, parallel, joins and conditional routes](examples/dag-control-flow.md) | Isolated checks completed; see case scope and remaining checks |
| [Subjobs and published references](examples/subjobs-and-versions.md) | Isolated checks completed; see case scope and remaining checks |
| [Time zones, business days and catch-up](examples/schedules-and-calendars.md) | Isolated checks completed; see case scope and remaining checks |
| [Event waits, approval and resumption](examples/events-and-approval.md) | Isolated checks completed; see case scope and remaining checks |
| [Business messages and run notifications](examples/push-and-notifications.md) | Isolated checks completed; see case scope and remaining checks |
| [Monitoring collection, thresholds and recovery](examples/monitoring.md) | Isolated checks completed; see case scope and remaining checks |
| [Deploy and invoke a trusted extension](examples/local-jar.md) | Isolated checks completed; see case scope and remaining checks |
| [Node routing and shared concurrency](examples/routing-and-concurrency.md) | Isolated checks completed; see case scope and remaining checks |
| [Transfer configuration between environments](examples/environment-transfer.md) | Isolated checks completed; see case scope and remaining checks |

Baseline data: three orders with amounts 100/200/300. Business examples use independent PostgreSQL; the platform uses independent SQLite. File examples use independent LOCAL directories. Intentional failure/stop outcomes are expected exercises, not successful business tasks.
