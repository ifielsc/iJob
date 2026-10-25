# SQL execution

[简体中文](../../../zh-CN/user-guide/plugins/sql.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `SQL`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Page field/action | Purpose and rules |
| --- | --- |
| Database resource / SQL content | Choose an enabled business database resource and SQL. Do not practice against the platform DB. Resources support MySQL/PostgreSQL/SQL Server/Oracle; platform SQLite does not imply a SQLite business-resource type. |
| SQL delimiter / Transaction | Default delimiter ; and transaction=true; transaction behavior depends on the DB and statement types. |
| Continue on error / Query timeout (seconds) | Defaults false / 600 seconds; continuing does not make failed statements successful. Inspect logs/commit scope. |
| Minimum rows / Maximum rows / Result variable | Set result-row bounds as needed; use a result variable for downstream references, not unbounded result sets. |

## Minimal example and verification

Prepare the dedicated resources above, fill the form, save, then trigger manually and verify the instance, outputs and target system as described below. Saving alone does not execute.

| Setting | Value to enter |
| --- | --- |
| Database resource | Select the dedicated test DB containing orders |
| Transaction | On |
| Continue on error | Off |
| Result variable | `ordersResult` |

```sql
SELECT COUNT(*) AS total FROM orders;
```

Prepare an independent orders table, save and run manually, inspect SQL results/logs and query actual writes in the database. This SQL plugin uses text substitution, not SQL parameter binding; validate inputs and handle string quoting explicitly. For reads with named binding, see the ETL source configuration. DDL/nontransactional engines may not roll back; design unique keys/idempotency before retrying INSERT.

Replace example resources, paths and object names with values from your environment. See the [case index](../../examples.md) for actual external-protocol verification status.

## Real page examples

Read-only SQL, resource selection and transaction/timeout summary.

![Read-only SQL, resource selection and transaction/timeout summary.](../../images/manual/sql-editor.png)

## Technical key reference (optional)

These stored keys help developers and troubleshooting; they are not a task JSON blob to paste into the UI. Automatically validated metadata is not a manual form input.

| Page field/group | Stored keys |
| --- | --- |
| Database resource / SQL content | `resourceId / script` |
| SQL delimiter / Transaction | `delimiter / transaction` |
| Continue on error / Query timeout (seconds) | `continueOnError / queryTimeoutSeconds` |
| Minimum rows / Maximum rows / Result variable | `minRows / maxRows / resultVariable` |
