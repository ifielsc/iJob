# Backup and independent restore

[简体中文](../../zh-CN/operations/backup-restore.md) · [Manual home](../README.md)

## What to retain

Keep the program/hash, complete IJOB_HOME, original AES key/key-id, database backup and additional business files/plugins together. The home includes private configuration, installation.properties, setup.complete, runtime and logs. Restrict access and encrypt according to organizational policy; never commit backups to Git.

## Consistent offline SQLite backup

1. Pause schedules/new triggers and wait for or handle active work.
2. Stop gracefully and verify process exit before copying the entire home into a fresh backup directory.
3. Record artifact hash, time, database path and checksums. Copying only a running platform.db is insufficient because WAL state may not be checkpointed.

## Independent restore exercise

Restore into a new directory/port, leaving backup and current instance untouched. For native deployment, point spring.datasource.url at the new runtime/platform.db and verify absolute paths, listener and permissions. Copied PID files are not current process identities; never kill a process solely using their PID.

For Docker, retain internal database paths and port 8080. Give the copied compose.yaml a new project name and change both bind sources to the new application.properties and runtime, preserving container UID/GID 10001 permissions. Start with the matching bundle's `legacy/start-docker.sh --recreate`.

Verify readiness, login, task/job versions, history and encrypted resource readability, then run an independent harmless task. Record duration/differences. A past rehearsal does not establish production RPO/RTO commitments.

## Service databases

Use the database product's consistent backup mechanism and align configuration, original keys and business files to the same recovery point. Restore into a new database; disable automatic business entry points before verification. Database backups do not include side effects already sent to APIs, email or queues.
