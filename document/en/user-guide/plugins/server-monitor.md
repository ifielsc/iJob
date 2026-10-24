# Server monitoring

[简体中文](../../../zh-CN/user-guide/plugins/server-monitor.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `SERVER_MONITOR`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Field/action | Purpose and rules |
| --- | --- |
| `resourceId` | Select a monitor-server resource; keep Agent endpoint/token in the resource. |
| `collect.items / collect.applications` | Select required system metrics/application checks supported by the Agent. |
| `rules.cpu / memory / disk / network / applications` | Configure thresholds/application rules for collected metrics; check units, paths and missing metrics. |
| `notify.enabled / notifyConfigId / recovery` | Notification enable/configuration/recovery flags; preview recipients and avoid sending rehearsal messages to business contacts. |

## Minimal example and verification

```text
Resource: dedicated monitor Agent
Collect: CPU + memory
Notify: disabled for first exercise
```

Test connectivity and collect once, checking time, freshness, metrics and rules. Collection failure, missing data and threshold alerts are different; unavailable is not zero. Verify alert/recovery separately with isolated rules, and delivery with separate evidence.

Snippets require your resource IDs/paths. See the [case index](../../examples.md) for actual external-protocol verification status.
