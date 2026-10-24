# Run center, logs, graphs and stop

[简体中文](../../zh-CN/user-guide/runs.md) · [Manual home](../README.md)

## Entry and workflow

Run center → trigger/instance details. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `触发与实例` | Acceptance means a request exists; wait for its root instance and inspect final state. |
| `版本/节点/时间` | Check actual definition version, Worker, start/end/duration; current edits are not the run snapshot. |
| `状态` | SUCCESS, FAILED and STOPPED are distinct outcomes; keep observing waiting/queued/paused work. |
| `日志/变量/拓扑` | Select root/node scope for logs, output and dependency states; sensitive fields are permission-controlled. |
| `停止/重试` | Stop is a request requiring result verification. Retry can repeat external actions; check idempotency/target data first. |

## Use and verify

docs-hello should show SUCCESS and `[STDOUT] hello-ijob`; inspect output variable rows. The workflow graph should complete hello→check. Log pagination/truncation does not limit actual execution. Supported pause acts at step boundaries; current external work may finish and pause time still counts toward timeout.

## Real page examples

Actual logs filtered through the UI to the hello output; clear search to view other loaded lines.

![Actual logs filtered through the UI to the hello output; clear search to view other loaded lines.](../images/manual/script-run.png)

Both nodes of the actual workflow run succeeded.

![Both nodes of the actual workflow run succeeded.](../images/manual/job-run.png)
