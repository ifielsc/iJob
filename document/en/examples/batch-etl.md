# Multi-table ETL and target naming

[简体中文](../../zh-CN/examples/batch-etl.md) · [Manual home](../README.md)

**Verification: configuration/workflow example; this round did not complete end-to-end acceptance of its external dependencies.**

Fields: [Batch ETL](../user-guide/plugins/batch-etl.md).

## Prepare, configure and run

Prepare independent databases with orders/order_items, three rows each and suitable logical key id, plus compatible target tables. Select an explicit table list, FULL, UPSERT and clearBeforeImport=false. Optional targetNaming.prefix=copy_ requires matching target tables.

Review table/column previews; verify counts, key sets and totals per table after execution. Repeat to test idempotency. Use another database for a missing-column failure and record committed/remaining tables. Clear/incremental/nontransactional TRUNCATE variants need additional review/backups; acknowledgment does not remove partial-commit risk.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
