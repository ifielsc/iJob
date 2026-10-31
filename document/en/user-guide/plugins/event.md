# Event publishing, waiting and approval

[简体中文](../../../zh-CN/user-guide/plugins/event.md) · [Manual](../../README.md)

Applies to **1.0.0**. Tasks → New/Edit → plugin `EVENT`.

## Before you start

Use ADMIN to create, configure and save tasks. ADMIN or OPERATOR can execute published definitions; ADMIN reads detailed logs and input/output variables. Resources must be enabled and reachable from the execution node. See [tasks](../tasks.md) for common timeout, retry and version settings, and the [permission matrix](../users-audits.md).

## Field reference

“Default” means the **initial value of a new 1.0.0 form**. An empty value means the field has not been filled in; required fields still need a value. Visibility follows the selected branch; conditional requirements apply only to that branch.

### Action

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Action | Select | PUBLISH | Always | Current task version |

- **Action**: Select publication, event waiting, approval waiting or file probing.

### File probing

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| File resource | Conditionally required | Empty | PROBE_FILE | Current task version |
| Probe directory | Conditionally required | / | PROBE_FILE | Current task version |
| File pattern | Has a default | * | PROBE_FILE | Current task version |
| Recursive | Has a default | false | PROBE_FILE | Current task version |
| Case sensitive | Has a default | true | PROBE_FILE | Current task version |
| Minimum file age (seconds) | Has a default | 30 | PROBE_FILE | Current task version |
| New event limit | Has a default | 100 | PROBE_FILE | Current task version |
| Maximum scan depth | Has a default | 16 | PROBE_FILE | Current task version |
| Scan entry limit | Has a default | 100000 | PROBE_FILE | Current task version |
| Scan timeout (seconds) | Has a default | 60 | PROBE_FILE | Current task version |

- **File resource**: File resource containing the directory to probe.
- **Probe directory**: Select the directory to probe within the file resource path constraints.
- **File pattern**: Filter files that should produce events.
- **Recursive**: Scan subdirectories subject to depth and entry limits.
- **Case sensitive**: Control case sensitivity when matching file names.
- **Minimum file age (seconds)**: Require files to reach the minimum stable age before probing.
- **New event limit**: Limit newly created events in one probe.
- **Maximum scan depth**: Limit recursive directory depth.
- **Scan entry limit**: Limit directory and file entries scanned per probe.
- **Scan timeout (seconds)**: Limit file-probe scan duration.

### Events and waiting

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Event code | Conditionally required | Empty | PROBE_FILE / PUBLISH / WAIT_EVENT | Current task version |
| Business key | Optional | Empty | PROBE_FILE / PUBLISH / WAIT_EVENT | Current task version |
| Correlation key | Optional | Empty | PROBE_FILE / PUBLISH / WAIT_EVENT | Current task version |
| Summary | Optional | Empty | PROBE_FILE / PUBLISH | Current task version |
| Event payload (JSON) | Optional | Empty | PUBLISH | Current task version |
| Idempotency key | Optional | Empty | PUBLISH | Current task version |
| Occurred at | Optional | Empty | PUBLISH | Current task version |
| Suppress same inbound event | Has a default | true | PUBLISH | Current task version |
| Wait timeout (seconds) | Optional | null | WAIT_EVENT / WAIT_APPROVAL | Current task version |
| Lookback (seconds) | Has a default | 0 | WAIT_EVENT | Current task version |
| Approvers | Conditionally required | [] | WAIT_APPROVAL | Current task version |

- **Event code**: Identify the event type to publish or wait for.
- **Business key**: Associate events with business objects; participate in wait matching.
- **Correlation key**: Correlate events in the same business flow; participate in wait matching.
- **Summary**: Provide a short business description of the event.
- **Event payload (JSON)**: Carry event business data without secrets.
- **Idempotency key**: Identify the same event publication for submission deduplication.
- **Occurred at**: Supply the event occurrence time as an ISO local date-time interpreted as UTC.
- **Suppress same inbound event**: Suppress repeated publication from the same inbound event.
- **Wait timeout (seconds)**: Event and approval waits use this field, not the shared task timeout.
- **Lookback (seconds)**: Allow a wait to search existing events within the lookback window.
- **Approvers**: List accounts permitted to decide this approval; approval permission is still required.

## Behavior and boundaries

- WAIT_APPROVAL requires at least one allowed approver; the account must be enabled and have approval permission.
- Event codes use [A-Za-z0-9._:-] and contain 1–128 characters.
- Occurrence time is an ISO local date-time without Z or an offset; the UI instructs users to enter UTC.
- The payload is limited to 16 KiB.
- File-probe metadata generates the payload, occurrence time and idempotency key.

## Typical configuration: Publish an order-ready event

Fill in the form below, replacing resource names with your enabled resources. This is a configuration guide, not importable JSON. Follow the tutorial linked below for data preparation and execution.

To observe wait resumption, first run the tutorial WAIT_EVENT task with the same event code and business key; with lookback=0, start waiting before publication.

| Setting | Value |
| --- | --- |
| Action | PUBLISH |
| Event code | docs.order.ready |
| Business key | order-001 |
| Summary | Example orders are ready |
| Event payload (JSON) | {"rows":3} |

**Expected result:** The event record contains docs.order.ready and order-001. A WAIT_EVENT instance started first with matching code and keys continues to completion; publication itself does not create a waiting instance.

[Follow the full tutorial](../../examples/events-and-approval.md). Save the definition before running it; inspect execution state and business results separately.

## Configuration keys

Use these keys when inspecting imported configuration, API payloads or errors. `[]` denotes a collection item; the form does not require you to enter key names.

| Category / field | Configuration key |
| --- | --- |
| Action / Action | `action` |
| File probing / File resource | `fileResourceId` |
| File probing / Probe directory | `filePath` |
| File probing / File pattern | `filePattern` |
| File probing / Recursive | `recursive` |
| File probing / Case sensitive | `fileCaseSensitive` |
| File probing / Minimum file age (seconds) | `minStableAgeSeconds` |
| File probing / New event limit | `maxMatches` |
| File probing / Maximum scan depth | `maxDepth` |
| File probing / Scan entry limit | `maxScannedEntries` |
| File probing / Scan timeout (seconds) | `scanTimeoutSeconds` |
| Events and waiting / Event code | `eventCode` |
| Events and waiting / Business key | `businessKey` |
| Events and waiting / Correlation key | `correlationKey` |
| Events and waiting / Summary | `summary` |
| Events and waiting / Event payload (JSON) | `payload` |
| Events and waiting / Idempotency key | `idempotencyKey` |
| Events and waiting / Occurred at | `occurredAt` |
| Events and waiting / Suppress same inbound event | `suppressSameInbound` |
| Events and waiting / Wait timeout (seconds) | `timeoutSeconds` |
| Events and waiting / Lookback (seconds) | `lookbackSeconds` |
| Events and waiting / Approvers | `allowedOperatorIds` |

## Related pages

[Tasks and versions](../tasks.md) · [Parameters and expressions](../../reference/variables.md) · [Scenario tutorials](../../examples.md)
