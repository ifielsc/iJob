# 调度、事件、通知与历史保留

[English](../../en/configuration/scheduling-retention.md) · [手册首页](../README.md)

## 调度

初始化默认开启 `app.schedule.enabled`；源码默认值可能不同。实际触发还要求调度本身启用、目标有效、时区/有效期/日历匹配且有可用 SCHEDULER。SKIP 跳过错过的时点；FIRE_ONCE 补发一次；CATCH_UP_BOUNDED 受窗口和数量双重限制。改策略前先预览停机窗口，防止恢复后产生意外批量写入。

## 事件与通知

启用事件接入不绕过登录和 EVENT_PUBLISH 权限。任务中的发布/等待/审批配置见事件插件；邮件通道的 SMTP 和秘密在页面维护。`app.notify.email.enabled` 缺省也启用，但没有有效邮件通道和规则不等于会发送。通知缺省语言只用 en/zh。

## 保留

先保持 `app.retention.dry-run=true`，确认 execution/notification/event/app-run/audit 各类保留周期和预期数量，再在维护窗口决定是否启用实际清理。`enabled=true` 且 `dry-run=false` 才允许删除。批次、间隔和每轮最大批次影响负载；清理不是备份，也不保证外部业务文件一同删除。

## 本主题配置索引

下列为源码/实现默认值；初始化覆盖、来源和完整说明见[完整清单](../configuration-catalog.md)。

| ID / 配置键 | 级别 / 类型 | 默认值来源 | 说明 |
| --- | --- | --- | --- |
| C026 / `app.notification.locale` | A / enum | ${IJOB_NOTIFICATION_LOCALE:en} | 通知默认语言 en/zh；与界面语言分开管理。 |
| C028 / `app.notification.outbox.scan-interval` | B / duration | ${IJOB_NOTIFICATION_OUTBOX_SCAN_INTERVAL:1s} | 通知投递：扫描周期。结合负载、恢复或清理策略验证后调整。 |
| C031 / `app.notification.outbox.retry-delay` | B / duration | ${IJOB_NOTIFICATION_OUTBOX_RETRY_DELAY:30s} | 通知投递：重试等待间隔。结合负载、恢复或清理策略验证后调整。 |
| C032 / `app.notification.outbox.scan-batch-size` | B / integer | ${IJOB_NOTIFICATION_OUTBOX_SCAN_BATCH_SIZE:100} | 通知投递：每轮扫描批量。结合负载、恢复或清理策略验证后调整。 |
| C033 / `app.notification.outbox.max-attempts` | B / integer | ${IJOB_NOTIFICATION_OUTBOX_MAX_ATTEMPTS:5} | 通知投递：内部处理尝试次数上限。结合负载、恢复或清理策略验证后调整。 |
| C077 / `app.schedule.enabled` | A / boolean | ${IJOB_SCHEDULE_ENABLED:false} | 全局调度扫描开关；不替代单条调度启用状态和节点角色。 |
| C078 / `app.schedule.scan-interval` | B / duration | ${IJOB_SCHEDULE_SCAN_INTERVAL:1s} | 调度扫描：扫描周期。结合负载、恢复或清理策略验证后调整。 |
| C079 / `app.schedule.initial-delay` | B / duration | ${IJOB_SCHEDULE_INITIAL_DELAY:5s} | 调度扫描：首次运行延迟。结合负载、恢复或清理策略验证后调整。 |
| C080 / `app.schedule.scan-batch-size` | B / integer | ${IJOB_SCHEDULE_SCAN_BATCH_SIZE:100} | 调度扫描：每轮扫描批量。结合负载、恢复或清理策略验证后调整。 |
| C081 / `app.schedule.max-triggers-per-scan` | B / integer | ${IJOB_SCHEDULE_MAX_TRIGGERS_PER_SCAN:100} | 每轮产生触发的上限；影响吞吐及恢复速度。 |
| C082 / `app.schedule.zone-id` | B / zone-id | ${IJOB_SCHEDULE_ZONE_ID:Asia/Shanghai} | 调度默认业务时区；与用户显示时区不同，核验单条调度设置。 |
| C083 / `app.schedule.misfire-policy` | B / enum | ${IJOB_SCHEDULE_MISFIRE_POLICY:FIRE_ONCE} | 错过触发默认策略 SKIP/FIRE_ONCE/CATCH_UP_BOUNDED；可能产生实际补发。 |
| C084 / `app.schedule.misfire-grace` | B / duration | ${IJOB_SCHEDULE_MISFIRE_GRACE:60s} | 判断错过触发的宽限时间；联动补发策略验证。 |
| C085 / `app.schedule.max-catchup-window` | B / duration | ${IJOB_SCHEDULE_MAX_CATCHUP_WINDOW:24h} | 补发回溯时间上限；避免恢复后集中补发过多历史运行。 |
| C086 / `app.schedule.max-catchup-count` | B / integer | ${IJOB_SCHEDULE_MAX_CATCHUP_COUNT:10} | 补发次数上限；与时间窗口共同约束。 |
| C087 / `app.event.ingress.enabled` | A / boolean | ${IJOB_EVENT_INGRESS_ENABLED:false} | 事件接入 API 开关；仍要求登录与 EVENT_PUBLISH 权限。 |
| C091 / `app.retention.enabled` | A / boolean | ${IJOB_RETENTION_ENABLED:false} | 业务历史清理总开关；开启前核对保留时间和备份。 |
| C092 / `app.retention.dry-run` | A / boolean | ${IJOB_RETENTION_DRY_RUN:true} | 清理试运行；关闭后才允许执行实际删除。 |
| C093 / `app.retention.initial-delay` | B / duration | ${IJOB_RETENTION_INITIAL_DELAY:1m} | 历史清理：首次运行延迟。结合负载、恢复或清理策略验证后调整。 |
| C094 / `app.retention.interval` | B / duration | ${IJOB_RETENTION_INTERVAL:1h} | 历史清理：运行间隔。结合负载、恢复或清理策略验证后调整。 |
| C095 / `app.retention.batch-size` | B / integer | ${IJOB_RETENTION_BATCH_SIZE:500} | 历史清理：批次大小。结合负载、恢复或清理策略验证后调整。 |
| C096 / `app.retention.max-batches-per-run` | B / integer | ${IJOB_RETENTION_MAX_BATCHES_PER_RUN:10} | 历史清理：单轮最大批次数。结合负载、恢复或清理策略验证后调整。 |
| C097 / `app.retention.execution` | A / duration | ${IJOB_RETENTION_EXECUTION:90d} | 执行历史保留时长；与清理总开关和试运行共同生效。 |
| C098 / `app.retention.notification` | A / duration | ${IJOB_RETENTION_NOTIFICATION:90d} | 通知历史保留时长；与清理总开关和试运行共同生效。 |
| C099 / `app.retention.event` | A / duration | ${IJOB_RETENTION_EVENT:180d} | 事件历史保留时长；与清理总开关和试运行共同生效。 |
| C100 / `app.retention.app-run` | A / duration | ${IJOB_RETENTION_APP_RUN:30d} | 节点运行历史保留时长；与清理总开关和试运行共同生效。 |
| C101 / `app.retention.audit` | A / duration | ${IJOB_RETENTION_AUDIT:90d} | 审计历史保留时长；与清理总开关和试运行共同生效。 |
| C163 / `app.notify.email.enabled` | B / boolean | true | 邮件通道实现开关，缺省也启用；SMTP 连接与凭据在邮件通道页面维护。 |
