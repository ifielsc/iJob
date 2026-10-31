# Sync two tables and control target names

[简体中文](../../zh-CN/examples/batch-etl.md) · [Manual home](../README.md)

Synchronize orders and order_items together, checking each mapping and target table.

## Audience and outcome

For users syncing multiple tables who need target naming, per-table results and recovery after partial commits.

## Before you start

Complete the [shared preparation](../examples.md), then prepare the resources below.

| Resource | Requirement |
| --- | --- |
| Databases | Two fresh separate PostgreSQL business databases. The setup identity needs CREATE/INSERT; iJob’s source account reads source tables and its target account can read/insert/update targets. The failure variant uses another fresh empty target database. |

## Configuration

| Field / setting | Example value |
| --- | --- |
| Tables / key | orders, order_items / id |
| Extract / load | FULL / UPSERT |
| Target prefix / clear | copy_ / disabled |

## Steps

### 1. Prepare two source tables

Use fresh dedicated databases rather than reusing the ETL exercise after its fourth row was appended. Run as the setup identity in the **source business database**:

```sql
CREATE TABLE orders (
 id INTEGER PRIMARY KEY,
 amount INTEGER NOT NULL,
 updated_at TIMESTAMP NOT NULL
);
INSERT INTO orders VALUES
 (1,100,TIMESTAMP '2026-01-01 00:00:00'),
 (2,200,TIMESTAMP '2026-01-02 00:00:00'),
 (3,300,TIMESTAMP '2026-01-03 00:00:00');
CREATE TABLE order_items (
 id INTEGER PRIMARY KEY,
 order_id INTEGER NOT NULL,
 qty INTEGER NOT NULL
);
INSERT INTO order_items VALUES (1,1,1),(2,2,2),(3,3,3);
```

Create two empty prefixed tables in the **target business database**:

```sql
CREATE TABLE copy_orders (
 id INTEGER PRIMARY KEY,
 amount INTEGER NOT NULL,
 updated_at TIMESTAMP NOT NULL
);
CREATE TABLE copy_order_items (
 id INTEGER PRIMARY KEY,
 order_id INTEGER NOT NULL,
 qty INTEGER NOT NULL
);
```

Grant SELECT on both source tables to iJob’s source account and the required read/insert/update privileges on both targets to its target account. Create and test separate database resources.

### 2. Choose resources and tables

Create BATCH_ETL, choose source/target resources, explicitly select orders and order_items, set id and prefix copy_. Keep FULL, UPSERT and clearing disabled.

### 3. Review mappings and publish

Review each final target name, writable columns and key mapping. Missing non-key target columns do not necessarily fail the task, so inspect the column list. Save/publish and run.

### 4. Check each table and rerun

Run these read-only queries against the target:

```sql
SELECT COUNT(*) AS row_count, SUM(amount) AS amount_total FROM copy_orders;
SELECT COUNT(*) AS row_count, SUM(qty) AS quantity_total FROM copy_order_items;
SELECT id FROM copy_orders ORDER BY id;
SELECT id FROM copy_order_items ORDER BY id;
```

Expect 3 rows/amount 600 in the first query and 3 rows/quantity 6 in the second. Both key sets are 1,2,3. Rerun the same task and query again: results remain unchanged.

### 5. Understand partial commits

Use **another fresh empty target business database** for the failure exercise, leaving the successful target unchanged. Run as that database’s setup identity:

```sql
CREATE TABLE copy_orders (
 id INTEGER PRIMARY KEY,
 amount INTEGER NOT NULL,
 updated_at TIMESTAMP NOT NULL
);
CREATE TABLE copy_order_items (
 id INTEGER PRIMARY KEY,
 order_id INTEGER NOT NULL,
 qty INTEGER NOT NULL,
 CONSTRAINT docs_reject_qty CHECK (qty < 0)
);
```

The constraint is created before data exists and rejects the positive sample quantities during writes. Create a separate resource for this target and grant required run privileges. Create another BATCH_ETL task with the same settings, changing only its target resource. Save/run and inspect FAILED, per-table records and actual data: the rejected table should have 0 rows; whether another table has committed depends on processing order. Do not assume failure rolled back every table.

Only in this failure-exercise database, use a DDL-capable identity to remove the tutorial constraint:

```sql
ALTER TABLE copy_order_items DROP CONSTRAINT docs_reject_qty;
```

Rerun and use step 4 queries to confirm 3/600 and 3/6 respectively, without duplicate keys.

## Expected results

| Check | Expected value |
| --- | --- |
| Orders target | 3 rows, total amount 600 |
| Items target | 3 rows, total quantity 6 |
| Rerun | No duplicate keys; unchanged totals |
| Item constraint rejects writes | FAILED; this example has 3 committed order rows and 0 item rows |
| Rerun after removing the tutorial constraint | Orders: 3 rows/600; items: 3 rows/quantity 6; existing orders are not duplicated |

## Common failures

| Symptom | Action |
| --- | --- |
| Failure after some writes | Inspect per-table results and committed data; repair and rerun idempotently rather than assuming global rollback. |

## Safety and cleanup

Disable related schedules and remove tutorial tables/mappings/failure copies after runs finish.

## Version and limitations

Applies to 1.0.0 PostgreSQL two-table FULL/UPSERT, reruns, item-constraint failure and recovery. The failure copy uses a separate target database, and commits can be partial across tables. Clearing, TRUNCATE and batch increments are outside the main path.

## Related pages

- [Batch ETL](../user-guide/plugins/batch-etl.md)

- [Query orders and add a data quality gate](sql-and-validation.md)
