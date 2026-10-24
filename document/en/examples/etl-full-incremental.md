# Full/incremental ETL and checkpoints

[简体中文](../../zh-CN/examples/etl-full-incremental.md) · [Manual home](../README.md)

**Verification: the baseline path was exercised in isolation; extensions do not inherit that result.**

Fields: [ETL: source, transforms, target and increments](../user-guide/plugins/etl.md).

## Prepare, configure and run

Use the independent three-row orders source and an empty target database with the same table. ETL source=DATABASE with `SELECT id,amount,updated_at FROM orders ORDER BY id`; target=DATABASE/table=orders/mode=UPSERT/logicalKeys=[id], mapping all three columns by name. Leave increments disabled. Verify through an independent connection:

```sql
SELECT COUNT(*), SUM(amount) FROM orders;
-- 3, 600
```

This proves this full DATABASE→DATABASE setup. For incremental extensions, use a separate checkpoint: watermarkField=updated_at with explicit type/initial value, id as a stable tie breaker and matching SQL predicates/order. Add equal-watermark rows, verify completeness/duplicates, then exercise failure/retry and record checkpoint/commit behavior. File increments, message sources and SCD2 need separate dependencies/verification.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.

## Real page examples

Full UPSERT configuration.

![Full UPSERT configuration.](../images/manual/etl-target.png)

Actual successful instance; target row count and amount were independently checked with SQL.

![Actual successful instance; target row count and amount were independently checked with SQL.](../images/manual/etl-run.png)
