# Scheduling, events, notifications and retention

[简体中文](../../zh-CN/configuration/scheduling-retention.md) · [Manual home](../README.md)

## Scheduling

Setup enables `app.schedule.enabled`; source defaults may differ. A fire also requires an enabled schedule, valid target, matching time zone/window/calendar and a healthy SCHEDULER. SKIP ignores missed times; FIRE_ONCE catches up once; CATCH_UP_BOUNDED is bounded by both window and count. Preview outage scenarios before changes to avoid unexpected recovery writes.

## Events and notifications

Enabling ingress does not bypass login or EVENT_PUBLISH permission. Use the event plugin for publish/wait/approval configuration. Manage SMTP and secrets in mail-channel pages. `app.notify.email.enabled` is enabled when absent, but delivery still requires a valid channel and rule. Notification fallback language uses en/zh only.

## Retention

Start with `app.retention.dry-run=true`, review execution/notification/event/app-run/audit periods and expected counts, then decide on actual cleanup in a maintenance window. Deletion requires both `enabled=true` and `dry-run=false`. Batch size, interval and maximum batches affect load. Cleanup is not a backup and does not imply deletion of external business files.

## Settings in this topic

These are source/implementation defaults; see the [complete catalog](../configuration-catalog.md) for setup overrides and provenance.

| ID / key | Level / type | Source defaults | Explanation |
| --- | --- | --- | --- |
| C026 / `app.notification.locale` | A / enum | ${IJOB_NOTIFICATION_LOCALE:en} | Default notification language en/zh; separate from UI language. |
| C028 / `app.notification.outbox.scan-interval` | B / duration | ${IJOB_NOTIFICATION_OUTBOX_SCAN_INTERVAL:1s} | Notification delivery: Scan interval. Validate against load, recovery or cleanup policy before tuning. |
| C031 / `app.notification.outbox.retry-delay` | B / duration | ${IJOB_NOTIFICATION_OUTBOX_RETRY_DELAY:30s} | Notification delivery: Retry delay. Validate against load, recovery or cleanup policy before tuning. |
| C032 / `app.notification.outbox.scan-batch-size` | B / integer | ${IJOB_NOTIFICATION_OUTBOX_SCAN_BATCH_SIZE:100} | Notification delivery: Scan batch size. Validate against load, recovery or cleanup policy before tuning. |
| C033 / `app.notification.outbox.max-attempts` | B / integer | ${IJOB_NOTIFICATION_OUTBOX_MAX_ATTEMPTS:5} | Notification delivery: Internal processing attempt limit. Validate against load, recovery or cleanup policy before tuning. |
| C077 / `app.schedule.enabled` | A / boolean | ${IJOB_SCHEDULE_ENABLED:false} | Global schedule scanner switch; separate from per-schedule enabled state and node roles. |
| C078 / `app.schedule.scan-interval` | B / duration | ${IJOB_SCHEDULE_SCAN_INTERVAL:1s} | Schedule scanning: Scan interval. Validate against load, recovery or cleanup policy before tuning. |
| C079 / `app.schedule.initial-delay` | B / duration | ${IJOB_SCHEDULE_INITIAL_DELAY:5s} | Schedule scanning: Initial delay. Validate against load, recovery or cleanup policy before tuning. |
| C080 / `app.schedule.scan-batch-size` | B / integer | ${IJOB_SCHEDULE_SCAN_BATCH_SIZE:100} | Schedule scanning: Scan batch size. Validate against load, recovery or cleanup policy before tuning. |
| C081 / `app.schedule.max-triggers-per-scan` | B / integer | ${IJOB_SCHEDULE_MAX_TRIGGERS_PER_SCAN:100} | Maximum triggers per scan; affects throughput and recovery rate. |
| C082 / `app.schedule.zone-id` | B / zone-id | ${IJOB_SCHEDULE_ZONE_ID:Asia/Shanghai} | Default scheduling time zone; distinct from display time zone. Check individual schedules. |
| C083 / `app.schedule.misfire-policy` | B / enum | ${IJOB_SCHEDULE_MISFIRE_POLICY:FIRE_ONCE} | Default missed-fire policy SKIP/FIRE_ONCE/CATCH_UP_BOUNDED; may create real catch-up runs. |
| C084 / `app.schedule.misfire-grace` | B / duration | ${IJOB_SCHEDULE_MISFIRE_GRACE:60s} | Grace period for missed fires; verify alongside catch-up policy. |
| C085 / `app.schedule.max-catchup-window` | B / duration | ${IJOB_SCHEDULE_MAX_CATCHUP_WINDOW:24h} | Maximum catch-up lookback; bounds recovery bursts. |
| C086 / `app.schedule.max-catchup-count` | B / integer | ${IJOB_SCHEDULE_MAX_CATCHUP_COUNT:10} | Maximum catch-up count; applies with the lookback window. |
| C087 / `app.event.ingress.enabled` | A / boolean | ${IJOB_EVENT_INGRESS_ENABLED:false} | Event ingress API switch; authentication and EVENT_PUBLISH permission still apply. |
| C091 / `app.retention.enabled` | A / boolean | ${IJOB_RETENTION_ENABLED:false} | Business-history cleanup switch; review retention windows and backups before enabling. |
| C092 / `app.retention.dry-run` | A / boolean | ${IJOB_RETENTION_DRY_RUN:true} | Cleanup dry run; disabling allows actual deletion when cleanup is enabled. |
| C093 / `app.retention.initial-delay` | B / duration | ${IJOB_RETENTION_INITIAL_DELAY:1m} | History cleanup: Initial delay. Validate against load, recovery or cleanup policy before tuning. |
| C094 / `app.retention.interval` | B / duration | ${IJOB_RETENTION_INTERVAL:1h} | History cleanup: Run interval. Validate against load, recovery or cleanup policy before tuning. |
| C095 / `app.retention.batch-size` | B / integer | ${IJOB_RETENTION_BATCH_SIZE:500} | History cleanup: Batch size. Validate against load, recovery or cleanup policy before tuning. |
| C096 / `app.retention.max-batches-per-run` | B / integer | ${IJOB_RETENTION_MAX_BATCHES_PER_RUN:10} | History cleanup: Maximum batches per run. Validate against load, recovery or cleanup policy before tuning. |
| C097 / `app.retention.execution` | A / duration | ${IJOB_RETENTION_EXECUTION:90d} | Execution history retention duration; applies with cleanup enabled and dry-run settings. |
| C098 / `app.retention.notification` | A / duration | ${IJOB_RETENTION_NOTIFICATION:90d} | Notification history retention duration; applies with cleanup enabled and dry-run settings. |
| C099 / `app.retention.event` | A / duration | ${IJOB_RETENTION_EVENT:180d} | Event history retention duration; applies with cleanup enabled and dry-run settings. |
| C100 / `app.retention.app-run` | A / duration | ${IJOB_RETENTION_APP_RUN:30d} | Node-run history retention duration; applies with cleanup enabled and dry-run settings. |
| C101 / `app.retention.audit` | A / duration | ${IJOB_RETENTION_AUDIT:90d} | Audit history retention duration; applies with cleanup enabled and dry-run settings. |
| C163 / `app.notify.email.enabled` | B / boolean | true | Email channel implementation switch, enabled when absent; SMTP and credentials belong in mail-channel UI. |
