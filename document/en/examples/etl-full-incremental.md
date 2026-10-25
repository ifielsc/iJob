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

This round verified repeated full UPSERT, checkpoint recovery and equal-watermark additions against independent PostgreSQL. Use a separate checkpointKey, watermarkField=updated_at, watermarkType=TIMESTAMP, initialWatermark=2025-12-31T00:00:00, tieBreakerFields=[id], initialTieBreakerValues=[0], and upper-bound SQL `SELECT MAX(updated_at) FROM orders`. Cast JSON string parameters explicitly in the source query:

```sql
SELECT id, amount, updated_at FROM orders
WHERE (updated_at > CAST(:etlLastWatermark AS timestamp)
   OR (updated_at = CAST(:etlLastWatermark AS timestamp) AND id > :etlLastTie1))
  AND updated_at <= CAST(:etlUpperWatermark AS timestamp)
ORDER BY updated_at, id;
```

After importing three rows, add id=4/amount=400 at the last watermark. The next execution produced four rows totaling 1000; an empty rerun changed nothing. Testing exposed and fixed a parse failure when reloading JDBC timestamp checkpoints serialized with an offset. The new package resumed the original checkpoint successfully; new values preserve local time and nanosecond precision. Checkpoints and target writes still have separate commit boundaries and need an idempotent target. Separate PostgreSQL tables verified SCD2 initial/unchanged writes, closing and opening versions on a business change, replay, backdated-effective-time rejection and recovery. Independent SQL checked contiguous history intervals and one current version. File increments and SCD2 on other database dialects remain separate; see [message scenarios](push-and-notifications.md) for actual message consumption and failure recovery. Existing screenshots show the full-load path.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.

## Real page examples

Full UPSERT configuration.

![Full UPSERT configuration.](../images/manual/etl-target.png)

Actual successful instance; target row count and amount were independently checked with SQL.

![Actual successful instance; target row count and amount were independently checked with SQL.](../images/manual/etl-run.png)
