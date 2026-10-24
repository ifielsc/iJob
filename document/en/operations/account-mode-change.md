# Changing account mode and bootstrapping the first administrator

[简体中文](../../zh-CN/operations/account-mode-change.md) · [Manual home](../README.md)

## Scope

This workflow converts an independent installation from STATIC to DATABASE during maintenance. Existing cluster authentication fingerprints require a separate compatibility migration, not independent node-by-node edits. [Back up](backup-restore.md) the database, complete configuration and original keys, pause triggers and stop the application first.

## Case A: empty user table

1. Preserve `app.auth.username` and `app.auth.password-hash`; set `app.auth.mode=DATABASE` and `app.auth.bootstrap-enabled=true`.
2. Start and inspect logs. Only an empty user table permits first-administrator bootstrap. Log in with the original credentials.
3. Complete the required password change, retain the new password and verify administrator role/status.
4. Stop again, set `app.auth.bootstrap-enabled=false`, preserve other controlled values and restart.
5. Log in with the new password in a fresh session; verify users, audits and a business read. Confirm bootstrap is off before resuming work.

## Case B: an enabled administrator already exists

Keep bootstrap=false and log in with the persisted administrator. STATIC credentials may differ; establish the usable identity and recovery path before maintenance.

## Case C: nonempty table without a usable administrator

A startup guard failure is not an instruction to clear the table. Bootstrap does not reset existing accounts. Retain errors/backups, confirm account status, target database and previous changes, then use a validated administrator recovery plan.

## Rollback and reverse conversion

Before restoring configuration, confirm the installation remains independent and old STATIC credentials work. Stop, restore matching configuration and verify login. DATABASE → STATIC changes effective permissions/account sources and requires reviewing all users; it does not delete persisted users. Configuration rollback does not undo database or external writes. Retain the original AES key rather than reinitializing for an account-mode change.
