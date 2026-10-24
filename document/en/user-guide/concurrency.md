# Business concurrency control

[简体中文](../../zh-CN/user-guide/concurrency.md) · [Manual home](../README.md)

## Entry and workflow

Concurrency; task concurrency policy. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `插件限制` | Bounds plugin concurrency; removing it removes only this layer. |
| `任务并发` | Task bound or no task-layer limit; saving does not publish a task version. |
| `共享资源` | Name/code/limit/enabled; all bound resources must grant permits. |
| `上限/活动实例/版本` | Limits 1–10000; active instances differ from permits. Backend guards/version checks apply. |

## Use and verify

Bind two test tasks to one resource with capacity 1 and observe sequential execution after waiting. Disabling a referenced resource blocks permits. Active work restricts lowering limits/policy changes. For uncertain writes, read current state and explicitly reapply before saving.
