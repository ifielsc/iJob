# Operations, upgrades and troubleshooting

[简体中文](../zh-CN/operations.md) · [Manual home](README.md)

## Daily operation

Keep the program directory, IJOB_HOME, runtime account and URL explicit. Separate program files from runtime data; every node needs its own local directory. Use the installation guide's lifecycle commands. Before stopping the platform, choose a maintenance window, disable schedules that must pause and inspect in-flight runs. Platform shutdown does not undo external side effects.

The workbench shows node availability, freshness and scan/renewal state. Use the Run center for actual instances, nodes, logs and topology. UNKNOWN, stale and unavailable do not mean zero load. A working web page alone does not prove Worker or scheduler health.

## Backup and restore

For a SQLite starter installation, use a consistent stopped backup: disable practice schedules, stop gracefully, confirm process exit and back up the whole IJOB_HOME, including settings, installation.properties, setup.complete, runtime, database and original AES key. Do not copy only a running platform.db; WAL state may not be merged. Restrict and, where required, encrypt backups. Never commit them to Git.

Rehearse restoration into a new directory with independent ports. Retain the original backup and check absolute paths, permissions, settings and data after restoration. Copied PID/control records do not establish ownership of a current process; launchers must verify identity. Never kill a PID just because it appears in a backup. Program and database capabilities must be compatible. Verify login, saved definitions/versions, history and encrypted-resource readability before running an isolated example.

Use the database vendor's backup/restore mechanisms for service databases, managed alongside application settings and the original key. Restore to a new database, not an existing business database. Measure RPO/RTO in the target environment; historical rehearsal timing is not a production guarantee.

## Upgrade and rollback

1. Record current package hashes, source identity, settings location and database version. Produce a restorable backup.
2. Extract the new package into a new program directory and verify SHA256SUMS and candidate test results. Never overwrite running program files.
3. Stop the old instance and start the new package with the original IJOB_HOME. Keep its database, settings and original key; setup retains existing installations.
4. Verify readiness, login, resources, definitions, history and one isolated example. Record deployment time and results.
5. Before rollback, determine whether schema or business writes changed. Choose a compatible old package or restore the matching backup to a new target. Replacing a JAR cannot undo migration or external operations.

The product remains 1.0.0 and new installations use formal V1. Do not point the application at a historical database. Historical conversion tooling/resources require a separate build and migration review, outside quick installation. There is no current-version one-click cross-database migration promise.

## Accounts, clusters and production entry points

STATIC is the default configured-account mode. For DATABASE accounts, back up in a single-node maintenance window. Only for an empty account table, temporarily enable bootstrap to establish the first account from existing administrator settings, complete mandatory password change, then disable bootstrap and restart. For a nonempty table, use an existing enabled administrator. Bootstrap neither resets accounts nor bypasses missing-administrator protection.

SQLite is single-instance only. CLUSTER requires a shared service database, consistent authentication/AES/token configuration, unique node names and separate local directories. Start A and wait for readiness before B. Never mix STANDALONE and CLUSTER writers on the same database or change a single node's key to experiment. Existing-cluster authentication/key changes need a compatibility migration plan. Execution is at least once; external writes need business idempotency keys. Two processes on one host are not physical HA.

Production requires target-specific TLS, access control, readiness-based load balancing, upload/SSE timeouts, alert ownership and capacity validation. Base images provide basic runtime components; explicitly supply and validate interpreters, Local JARs and business files. Do not mount docker.sock or enable privileged mode to add task capabilities.

## Troubleshooting

| Symptom | Check in order |
| --- | --- |
| Startup failure/timeout | Java → home/permissions → port → logs → database; timeout does not mean process exit |
| Settings appear ignored | Correct home → duplicate keys → launcher/environment overrides → restart/container recreation |
| Login failure | Authentication mode/account source → actual node → logs/audit; do not erase accounts or keys |
| Task does not run | Enabled target → published version → group/Worker → concurrency → instance state |
| Schedule does not fire | Enabled target and schedule → global switch → timezone/window/calendar → SCHEDULER |
| Missing file/interpreter | Check the actual Worker/container filesystem, not the browser's computer |
| Node remains fenced after database recovery | Verify database, restart A to readiness then B; retain coordination records |

When reporting issues, include version, source/artifact hashes, steps, instance ID, sanitized errors and expected behavior. Exclude complete application.properties files, databases, tokens and credentials.
