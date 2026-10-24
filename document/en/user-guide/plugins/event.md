# Event publishing, waiting and approval

[简体中文](../../../zh-CN/user-guide/plugins/event.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `EVENT`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Field/action | Purpose and rules |
| --- | --- |
| `action / eventCode` | Choose publish, wait, approval or file-wait action in the form; eventCode identifies the event family. Saving publishes configuration, not an event. |
| `businessKey / correlationKey / idempotencyKey` | Use stable business association, correlation and idempotency keys as appropriate; do not share one key across all business operations. |
| `occurredAt / summary / payload / suppressSameInbound` | Set occurrence time, summary and payload; same-inbound suppression defaults true to reduce loops. |
| `timeoutSeconds / lookbackSeconds / allowedOperatorIds` | Wait deadline, historical lookback (default 0) and allowed approvers; verify identities/event permissions. |
| `fileResourceId / filePath / filePattern / recursive` | For file waits select the file resource, path, pattern and recursion; defaults * and nonrecursive. |
| `minStableAgeSeconds / maxMatches / maxDepth / maxScannedEntries / scanTimeoutSeconds` | Defaults 30 seconds, 100, 16, 100000 and 60 seconds; bound scanning/stability. fileCaseSensitive defaults true. |

## Minimal example and verification

```json
{"action":"PUBLISH","eventCode":"docs.order.ready","businessKey":"order-001","summary":"Synthetic order ready","payload":{"rows":3}}
```

Use a dedicated event code and synthetic data; inspect the event record before configuring a matching wait. Authorized users act in Approvals. Verify timeout, duplicate and nonmatching cases separately. Waiting is not a failure, and repeated publishing can duplicate work.

Snippets require your resource IDs/paths. See the [case index](../../examples.md) for actual external-protocol verification status.
