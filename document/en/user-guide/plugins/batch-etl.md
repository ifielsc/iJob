# Batch ETL

[简体中文](../../../zh-CN/user-guide/plugins/batch-etl.md) · [Manual](../../README.md)

Applies to **1.0.0**. Tasks → New/Edit → plugin `BATCH_ETL`.

## Before you start

Use ADMIN to create, configure and save tasks. ADMIN or OPERATOR can execute published definitions; ADMIN reads detailed logs and input/output variables. Resources must be enabled and reachable from the execution node. See [tasks](../tasks.md) for common timeout, retry and version settings, and the [permission matrix](../users-audits.md).

## Field reference

“Default” means the **initial value of a new 1.0.0 form**. An empty value means the field has not been filled in; required fields still need a value. Visibility follows the selected branch; conditional requirements apply only to that branch.

### Tables and write settings

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Source database | Required | None | Always | Current task version |
| Target database | Required | None | Always | Current task version |
| Source schema (optional) | Optional | Empty | Always | Current task version |
| Target schema (optional) | Optional | Empty | Always | Current task version |
| Tables | Required | Empty list | Always | Current task version |
| Logical keys | Conditionally required | Empty list | Non-APPEND writes or incremental extraction | Current task version |
| Extraction mode | Select | FULL | Always | Current task version |
| Write mode | Select | UPSERT | Always | Current task version |
| Null policy | Select | OVERWRITE | Always | Current task version |
| Target prefix (optional) | Optional | Empty | Always | Current task version |
| Target suffix (optional) | Optional | Empty | Always | Current task version |
| Marker column | Conditionally required | Empty | INCREMENTAL | Current task version |
| Marker type | Select | TIMESTAMP | INCREMENTAL | Current task version |
| Initial marker value | Conditionally required | Empty | INCREMENTAL | Current task version |
| Initial logical key values | Conditionally required | Empty list | INCREMENTAL | Current task version |
| Clear before import | Optional | false | FULL | Current task version |
| Clear mode | Select | DELETE | FULL with Clear before import enabled | Current task version |
| I understand: deletion and batch commits can leave partial results | Selection required conditionally | false | DELETE clearing mode | Current task version |
| I understand: TRUNCATE may commit implicitly and cannot be fully rolled back | Selection required conditionally | false | TRUNCATE clearing mode | Current task version |
| I understand: automatic retries with append can duplicate data | Selection required conditionally | false | APPEND with task retries greater than 0 | Current task version |

- **Source database**: Provide the source database connection for batch reads.
- **Target database**: Provide the target database connection for batch writes.
- **Source schema (optional)**: Select the schema containing source tables.
- **Target schema (optional)**: Select the schema containing target tables.
- **Tables**: List tables to synchronize without duplicates.
- **Logical keys**: Match target records and break incremental ties; every table must contain these columns.
- **Extraction mode**: Choose full or incremental extraction.
- **Write mode**: Choose append, insert, update or upsert behavior for target tables.
- **Null policy**: Choose whether source nulls overwrite or preserve existing target values.
- **Target prefix (optional)**: Prefix added when deriving target table names.
- **Target suffix (optional)**: Suffix added when deriving target table names.
- **Marker column**: Column used to track incremental progress in every table.
- **Marker type**: Type used to interpret and compare incremental markers.
- **Initial marker value**: Starting marker used when no checkpoint exists.
- **Initial logical key values**: Initial tie-break values in the same order as logical keys.
- **Clear before import**: Clear target tables before a full import.
- **Clear mode**: Choose DELETE or TRUNCATE for clearing tables.
- **I understand: deletion and batch commits can leave partial results**: Acknowledge that deletion followed by batch commits can leave partial results on failure.
- **I understand: TRUNCATE may commit implicitly and cannot be fully rolled back**: Acknowledge that TRUNCATE may commit implicitly and prevent complete rollback.
- **I understand: automatic retries with append can duplicate data**: Explicitly accept possible duplicate writes when retrying append operations.

### Execution limits

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Fetch batch rows | Has a default | 2000 | Advanced settings | Current task version |
| Write batch rows | Has a default | 1000 | Advanced settings | Current task version |
| Query timeout (seconds) | Has a default | 300 | Advanced settings | Current task version |
| Maximum tables | Has a default | 500 | Advanced settings | Current task version |
| Maximum rows per table | Has a default | 10000000 | Advanced settings | Current task version |
| Maximum total rows | Has a default | 50000000 | Advanced settings | Current task version |

- **Fetch batch rows**: 1–100000。
- **Write batch rows**: 1–100000。
- **Query timeout (seconds)**: 1–86400。
- **Maximum tables**: 1–500。
- **Maximum rows per table**: 1–9223372036854775807。
- **Maximum total rows**: 1–9223372036854775807。

## Behavior and boundaries

- At most 500 tables; table and logical-key lists cannot contain duplicates.
- Incremental mode forbids APPEND; every table must contain the configured watermark column and logical keys.
- Tables have independent commit boundaries; a failure does not imply rollback of tables already committed.

## Typical configuration: Synchronize orders and order_items

Fill in the form below, replacing resource names with your enabled resources. This is a configuration guide, not importable JSON. Follow the tutorial linked below for data preparation and execution.

Prepare three rows each in source orders and order_items; create target copy_orders and copy_order_items with id primary keys.

| Setting | Value |
| --- | --- |
| Source database | Isolated source database |
| Target database | Isolated target database |
| Tables | ["orders","order_items"] |
| Logical keys | ["id"] |
| Extraction mode | FULL |
| Write mode | UPSERT |
| Clear before import | false |
| Target prefix (optional) | copy_ |

**Expected result:** copy_orders has three rows totaling 600; copy_order_items has three rows with qty totaling 6. Keys and totals remain unchanged after a repeat run.

[Follow the full tutorial](../../examples/batch-etl.md). Save the definition before running it; inspect execution state and business results separately.

## Configuration keys

Use these keys when inspecting imported configuration, API payloads or errors. `[]` denotes a collection item; the form does not require you to enter key names.

| Category / field | Configuration key |
| --- | --- |
| Tables and write settings / Source database | `sourceResourceId` |
| Tables and write settings / Target database | `targetResourceId` |
| Tables and write settings / Source schema (optional) | `sourceSchema` |
| Tables and write settings / Target schema (optional) | `targetSchema` |
| Tables and write settings / Tables | `tables` |
| Tables and write settings / Logical keys | `logicalKeys` |
| Tables and write settings / Extraction mode | `extractMode` |
| Tables and write settings / Write mode | `loadMode` |
| Tables and write settings / Null policy | `nullPolicy` |
| Tables and write settings / Target prefix (optional) | `targetNaming.prefix` |
| Tables and write settings / Target suffix (optional) | `targetNaming.suffix` |
| Tables and write settings / Marker column | `incremental.watermarkColumn` |
| Tables and write settings / Marker type | `incremental.watermarkType` |
| Tables and write settings / Initial marker value | `incremental.initialWatermark` |
| Tables and write settings / Initial logical key values | `incremental.initialLogicalKeyValues` |
| Tables and write settings / Clear before import | `clearBeforeImport` |
| Tables and write settings / Clear mode | `clearMode` |
| Tables and write settings / I understand: deletion and batch commits can leave partial results | `acknowledgePartialCommit` |
| Tables and write settings / I understand: TRUNCATE may commit implicitly and cannot be fully rolled back | `acknowledgeNonTransactionalTruncate` |
| Tables and write settings / I understand: automatic retries with append can duplicate data | `allowNonIdempotentRetry` |
| Execution limits / Fetch batch rows | `execution.fetchSize` |
| Execution limits / Write batch rows | `execution.writeBatchSize` |
| Execution limits / Query timeout (seconds) | `execution.queryTimeoutSeconds` |
| Execution limits / Maximum tables | `execution.maxTables` |
| Execution limits / Maximum rows per table | `execution.maxRowsPerTable` |
| Execution limits / Maximum total rows | `execution.maxTotalRows` |

## Related pages

[Tasks and versions](../tasks.md) · [Parameters and expressions](../../reference/variables.md) · [Scenario tutorials](../../examples.md)
