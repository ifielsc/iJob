# Runtime-mode changes and rollback

[简体中文](../../zh-CN/operations/runtime-mode-change.md) · [Manual home](../README.md)

## Before changing

Record mode, database product/location, artifact hash, roles, routing, account source and compatibility settings. Take a restorable backup. SQLite → service database is a data migration, not merely a JDBC URL edit. There is no one-click cross-product migration; validate migration and recovery in an independent target first.

## STANDALONE → CLUSTER

1. Pause triggers, handle active instances and stop all old instances. Never run both modes against the same business database concurrently.
2. Verify the shared database, privileges, compatible artifacts and identical security settings. Create independent homes/names/directories for each node.
3. Set CLUSTER and planned roles/groups while retaining migration and required maintenance. Do not disable guards to bypass failures.
4. Start A first and verify READY, database and roles; then start B and check compatibility, freshness and claims.
5. Exercise harmless task/job/schedule scenarios, checking execution nodes, logs and recovery before resuming business.

## CLUSTER → STANDALONE

Pause new work and stop all nodes. Confirm database compatibility and supported recovery of active work/coordination state, then start the sole STANDALONE instance. If startup guards reject it, preserve evidence and diagnose rather than clearing coordination records.

## Rollback conditions

Stop new nodes first to avoid mixed modes. Restore the old deployment only when program/database compatibility, matching configuration and side effects have been checked. Otherwise restore a matching backup into a new target. Existing-cluster key/fingerprint changes, cross-database migration and incompatible protocol mixing are not automated by this workflow.
