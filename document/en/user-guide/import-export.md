# Configuration export, mapping and import

[简体中文](../../zh-CN/user-guide/import-export.md) · [Manual home](../README.md)

## Entry and workflow

Task/workflow lists → Export selected/Import package. Both import and export require ADMIN. OPERATOR and VIEWER cannot generate or apply configuration packages. Imports must also satisfy dependency, name-conflict and resource-mapping checks. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

### Export and mapping fields

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Export selected | Required | Current selection | Task/job list | At most 100 roots and 500 expanded objects; shared dependencies are deduplicated. |
| Resource handling | Initialized | Include configuration (without credentials) | Export wizard | Alternatively export references only. Passwords, keys and tokens are excluded. |
| Include schedules | Initialized | Selected | Export wizard | Clear if unnecessary; newly imported schedules default disabled. |
| Include notification bindings | Initialized | Selected | Export wizard | Export bindings and channel references; map existing channels in the target environment. |
| Action | Required | Detected from package and target environment | Dependency mapping | Choose supported create, reuse or update actions; resolve conflicts explicitly. |
| Target object | Required for reuse/update | Empty | Dependency mapping | Choose a compatible object and verify its environment. |
| Target environment credentials | According to target resource authentication | Empty | Creating/updating resources requiring authentication | Held in the current draft, then encrypted in the target environment; excluded from reports. |

### Actions

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Validate file | ADMIN | Format-1 .ijob.zip file, at most 16 MiB | Read the package and proceed to mapping; objects are not yet imported. |
| Check differences | ADMIN | Required mappings and target configuration are complete | Compare fields, references and target environment before/after import. |
| Confirm import | ADMIN | Checks pass and target environment/resource configuration has been reviewed | Apply explicit choices. Created/updated tasks/jobs and new schedules default disabled. |
| Continue import | ADMIN | Incomplete record for this account; refresh to verify its actual state first | Continue unfinished objects without recreating completed ones; recheck after configuration changes. |

### Page information

| Display | Meaning | Shown when |
| --- | --- | --- |
| Import history | Account-scoped history. Resolve an uncertain outcome before resubmitting the whole package. | Always |
| Task concurrency policy | Moves with tasks. Global plugin concurrency policy is excluded; shared resources follow the resource-export option. | Always |

## Use and verify

Practice in a fresh target environment: map read-only resources, import one SCRIPT, verify versions/parameters/disabled state, then enable and run manually. Reports exclude credentials. Resolve remaining items from history after failures; partial completion is not full success.
