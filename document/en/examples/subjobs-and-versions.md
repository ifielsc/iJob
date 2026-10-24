# Subjobs and published references

[简体中文](../../zh-CN/examples/subjobs-and-versions.md) · [Manual home](../README.md)

**Verification: configuration/workflow example; this round did not complete end-to-end acceptance of its external dependencies.**

Fields: [Page reference](../user-guide/README.md).

## Prepare, configure and run

Create task v1 outputting version-one, a subjob referencing it and a parent referencing the subjob. Record all definition versions. Update the task to v2 outputting version-two without republishing jobs; the parent should retain its published snapshot references.

Republish the subjob and verify its selected task version, then republish the parent and verify the new subjob version. History is read-only; inspect published nested topology for old references. Preflight does not lock publication-time versions. Under concurrent editing, use confirmation and actual snapshots. Capture each step; version increments alone do not prove execution behavior.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
