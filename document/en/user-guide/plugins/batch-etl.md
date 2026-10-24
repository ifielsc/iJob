# Batch ETL

[简体中文](../../../zh-CN/user-guide/plugins/batch-etl.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `BATCH_ETL`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Field/action | Purpose and rules |
| --- | --- |
| `sourceResourceId / sourceSchema / targetResourceId / targetSchema / tables` | Source/target DB resources, optional schemas and explicit table list; start with one or two small tables. |
| `logicalKeys / extractMode / incremental` | Default FULL; INCREMENTAL requires watermarkColumn/type/initialWatermark/initialLogicalKeyValues. Verify required columns in every table. |
| `loadMode / nullPolicy / targetNaming` | Defaults UPSERT / OVERWRITE; target prefix/suffix default empty and do not rename sources. |
| `clearBeforeImport / clearMode` | No pre-clear by default; clearMode DELETE. TRUNCATE may be nontransactional. |
| `acknowledgeNonTransactionalTruncate / acknowledgePartialCommit / allowNonIdempotentRetry` | Explicit risk acknowledgments, not automatic safety; default false. |
| `execution` | Defaults fetchSize 2000, writeBatchSize 1000, timeout 300 seconds, 500 tables, 10 million rows/table and 50 million rows total. |

## Minimal example and verification

```text
Tables: orders, order_items
Extract: FULL
Load: UPSERT
Clear before import: false
```

Ensure both tables have suitable logical keys and target schemas. Preview selected tables, target names and mappings; verify counts, keys and totals per table. One successful table does not prove whole-batch success. Review committed tables before retrying; exercise clearing only on independent copies with restorable backups.

Snippets require your resource IDs/paths. See the [case index](../../examples.md) for actual external-protocol verification status.
