# Multi-table ETL and target naming

[简体中文](../../zh-CN/examples/batch-etl.md) · [Manual home](../README.md)

**Verification: independent PostgreSQL passed two-table full loads, target prefixes, partial-commit failure and reexecution after repair. Clear, incremental and TRUNCATE variants remain separate.**

Fields: [Batch ETL](../user-guide/plugins/batch-etl.md).

## Prepare, configure and run

Prepare independent databases with orders/order_items, three rows each and suitable logical key id, plus compatible target tables. Select an explicit table list, FULL, UPSERT and clearBeforeImport=false. Optional targetNaming.prefix=copy_ requires matching target tables.

Review table/column previews; verify counts, key sets and totals per table after execution. Mapping uses writable columns with matching names; omitting a non-key source column from the target does not necessarily fail, so review the mapping. In a separate target database, a CHECK constraint on the second table deliberately rejected the sample data. The first table retained four committed rows totaling 1000, the second retained zero, and the FAILED instance reported possible partial commits. After removing only the fixture constraint, reexecution kept four first-table rows and completed three second-table rows. Clear/incremental/nontransactional TRUNCATE variants need additional review/backups; acknowledgment does not remove partial-commit risk.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
