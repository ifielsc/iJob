# Server monitoring

[简体中文](../../../zh-CN/user-guide/plugins/server-monitor.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `SERVER_MONITOR`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Page field/action | Purpose and rules |
| --- | --- |
| Monitor server | Select a monitor-server resource; keep Agent endpoint/token in the resource. |
| Collection items / Application collection | Select required system metrics/application checks supported by the Agent. |
| CPU / Memory / Disk / Network / Application thresholds | Configure thresholds/application rules for collected metrics; check units, paths and missing metrics. |
| Send monitor alerts / Notification configuration | Choose whether to send monitor alerts and the notification configuration. Recovery notifications and cross-run deduplication are not supported; disable notifications for the first exercise. |

## Minimal example and verification

Prepare the dedicated resources above, fill the form, save, then trigger manually and verify the instance, outputs and target system as described below. Saving alone does not execute.

| Setting | Value to enter |
| --- | --- |
| Monitor server | Select a dedicated test Agent resource |
| Collection items | CPU, memory |
| CPU usage → Enable / Warning / Critical | On / 80 / 95 (percent) |
| Send monitor alerts | Off for the first exercise |

Test connectivity and collect once, checking time, freshness, metrics and rules. Collection failure, missing data and threshold alerts are different; unavailable is not zero. Verify abnormal and healthy collection results with isolated rules. Returning to normal does not automatically send a recovery notification; message delivery requires separate evidence.

Replace example resources, paths and object names with values from your environment. See the [case index](../../examples.md) for actual external-protocol verification status.

## Technical key reference (optional)

These stored keys help developers and troubleshooting; they are not a task JSON blob to paste into the UI. Automatically validated metadata is not a manual form input.

| Page field/group | Stored keys |
| --- | --- |
| Monitor server | `resourceId` |
| Collection items / Application collection | `collect.items / collect.applications` |
| CPU / Memory / Disk / Network / Application thresholds | `rules.cpu / memory / disk / network / applications` |
| Send monitor alerts / Notification configuration | `notify.enabled / notify.notifyConfigId` |
