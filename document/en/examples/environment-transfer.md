# Transfer configuration between environments

[简体中文](../../zh-CN/examples/environment-transfer.md) · [Manual home](../README.md)

**Verification: configuration/workflow example; this round did not complete end-to-end acceptance of its external dependencies.**

Fields: [Page reference](../user-guide/README.md).

## Prepare, configure and run

Register a fresh target B with independent accounts/database/keys/ports/resources, leaving source A intact. Export one harmless SCRIPT first, then a package containing resources/subjobs. Choose resource configuration, schedules and notification bindings as needed; credentials are excluded.

Upload .ijob.zip into B, map independent resources/channels, fill B credentials, review differences and import. Verify imported tasks/jobs/schedules are disabled, check versions/references, then enable/run one sample. Review history/reports and continue partial failures from records rather than blindly resubmitting. Retain A/B mapping and reports; configuration transfer is not database-history or secret recovery.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
