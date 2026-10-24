# Tasks: shared configuration and versions

[简体中文](../../zh-CN/user-guide/tasks.md) · [Manual home](../README.md)

## Entry and workflow

Tasks → New task/Edit. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `name / description` | Use a searchable name and describe purpose/external effects. |
| `pluginType / pluginConfig` | Choose one of 11 plugins and complete its form; see plugin references. |
| `retryTimes / retryInterval / timeout` | Retry count, interval seconds and execution timeout seconds. Start with 0 retries, interval 1, timeout 60; assess repeated writes. |
| `enabled / groups` | Enabled state and up to 16 execution groups matching Workers, not account groups. |
| `保存 / 版本` | Saving publishes the first/next version without running it; inspect history to identify the actual definition. |
| `执行 / 更多操作` | Run submits a trigger; inspect the instance for results. Concurrency policy is separate and does not publish a task version. |

## Use and verify

Create docs-hello with [SCRIPT](plugins/script.md), save, run and confirm the parameter dialog. Updating a task does not rewrite references pinned in published jobs; republish the parent to adopt it. If submission outcome is uncertain, inspect the latest version before resubmitting.

## Real page examples

Saved SCRIPT form; saving publishes a version without running it.

![Saved SCRIPT form; saving publishes a version without running it.](../images/manual/script-editor.png)
