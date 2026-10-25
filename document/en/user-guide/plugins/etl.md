# ETL: source, transforms, target and increments

[简体中文](../../../zh-CN/user-guide/plugins/etl.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `ETL`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Page field/action | Purpose and rules |
| --- | --- |
| Source type / Resource | DATABASE/FILE/KAFKA/RABBITMQ; match the resource type. |
| Query SQL / Parameters / Before SQL / Fetch size | Read SQL, bound parameters, beforeSql and fetch size (2000). beforeSql may write data. |
| File path / Format / Read mode | FILE formats CSV/TXT/EXCEL/REGEX/SQLITE/MDB; DIRECT and SQL expose different fields. A SQLite file source is not a DB resource. |
| Charset / Delimiter / Quote / Escape / Header / Skip lines | File decoding/delimiters; defaults UTF-8, comma, double quote, header enabled, zero skipped lines. |
| Sheet name / Sheet index / Multiple sheets / Header and start rows | Excel single/multiple sheets and row selection; headerRow defaults 0. Follow index hints in the form. |
| Regex mode / Pattern / Block boundaries / Regex field mappings | Regex line/block parsing; map fields to groups/defaults and bound block lines/characters. |
| Staging table / Staging columns | Staging table/columns for file SQL mode, separate from platform tables. |
| Transformations and validations | Apply ordered transforms and validations; preview samples and check types, nulls and rejection behavior. |
| Target type / Resource / Schema / Table / File path | DATABASE or FILE target; supply the matching table/file path. |
| Mapping mode / Field mappings / Excluded fields / Logical keys | Manual/automatic mapping; verify source-to-target columns. UPSERT and similar modes need valid logical keys. |
| Write mode / Transaction scope / Null policy / Per-column null policies | APPEND/UPSERT/INSERT_ONLY/UPDATE_ONLY/SCD2; defaults APPEND, BATCH, KEEP_TARGET. Column null policies override the overall policy. |
| Before and after SQL / Record protection / SCD2 settings | Before/after SQL, protected records and history settings affect writes. SCD2 needs business keys, comparison fields, effective time and validity/current-flag columns. |
| Existing-file policy / Empty-data output | Defaults FAIL / CREATE_WITH_HEADER; choose overwrite/skip/empty output deliberately. |
| Enable incremental / Checkpoint key / Watermark field and type | Enable increments with explicit checkpoint ownership, watermark field/type; disabled by default, type STRING. |
| Tie-breaker fields / Initial values / Upper-bound SQL / Initial watermark | Use stable tie breakers for equal watermarks; initial values and upper-bound SQL must match predicates/order. |
| Advance past invalid records / File incremental rules | Default allows checkpoint advancement past invalid records; decide whether skipping is acceptable. File increments also need marker location/template or regex/zone/lookback/stability/change policy. |
| Execution limits / Message limits / Non-idempotent retry acknowledgement | Bound read/write batches, query time, records/files/text/Excel. Message sources also bound intake/idle time, batches/buffers/bytes and JSON depth. Non-idempotent retry is not enabled by default. |

## Minimal example and verification

Prepare the dedicated resources above, fill the form, save, then trigger manually and verify the instance, outputs and target system as described below. Saving alone does not execute.

| Setting | Value to enter |
| --- | --- |
| Source type / Resource | Database / Dedicated test source |
| Target type / Resource | Database / Dedicated test target |
| Target table | `orders` (create in advance) |
| Write mode | Upsert (UPSERT) |
| Logical keys | `id` |
| Field mapping | `id→id`, `amount→amount`, `updated_at→updated_at` |
| Enable incremental | Off |

```sql
SELECT id, amount, updated_at FROM orders ORDER BY id;
```

Begin with three-row full DATABASE→DATABASE transfer to orders, map id/amount/updated_at, use logical key id and UPSERT. Verify three target rows and amount sum 600. Exercise increments separately with equal watermarks, retries, failures, invalid rows and checkpoints; a maximum timestamp alone does not prove completeness. BATCH transactions may partially commit. Inspect target/checkpoint before retrying; message acknowledgments and target writes are separate boundaries.

Replace example resources, paths and object names with values from your environment. See the [case index](../../examples.md) for actual external-protocol verification status.

## Real page examples

Database source and read-only extraction SQL.

![Database source and read-only extraction SQL.](../../images/manual/etl-editor.png)

Target table, UPSERT, logical key and same-name column mappings.

![Target table, UPSERT, logical key and same-name column mappings.](../../images/manual/etl-target.png)

## Technical key reference (optional)

These stored keys help developers and troubleshooting; they are not a task JSON blob to paste into the UI. Automatically validated metadata is not a manual form input.

| Page field/group | Stored keys |
| --- | --- |
| Source type / Resource | `source.type / resourceId` |
| Query SQL / Parameters / Before SQL / Fetch size | `source.sql / parameters / beforeSql / fetchSize` |
| File path / Format / Read mode | `source.path / format / readMode` |
| Charset / Delimiter / Quote / Escape / Header / Skip lines | `source.charset / delimiter / quote / escape / header / skipLines` |
| Sheet name / Sheet index / Multiple sheets / Header and start rows | `source.sheetName / sheetIndex / sheets / headerRow / startRow` |
| Regex mode / Pattern / Block boundaries / Regex field mappings | `source.regexMode / pattern / blockStart / blockEnd / regexFields` |
| Staging table / Staging columns | `source.stagingTable / stagingColumns` |
| Transformations and validations | `transforms / validations` |
| Target type / Resource / Schema / Table / File path | `target.type / resourceId / schema / table / path` |
| Mapping mode / Field mappings / Excluded fields / Logical keys | `target.mappingMode / columns / excludedFields / logicalKeys` |
| Write mode / Transaction scope / Null policy / Per-column null policies | `target.mode / transactionScope / nullPolicy / columnNullPolicies` |
| Before and after SQL / Record protection / SCD2 settings | `target.beforeSql / afterSql / protection / scd2` |
| Existing-file policy / Empty-data output | `target.existingFilePolicy / emptyFilePolicy` |
| Enable incremental / Checkpoint key / Watermark field and type | `incremental.enabled / checkpointKey / watermarkField / watermarkType` |
| Tie-breaker fields / Initial values / Upper-bound SQL / Initial watermark | `incremental.tieBreakerFields / initialTieBreakerValues / upperBoundSql / initialWatermark` |
| Advance past invalid records / File incremental rules | `incremental.allowCheckpointPastInvalidRecords / file` |
| Execution limits / Message limits / Non-idempotent retry acknowledgement | `execution / message / allowNonIdempotentRetry` |
