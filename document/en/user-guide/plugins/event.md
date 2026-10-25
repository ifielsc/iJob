# Event publishing, waiting and approval

[简体中文](../../../zh-CN/user-guide/plugins/event.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `EVENT`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Page field/action | Purpose and rules |
| --- | --- |
| Event action / Event code | Choose publish, wait, approval or file-probe action in the form; eventCode identifies the event family. Saving publishes configuration, not an event. |
| Business key / Correlation key / Idempotency key | Use stable business association, correlation and idempotency keys as appropriate; do not share one key across all business operations. |
| Occurrence time / Summary / Payload / Suppress same inbound event | Set occurrence time, summary and payload; same-inbound suppression defaults true to reduce loops. |
| Wait timeout / Lookback / Approvers | Wait deadline, historical lookback (default 0) and allowed approvers; verify identities/event permissions. |
| File resource / Probe directory / Pattern / Recursion | For file probes select the file resource, path, pattern and recursion; defaults * and nonrecursive. |
| Minimum file age / Event limit / Scan depth / Entry limit / Scan timeout | Defaults 30 seconds, 100, 16, 100000 and 60 seconds; bound scanning/stability. fileCaseSensitive defaults true. |

## Minimal example and verification

Prepare the dedicated resources above, fill the form, save, then trigger manually and verify the instance, outputs and target system as described below. Saving alone does not execute.

| Setting | Value to enter |
| --- | --- |
| Event action | Publish event (PUBLISH) |
| Event code | `docs.order.ready` |
| Business key | `order-001` |
| Summary | Synthetic order ready |
| Payload (JSON) | `{"rows":3}` (this field requires JSON) |

Use a dedicated event code and synthetic data; inspect the event record before configuring a matching wait. Authorized users act in Approvals. Verify timeout, duplicate and nonmatching cases separately. Waiting is not a failure, and repeated publishing can duplicate work.

Replace example resources, paths and object names with values from your environment. See the [case index](../../examples.md) for actual external-protocol verification status.

## Technical key reference (optional)

These stored keys help developers and troubleshooting; they are not a task JSON blob to paste into the UI. Automatically validated metadata is not a manual form input.

| Page field/group | Stored keys |
| --- | --- |
| Event action / Event code | `action / eventCode` |
| Business key / Correlation key / Idempotency key | `businessKey / correlationKey / idempotencyKey` |
| Occurrence time / Summary / Payload / Suppress same inbound event | `occurredAt / summary / payload / suppressSameInbound` |
| Wait timeout / Lookback / Approvers | `timeoutSeconds / lookbackSeconds / allowedOperatorIds` |
| File resource / Probe directory / Pattern / Recursion | `fileResourceId / filePath / filePattern / recursive` |
| Minimum file age / Event limit / Scan depth / Entry limit / Scan timeout | `minStableAgeSeconds / maxMatches / maxDepth / maxScannedEntries / scanTimeoutSeconds` |
