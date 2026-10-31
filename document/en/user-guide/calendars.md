# Calendars and exceptions

[简体中文](../../zh-CN/user-guide/calendars.md) · [Manual home](../README.md)

## Entry and workflow

Calendars → New/Edit. ADMIN can create, edit and delete configuration. OPERATOR and VIEWER can read summaries but cannot modify configuration. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

After saving, select it in the schedule’s Calendar (optional) field. Check weekdays, weekends, exception dates and time boundaries separately.

### Calendar fields

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Name | Required | Empty | Always | Describe the business period, for example business-days. |
| Description | Optional | Empty | Always | Describe calendar usage. |
| Weekly workdays | Required | Monday to Friday | Always | Set allowed weekdays; exception dates take priority. |
| Date | Required | Today in the business timezone | Exception-date row | Use business-zone dates without duplicates. |
| Type | Initialized | New exceptions default to Holiday | Exception-date row | Choose Holiday or Workday to override the weekly rule. |

### Actions

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Save | ADMIN | Name, weekday rules and exception dates are valid | Save the calendar; a calendar does not create triggers by itself. |
| Date preview | ADMIN / OPERATOR / VIEWER | Calendar selected and preview range valid | Calculate allowed dates using saved rules; this does not indicate actual scheduled execution. |

## Use and verify

Exclude a future date and inspect allowed/excluded results in schedule preview. Review referencing schedules before changing a shared calendar; test outage recovery because filtering and catch-up interact.
