# Platform database: standalone, cluster and business resources

[简体中文](../../zh-CN/configuration/platform-database.md) · [Manual home](../README.md)

## Distinguish two database connections

`spring.datasource.url`, `spring.datasource.username` and `spring.datasource.password` configure the **iJob platform database**, containing task/workflow definitions, execution history, schedules, resource settings and coordination data. Each node reads them from application.properties in its own data home; changes are controlled operations.

**Business databases** used by SQL, ETL or PUSH are configured under Resources → Database resources and selected by tasks. They are separate connections. Do not change spring.datasource to switch a task's source, or use the platform DB for practice writes.

| Deployment | Platform connection |
| --- | --- |
| STANDALONE | One instance uses its platform DB: SQLite or supported MySQL, PostgreSQL, SQL Server or Oracle. Standalone does not imply SQLite. |
| CLUSTER | Each node configures its own application.properties and connects to the same logical platform database/schema. SQLite is unsupported. URL strings may differ for network routing, but must reach the same platform data. |
| Independent systems | Separate platform databases/schemas, data homes, identities and ports. Sharing a machine does not make a cluster. |

## Additional cluster requirements

Matching database URLs alone do not establish a cluster. Nodes also need CLUSTER mode, unique instance names, complete role coverage, compatible versions, matching authentication mode and the required identical BCrypt/AES/Token settings. Each has its own home/control/log directories; sharing the DB does not copy scripts/files. Follow the [mode-change workflow](../operations/runtime-mode-change.md), stop and verify coherently, and never mix STANDALONE/CLUSTER against the same platform data.

| Setting | Multi-node meaning |
| --- | --- |
| spring.datasource.url | Shared platform DB reachable from that node/container. Container localhost refers to the container itself. |
| spring.datasource.username / password | Node DB credentials with required privileges, connecting to the same platform schema. These are not web-login credentials. |
| spring.datasource.hikari.maximum-pool-size | Per-node pool limit. Size database capacity for all nodes plus other clients. |
| Vendor, driver, platform/Flyway/default schema | Keep aligned with the connection and installation metadata. A URL edit alone cannot change database products or migrate data. |

Initialization defaults to STANDALONE; choosing a server DB does not automatically enable a cluster. Distinguish a new address for the same DB from a new DB/product migration, which requires a separate migration/restore plan. Preserve the original database, configuration and AES key through [backup/restore](../operations/backup-restore.md). Verify migrations, login, existing definitions/history and a harmless task after the change.

## Relationship to the launcher

instance.conf locates the program, home, language and installation mode. DB properties remain in home/application.properties. The 54 settings/check-settings validations do not establish database connectivity or cluster consistency; verify startup and real operation.
