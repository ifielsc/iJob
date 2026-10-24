# Configuration export, mapping and import

[简体中文](../../zh-CN/user-guide/import-export.md) · [Manual home](../README.md)

## Entry and workflow

Task/workflow lists → Export selected/Import package. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `导出范围` | Select up to 100 tasks/jobs, at most 500 expanded objects; choose resource references/configuration without credentials. |
| `调度/通知/并发` | Optional schedules/notification bindings; task concurrency moves with tasks, global plugin policies do not. |
| `上传/映射` | Format-1 .ijob.zip up to 16 MiB; map existing/new targets and supply target credentials. |
| `差异确认/应用` | Review endpoints/resources/references before applying. Imported tasks/jobs/schedules default disabled. |
| `记录/继续导入` | Review account-scoped history/completed objects; resolve uncertain outcomes before resubmitting. |

## Use and verify

Practice in a fresh target environment: map read-only resources, import one SCRIPT, verify versions/parameters/disabled state, then enable and run manually. Reports exclude credentials. Resolve remaining items from history after failures; partial completion is not full success.
