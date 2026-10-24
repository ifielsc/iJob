# Schedules, preview and catch-up

[简体中文](../../zh-CN/user-guide/schedules.md) · [Manual home](../README.md)

## Entry and workflow

Schedules → New schedule. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `目标类型/目标` | Choose task/job; admission uses the target current published version. |
| `CRON / INTERVAL / ONCE` | CRON has six fields: second minute hour day month weekday. Intervals use seconds; ONCE needs a valid time. |
| `业务时区 / 开始结束 / 日历` | Business zone comes from the backend, distinct from display zone. Time bounds and calendar filter candidates together. |
| `变量 / 参数模板` | Save the current parameter snapshot; later template edits do not update it. |
| `错过策略 / 宽限 / 补发上限 / 排队期限` | Inherit defaults or override. Queue deadline starts at planned fire time, not execution start. |
| `预览 / 启用` | Preview calculates candidates without saving/running. Enable permits scheduling; preview again after edits. |

## Use and verify

Start with docs-hello, INTERVAL=3600 and disabled state. Preview five candidates and check zone/calendar. Enable only in a controlled window to verify trigger→instance→result, then disable. If no candidates appear, inspect bounds/calendar before shortening the interval.

## Real page examples

Five candidates for a disabled schedule; preview only, automatic running remains off.

![Five candidates for a disabled schedule; preview only, automatic running remains off.](../images/manual/schedule-preview.png)
