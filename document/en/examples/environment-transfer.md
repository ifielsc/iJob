# Transfer configuration between environments

[简体中文](../../zh-CN/examples/environment-transfer.md) · [Manual home](../README.md)

**Verification: tested in this round’s isolated environment within the scope below.**

Fields: [Page reference](../user-guide/README.md).

## Prepare, configure and run

Register a fresh target B with independent accounts/database/keys/ports/resources, leaving source A intact. Export one harmless SCRIPT first, then a package containing resources/subjobs. Choose resource configuration, schedules and notification bindings as needed; credentials are excluded.

Upload .ijob.zip into B, map independent resources/channels, fill B credentials, review differences and import. Verify imported tasks/jobs/schedules are disabled, check versions/references, then enable/run one sample. Review history/reports and continue partial failures from records rather than blindly resubmitting. Retain A/B mapping and reports; configuration transfer is not database-history or secret recovery.

## Scope verified this round

This round exported a current parent/child graph and harmless script from A into B with independent database, accounts and keys. New objects were disabled and verified; repeating the import after B restarted reused every object. An explicitly enabled script executed successfully, and the original plan then reported DRIFTED after that intentional change. Resource/secret remapping, partial resume and concurrent target conflicts remain separate.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
