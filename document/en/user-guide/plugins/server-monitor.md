# Server monitoring

[简体中文](../../../zh-CN/user-guide/plugins/server-monitor.md) · [Manual](../../README.md)

Applies to **1.0.0**. Tasks → New/Edit → plugin `SERVER_MONITOR`.

## Before you start

Use ADMIN to create, configure and save tasks. ADMIN or OPERATOR can execute published definitions; ADMIN reads detailed logs and input/output variables. Resources must be enabled and reachable from the execution node. See [tasks](../tasks.md) for common timeout, retry and version settings, and the [permission matrix](../users-audits.md).

## Field reference

“Default” means the **initial value of a new 1.0.0 form**. An empty value means the field has not been filled in; required fields still need a value. Visibility follows the selected branch; conditional requirements apply only to that branch.

### Collection

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Monitor server | Required | None | Always | Current task version |
| Collection items | At least one item | ["CPU"] | Always | Current task version |

- **Monitor server**: Provide the monitoring-agent connection for the monitored server.
- **Collection items**: CPU/MEMORY/DISK/NETWORK/APPLICATION, without duplicates.

### Application collection

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Application name | Required | Empty | APPLICATION collection enabled | Current task version |
| Match type | Select | PROCESS_NAME | APPLICATION collection enabled | Current task version |
| Match value | Required | Empty | APPLICATION collection enabled | Current task version |
| Must be running | Select | true | APPLICATION collection enabled | Current task version |
| Allow multiple processes | Select | false | APPLICATION collection enabled | Current task version |

- **Application name**: Unique application name in the monitor configuration; at most 64 applications and 128 characters per name.
- **Match type**: Choose PROCESS_NAME, COMMAND, PID or REGEX to match processes.
- **Match value**: Provide the value required by the selected match type; PID must be a positive integer and regex patterns are limited to 256 characters.
- **Must be running**: Specify whether the application must be running.
- **Allow multiple processes**: Specify whether multiple processes may match the application.

### System thresholds

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Warning | At least one threshold for an enabled rule | 80 | Corresponding metric collection and thresholds enabled | CPU usage (%) |
| Critical | At least one threshold for an enabled rule | 90 | Corresponding metric collection and thresholds enabled | CPU usage (%) |
| Warning | At least one threshold for an enabled rule | None | Corresponding metric collection and thresholds enabled | Memory usage (%) |
| Critical | At least one threshold for an enabled rule | None | Corresponding metric collection and thresholds enabled | Memory usage (%) |
| Warning | At least one threshold for an enabled rule | None | Corresponding metric collection and thresholds enabled | Disk usage (%) |
| Critical | At least one threshold for an enabled rule | None | Corresponding metric collection and thresholds enabled | Disk usage (%) |
| Warning | At least one threshold for an enabled rule | None | Corresponding metric collection and thresholds enabled | Network receive (B/s) |
| Critical | At least one threshold for an enabled rule | None | Corresponding metric collection and thresholds enabled | Network receive (B/s) |
| Warning | At least one threshold for an enabled rule | None | Corresponding metric collection and thresholds enabled | Network send (B/s) |
| Critical | At least one threshold for an enabled rule | None | Corresponding metric collection and thresholds enabled | Network send (B/s) |

- **Warning**: Network rates use B/s; CPU, memory and disk use percentages.
- **Critical**: The critical threshold cannot be lower than the warning threshold.
- **Warning**: Network rates use B/s; CPU, memory and disk use percentages.
- **Critical**: The critical threshold cannot be lower than the warning threshold.
- **Warning**: Network rates use B/s; CPU, memory and disk use percentages.
- **Critical**: The critical threshold cannot be lower than the warning threshold.
- **Warning**: Network rates use B/s; CPU, memory and disk use percentages.
- **Critical**: The critical threshold cannot be lower than the warning threshold.
- **Warning**: Network rates use B/s; CPU, memory and disk use percentages.
- **Critical**: The critical threshold cannot be lower than the warning threshold.

Each enabled rule requires a warning or critical threshold; critical cannot be below warning. CPU, memory and disk use percentages; network rates use B/s.

### Disk

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Disk path overrides | Optional | Empty | DISK collection and disk rules enabled | Current task version |

- **Disk path overrides**: Match the full path; use default thresholds when no override matches.

### Application thresholds

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| CPU (%) | Conditionally required | None | The corresponding application monitor is enabled | Current task version |
| Memory (MiB) | Conditionally required | None | The corresponding application monitor is enabled | Current task version |

- **CPU (%)**: Application names must match collection settings; CPU is a percentage and memory is in MiB.
- **Memory (MiB)**: Application names must match collection settings; CPU is a percentage and memory is in MiB.

### Alerts

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Send monitor alerts | Optional | false | Always | Current task version |
| Notification configuration | Conditionally required | None | Monitor alerts enabled | Current task version |

- **Send monitor alerts**: Send monitoring alerts from rule results when enabled.
- **Notification configuration**: Select the notification configuration for monitoring alerts.

## Behavior and boundaries

- Enable at least one valid rule; rules can be configured only for collected metrics.
- Recovery notifications and cross-run alert deduplication are unsupported; notify.recovery=true is rejected.
- The first rate sample may be 0; missing metrics differ from actual zero values.

## Typical configuration: Collect CPU and memory metrics

Fill in the form below, replacing resource names with your enabled resources. This is a configuration guide, not importable JSON. Follow the tutorial linked below for data preparation and execution.

Prepare an authenticated monitoring agent reachable from the Worker. Threshold results depend on actual metrics; this example does not guarantee a healthy result.

| Setting | Value |
| --- | --- |
| Monitor server | Configured tutorial agent resource |
| Collection items | ["CPU","MEMORY"] |
| Enable CPU thresholds | true |
| CPU warning threshold | 80 |
| CPU critical threshold | 95 |
| Send monitor alerts | false |

**Expected result:** Timestamps and metrics are current and valid; threshold results are interpretable. An alert does not automatically mean task execution failed.

[Follow the full tutorial](../../examples/monitoring.md). Save the definition before running it; inspect execution state and business results separately.

## Configuration keys

Use these keys when inspecting imported configuration, API payloads or errors. `[]` denotes a collection item; the form does not require you to enter key names.

| Category / field | Configuration key |
| --- | --- |
| Collection / Monitor server | `resourceId` |
| Collection / Collection items | `collect.items` |
| Application collection / Application name | `collect.applications[].name` |
| Application collection / Match type | `collect.applications[].matchType` |
| Application collection / Match value | `collect.applications[].matchValue` |
| Application collection / Must be running | `collect.applications[].required` |
| Application collection / Allow multiple processes | `collect.applications[].allowMultiple` |
| System thresholds / Warning | `rules.cpu.usage.warning` |
| System thresholds / Critical | `rules.cpu.usage.critical` |
| System thresholds / Warning | `rules.memory.usage.warning` |
| System thresholds / Critical | `rules.memory.usage.critical` |
| System thresholds / Warning | `rules.disk.usage.warning` |
| System thresholds / Critical | `rules.disk.usage.critical` |
| System thresholds / Warning | `rules.network.receiveRate.warning` |
| System thresholds / Critical | `rules.network.receiveRate.critical` |
| System thresholds / Warning | `rules.network.sendRate.warning` |
| System thresholds / Critical | `rules.network.sendRate.critical` |
| Disk / Disk path overrides | `rules.disk.paths` |
| Application thresholds / CPU (%) | `rules.applications[].cpu` |
| Application thresholds / Memory (MiB) | `rules.applications[].memory` |
| Alerts / Send monitor alerts | `notify.enabled` |
| Alerts / Notification configuration | `notify.notifyConfigId` |

## Related pages

[Tasks and versions](../tasks.md) · [Parameters and expressions](../../reference/variables.md) · [Scenario tutorials](../../examples.md)
