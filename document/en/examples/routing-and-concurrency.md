# Route by group and limit shared concurrency

[简体中文](../../zh-CN/examples/routing-and-concurrency.md) · [Manual home](../README.md)

Route tasks to matching Workers, then serialize two tasks through one shared permit.

## Audience and outcome

For workflow authors who have completed the quick start.

## Before you start

Read the [shared tutorial preparation](../examples.md). Estimates exclude external-service installation. Use your own tutorial resources, paths and ports.

| Resource | Requirement |
| --- | --- |
| Cluster | A dedicated CLUSTER environment with a supported shared platform database and two independently identified Workers/data directories. |
| Scripts | Two Windows LOCAL / POWERSHELL tasks, each waiting 6 seconds and logging UTC start/end times and a Worker marker. |

## Configuration

| Field / setting | Example value |
| --- | --- |
| Worker groups | Worker A=finance; Worker B=other |
| Task groups | Task A=finance; Task B=other |
| Shared permit resource | docs-shared, capacity 1, enabled |

## Steps

### 1. Configure and inspect Workers

Follow the [cluster guide](../operations/standalone-cluster.md) to prepare a shared platform database and three independent nodes: a management node with API, SCHEDULER, and MAINTENANCE, and two WORKER-only nodes in groups finance and other. Use CLUSTER, compatible authentication/security settings, distinct instance names, and separate data directories. **Start the first node and wait for formal migrations and readiness before starting the others**; do not initialize an empty platform database concurrently. Confirm A/B readiness with no unintended ID allowlists. Set `IJOB_TUTORIAL_WORKER=finance` in Worker A’s startup environment and `other` for B, then restart so child processes inherit it. This is a tutorial observation marker, not platform routing configuration.

### 2. Publish matching tasks

Create two SCRIPT tasks with LOCAL execution, POWERSHELL, UTF-8 script/output encoding, timeout 60 seconds, and retry count/interval 0. Assign finance to A and other to B, use the same script, and publish:

```powershell
$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($env:IJOB_TUTORIAL_WORKER)) { throw "Worker marker missing" }
Write-Output ("started=" + [DateTime]::UtcNow.ToString("o"))
Write-Output ("worker=" + $env:IJOB_TUTORIAL_WORKER)
Write-Output ("__VAR__:worker=" + $env:IJOB_TUTORIAL_WORKER)
Start-Sleep -Seconds 6
Write-Output ("finished=" + [DateTime]::UtcNow.ToString("o"))
```

Run each separately. Logs and output variable `worker` should identify finance / other and match the actual claiming nodes.

### 3. Observe execution without the shared limit

In concurrency management, ensure SCRIPT plugin capacity is at least 2, task policies introduce no additional serialization, and neither task has the shared permit yet. Trigger A/B together and inspect the two SUCCESS runs: their six-second execution intervals should overlap. If they do not, check trigger timing, Worker readiness, and capacity before comparing the shared limit; acceptance alone does not prove parallel execution.

### 4. Bind a capacity-1 shared resource

After both finish, create enabled docs-shared with capacity 1 in concurrency management and bind it to both tasks. Saving concurrency policy does not publish task definitions.

### 5. Trigger together and inspect exclusion

Trigger A/B together and observe one executing while the other waits for a permit. Compare actual execution intervals; they must not overlap. The limit applies across nodes.

### 6. Understand unmatched groups and allowlists

Use a dedicated unmatched-group task to inspect queuing, then provide a matching Worker or cancel through supported controls rather than leaving it pending. ID allowlists filter root targets: task-only lists reject job roots, and job-only lists reject standalone task roots.

## Expected results

| Check | Expected value |
| --- | --- |
| Routing | finance→A, other→B |
| Without the shared permit | Both runs SUCCESS, with overlapping execution intervals |
| Shared capacity 1 | Both runs SUCCESS, with non-overlapping intervals and unchanged finance / other routing |

## Common failures

| Symptom | Action |
| --- | --- |
| Persistent queue | Check roles, groups, root allowlists and layered permits; an empty Worker group is not a wildcard. |

## Safety and cleanup

Drain/cancel tutorial triggers, then remove shared bindings/resources and restore dedicated node settings after active work finishes.

## Version and limitations

Applies to iJob 1.0.0. The example covers a separate CLUSTER with one management node and two Windows Workers, finance / other group routing, and cross-Worker serialization through shared capacity 1, with the same results in both languages. Multiple processes on one host do not establish physical HA. Linux scripts, cross-host failover, and allowlist combinations are not claimed as fully covered here.

## Related pages

- [Node roles, groups and eligible targets](../configuration/routing.md)

- [Business concurrency control](../user-guide/concurrency.md)

- [Standalone and cluster: prerequisites and roles](../operations/standalone-cluster.md)
