# Batch ETL

[简体中文](../../../zh-CN/user-guide/plugins/batch-etl.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `BATCH_ETL`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Page field/action | Purpose and rules |
| --- | --- |
| Source database / Source schema / Target database / Target schema / Tables | Source/target DB resources, optional schemas and explicit table list; start with one or two small tables. |
| Logical keys / Extraction mode / Incremental rules | Default FULL; INCREMENTAL requires watermarkColumn/type/initialWatermark/initialLogicalKeyValues. Verify required columns in every table. |
| Load mode / Null policy / Target name prefix and suffix | Defaults UPSERT / OVERWRITE; target prefix/suffix default empty and do not rename sources. |
| Clear before import / Clear mode | No pre-clear by default; clearMode DELETE. TRUNCATE may be nontransactional. |
| Nontransactional truncate / Partial commit / Non-idempotent retry acknowledgements | Explicit risk acknowledgments, not automatic safety; default false. |
| Batch and execution limits | Defaults fetchSize 2000, writeBatchSize 1000, timeout 300 seconds, 500 tables, 10 million rows/table and 50 million rows total. |

## Minimal example and verification

Prepare the dedicated resources above, fill the form, save, then trigger manually and verify the instance, outputs and target system as described below. Saving alone does not execute.

| Setting | Value to enter |
| --- | --- |
| Source / Target database | Select independent test resources |
| Tables | `orders`, `order_items` |
| Logical keys | `id` (must exist in both tables) |
| Extraction mode | Full (FULL) |
| Load mode | Upsert (UPSERT) |
| Clear before import | Off |

Ensure both tables have suitable logical keys and target schemas. Preview selected tables, target names and mappings; verify counts, keys and totals per table. One successful table does not prove whole-batch success. Review committed tables before retrying; exercise clearing only on independent copies with restorable backups.

Replace example resources, paths and object names with values from your environment. See the [case index](../../examples.md) for actual external-protocol verification status.

## Technical key reference (optional)

These stored keys help developers and troubleshooting; they are not a task JSON blob to paste into the UI. Automatically validated metadata is not a manual form input.

| Page field/group | Stored keys |
| --- | --- |
| Source database / Source schema / Target database / Target schema / Tables | `sourceResourceId / sourceSchema / targetResourceId / targetSchema / tables` |
| Logical keys / Extraction mode / Incremental rules | `logicalKeys / extractMode / incremental` |
| Load mode / Null policy / Target name prefix and suffix | `loadMode / nullPolicy / targetNaming` |
| Clear before import / Clear mode | `clearBeforeImport / clearMode` |
| Nontransactional truncate / Partial commit / Non-idempotent retry acknowledgements | `acknowledgeNonTransactionalTruncate / acknowledgePartialCommit / allowNonIdempotentRetry` |
| Batch and execution limits | `execution` |
