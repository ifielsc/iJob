# Runtime variables and expressions

[简体中文](../../zh-CN/reference/variables.md) · [Manual home](../README.md)

Applies to 1.0.0. Follow [parameters and inputs](../user-guide/parameters.md) for configuration. Platform variables, native script environment variables and SQL bound parameters are different mechanisms.

## Built-in variables

The `ijob.` prefix is reserved. User inputs and outputs cannot use it. Availability depends on the execution context.

| Variable | Meaning | Availability / format |
| --- | --- | --- |
| `ijob.instance.id` | Current instance ID | Instance has an ID; number |
| `ijob.instance.rootId` | Root instance ID | Recorded root exists; number |
| `ijob.trigger.id` | Trigger ID | Valid trigger exists; number |
| `ijob.trigger.type` | Trigger type | Valid trigger exists; enum name |
| `ijob.trigger.time` | UTC trigger time | ISO local date-time string without an offset; interpret it as UTC |
| `ijob.trigger.date` | UTC trigger date | ISO date string |
| `ijob.business.date` | Date in the business zone | Complete business context was recorded |
| `ijob.business.time` | Business time | Complete business context; ISO offset date-time string |
| `ijob.business.zoneId` | Business time zone | Recorded with business time |
| `ijob.schedule.id` | Schedule ID | Trigger has a schedule; not guaranteed for manual execution |

The execution context additionally injects built-ins; they are not user input entries.

Retries do not infer missing business context for older triggers. Display, machine and business time zones have different purposes; see [schedules](../user-guide/schedules.md).

## Fields supporting variable resolution

| Syntax | Behavior |
| --- | --- |
| `${limit}` | Replace a present non-null variable; retain the placeholder when missing |
| `${limit!}` | Fail if the value is missing or null |
| `${region:default}` | Use the text default when missing or null |
| `$${HOME}` | In script text only, preserve native `${HOME}` for the script engine |

These forms apply only to fields using variable resolution. Gateway conditions, JSONPath, Cron and file patterns use their own syntax. Defaults are text and do not automatically become integers.

For fields supporting object resolution, a standalone placeholder can preserve the JSON type. An embedded placeholder produces text: `${limit}` may remain an integer, while `limit=${limit}` is a string.

## SCRIPT, SQL and ETL differ

| Location | Example | Rule |
| --- | --- | --- |
| SCRIPT text | `echo "limit=${limit}"` | Text interpolation before writing the script; limit must be an integer in this example |
| Native SHELL variable | `echo "$${HOME}"` | Removes the extra dollar sign and leaves `${HOME}` to the shell |
| SCRIPT output | `echo __VAR__:rows=3` | Publishes the string `"3"`; the protocol does not parse it as a JSON number |
| SQL plugin | `SELECT id FROM orders WHERE id <= ${limit};` | Text replacement, not prepared-statement binding; use only a constrained integer here |
| ETL database query | `SELECT id FROM orders WHERE id <= :limit` | Configure limit in query parameters for driver binding; do not quote :limit in SQL |

ETL query parameters can map limit to `${limit}`. Cast date values explicitly for the database, for example PostgreSQL `CAST(:batchDate AS date)`. Bound parameters cannot replace SQL identifiers such as table or column names.

SCRIPT working-directory and environment-variable settings are static and do not perform platform `${...}` interpolation.

Interpolating arbitrary strings can change script or SQL semantics. Constrain types and handle quoting for the actual interpreter/database. Keep secrets in resources, not ordinary variables or logs.

## Input snapshots and output merging

- Execution values belong to that request and do not update templates or saved schedules.
- Saved schedules do not automatically follow template updates; Reload replaces the current editing draft.
- Upstream outputs can replace same-name base inputs. Duplicate output names from multiple upstream nodes cause an ambiguity conflict rather than depending on completion order.
- A null upstream output removes the key. Use distinct, explicit names for downstream values.

## Limits

| Object | Limit |
| --- | --- |
| User input / output snapshots | 100 entries, names up to 128 characters |
| Input and variable snapshots | 65,536 bytes of UTF-8 JSON |
| Parameter types | STRING, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME, ENUM, OBJECT, ARRAY |
| Sensitive parameters | No saved default values; variables are not a secret vault |
| SCRIPT output variables | 100 values, 16 KiB per value; see the script reference for other output/log limits |

## Related pages

[Scripts](../user-guide/plugins/script.md) · [SQL](../user-guide/plugins/sql.md) · [ETL](../user-guide/plugins/etl.md) · [API and parameters tutorial](../examples/api-and-parameters.md)
