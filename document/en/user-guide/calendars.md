# Calendars and exceptions

[简体中文](../../zh-CN/user-guide/calendars.md) · [Manual home](../README.md)

## Entry and workflow

Calendars → New/Edit. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `名称/说明` | Describe the business period, e.g. docs-business-days. |
| `规则与日期例外` | Configure supported weekday/date exclusions and exceptions using business-zone dates. |
| `调度关联` | Select the saved calendar on a schedule; calendars do not trigger tasks by themselves. |
| `预览验证` | Verify ordinary weekdays, weekends, exception dates and time boundaries separately. |

## Use and verify

Exclude a future date and inspect allowed/excluded results in schedule preview. Review referencing schedules before changing a shared calendar; test outage recovery because filtering and catch-up interact.
