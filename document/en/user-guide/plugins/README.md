# Plugin selection and shared behavior

[简体中文](../../../zh-CN/user-guide/plugins/README.md) · [Manual home](../../README.md)

Choose a plugin by business action, then prepare resources. Plugin switches, definition enablement, manual execution and scheduling are separate layers. Saving publishes a task version without running it. Files/interpreters belong to the executing Worker or explicit remote resource.

| Plugin | Reference |
| --- | --- |
| SCRIPT | [Script](script.md) |
| SQL | [SQL execution](sql.md) |
| API_CALL | [API calls](api-call.md) |
| EVENT | [Event publishing, waiting and approval](event.md) |
| DATA_CHECK | [Data checks](data-check.md) |
| FILE_TRANSFER | [File transfer and archives](file-transfer.md) |
| PUSH | [Push database results to messaging](push.md) |
| ETL | [ETL: source, transforms, target and increments](etl.md) |
| BATCH_ETL | [Batch ETL](batch-etl.md) |
| SERVER_MONITOR | [Server monitoring](server-monitor.md) |
| LOCAL_JAR | [Trusted Local JAR plugins](local-jar.md) |

Timeout/stop/retry do not roll back external effects. Plan idempotency and business verification for writes. Logs, variables, row counts and consumer confirmations prove different facts.
