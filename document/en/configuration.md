# User configuration: location, levels and activation

[简体中文](../zh-CN/configuration.md) · [Manual home](README.md)

## Distinguish three configuration layers

| Layer | Where to edit | Activation |
| --- | --- | --- |
| Platform startup settings | IJOB_HOME/application.properties | Native restart; Docker legacy/start-docker.sh --recreate |
| Host/container settings | Environment variables and this home's compose.yaml | Restart the process or recreate containers |
| Tasks/jobs/resources/schedules | Management pages | Save/publish/enable as documented; usually no platform restart |

The external file overrides bundled defaults, but command-line arguments, JVM properties and applicable environment variables may override it. Maintain each key in one place. Prefer `/` for Windows paths in properties; backslashes require properties escaping. Replace placeholder paths before use.

## Read by risk and purpose

| Level | Recommendation | Entry point |
| --- | --- | --- |
| A Common | Users may adjust for their workload | [Common](configuration/common.md), [routing](configuration/routing.md), [plugins](configuration/plugins.md) |
| B Advanced | Adjust after understanding capacity and dependencies | [Scheduling/retention](configuration/scheduling-retention.md), [capacity](configuration/capacity.md), [deployment/Agent](configuration/deployment-agent.md) |
| C Controlled | Change through backup, implementation and verification | [Controlled settings](configuration/controlled.md) and operations workflows |
| D Internal | Not recommended as ordinary user settings | [Internal appendix](configuration/internals.md) |

The [complete catalog](configuration-catalog.md) retains 254 entries with sources, defaults and levels; users do not need to fill every entry. Setup overrides some source defaults, including native port 18080, group default and enabled scheduling. Source YAML is not the installed configuration.

## Change one setting safely

1. Identify the node, program version and IJOB_HOME; back up private configuration.
2. Read scope/dependencies and edit only the required keys without duplicates.
3. Run the checker below from the bundle directory, then restart as documented for the platform.
4. Verify the effect using the dashboard or a small task; restore the old config and restart if necessary.

```sh
java -jar ijob-setup-tool-1.0.0.jar settings --lang en
java -jar ijob-setup-tool-1.0.0.jar check-settings --home /absolute/ijob-home --lang en
```

Use your absolute Windows path when applicable. The checker lists/checks formats, duplicate keys and selected bounds for the following 54 settings without printing secrets. **It does not establish database connectivity, valid role coverage, routing eligibility, directory permissions or validation of all 254 entries.** Other advanced settings still require startup validation and actual exercises. See each chapter for empty-list semantics.

<!-- SETTINGS_TABLE -->

| Setting | Type | Fresh-install default | Description |
| --- | --- | --- | --- |
| `server.address` | text | 127.0.0.1 | Native listen address; Docker keeps 0.0.0.0 inside the container. |
| `server.port` | port | 18080 | Native HTTP port; Docker keeps 8080 and changes the published host port. |
| `spring.datasource.url` | text | generated | Database URL created during setup. Changing database products requires a migration plan. |
| `spring.datasource.username` | text | generated | Database account; not used for SQLite. Never put credentials in a URL. |
| `spring.datasource.password` | secret | generated | Edit only in the private application.properties; never pass on the command line. |
| `spring.datasource.hikari.maximum-pool-size` | positive-int | 4 | Connection pool size; coordinate with the database connection budget. |
| `app.schedule.enabled` | boolean | true | Global schedule scanner; individual schedules still need to be enabled in the UI. |
| `app.plugin.etl.enabled` | boolean | true | Enable ETL tasks. |
| `app.plugin.batch-etl.enabled` | boolean | true | Enable batch ETL tasks. |
| `app.plugin.local-jar.enabled` | boolean | false | Enable trusted Local JAR plugins only after preparing their directory and dependencies. |
| `app.plugin.local-jar.root-directory` | text | empty | Absolute plugin directory on the executing node; Docker requires an explicit mount. |
| `app.resource-browser.local-roots` | text | empty | Comma-separated absolute roots allowed for browsing LOCAL resource drafts. |
| `app.http.connect-timeout` | duration | 5s | Connection timeout for the shared HTTP client, not every external plugin. |
| `app.http.read-timeout` | duration | 30s | Read timeout for the shared HTTP client. |
| `app.notification.locale` | notification-locale | en | Default notification language; notification-specific choices can override it. |
| `app.event.ingress.enabled` | boolean | false | Enable event ingress; authentication and EVENT_PUBLISH permission still apply. |
| `app.retention.enabled` | boolean | false | Enable history retention only after reviewing the retention rules. |
| `app.retention.dry-run` | boolean | true | Keep true for a dry run; false permits deletion when retention is enabled. |
| `ijob.logging.max-file-size` | size | 10MB | Native log rotation size; Docker logging is configured in Compose. |
| `ijob.logging.max-history` | positive-int | 14 | Native log archive retention in days. |
| `ijob.logging.total-size-cap` | size | 200MB | Native archive size cap; may remove archives before the day limit. |
| `spring.web.locale` | locale | en | Server fallback locale; Accept-Language and frontend preference also apply. |
| `app.scheduling.pool-size` | positive-int | 8 | Internal scheduled-scanner threads; not the maximum business-task concurrency. |
| `app.runtime.instance-name` | text | generated | Node identity; use distinct names in a cluster for traceability. |
| `app.runtime.roles` | roles | API,SCHEDULER,WORKER,MAINTENANCE | API/SCHEDULER/WORKER/MAINTENANCE responsibilities; retain coverage of required roles. |
| `app.runtime.groups` | groups | default | Worker groups match published definitions; empty worker groups do not mean all groups. |
| `app.runtime.allowed-task-ids` | ids | empty | Task IDs eligible as root trigger targets; jointly restrict scope with the job allowlist. |
| `app.runtime.allowed-job-ids` | ids | empty | Job IDs eligible as root trigger targets; not an individual authorization list for every descendant task. |
| `app.runtime.work-queue.max-inflight-roots` | positive-int | 20 | In-flight root-run limit per node; size with executors, concurrency controls and capacity. |
| `app.schedule.zone-id` | zone | Asia/Shanghai | Default scheduling time zone; distinct from display time zone. Check individual schedules. |
| `app.schedule.misfire-policy` | misfire | FIRE_ONCE | Default missed-fire policy SKIP/FIRE_ONCE/CATCH_UP_BOUNDED; may create real catch-up runs. |
| `app.schedule.misfire-grace` | nonnegative-duration | 60s | Grace period for missed fires; verify alongside catch-up policy. |
| `app.schedule.max-catchup-window` | duration | 24h | Maximum catch-up lookback; bounds recovery bursts. |
| `app.schedule.max-catchup-count` | positive-int | 10 | Maximum catch-up count; applies with the lookback window. |
| `app.retention.interval` | duration | 1h | History cleanup: Run interval. Validate against load, recovery or cleanup policy before tuning. |
| `app.retention.batch-size` | positive-int | 500 | History cleanup: Batch size. Validate against load, recovery or cleanup policy before tuning. |
| `app.retention.max-batches-per-run` | positive-int | 10 | History cleanup: Maximum batches per run. Validate against load, recovery or cleanup policy before tuning. |
| `app.retention.execution` | duration | 90d | Execution history retention duration; applies with cleanup enabled and dry-run settings. |
| `app.retention.notification` | duration | 90d | Notification history retention duration; applies with cleanup enabled and dry-run settings. |
| `app.retention.event` | duration | 180d | Event history retention duration; applies with cleanup enabled and dry-run settings. |
| `app.retention.app-run` | duration | 30d | Node-run history retention duration; applies with cleanup enabled and dry-run settings. |
| `app.retention.audit` | duration | 90d | Audit history retention duration; applies with cleanup enabled and dry-run settings. |
| `app.execution.shutdown-timeout` | duration | 30s | Executor shutdown wait; coordinate with container grace and Spring shutdown limits. |
| `app.plugin.event.enabled` | boolean | true | event plugin switch on this node; review published tasks and active work before disabling. |
| `app.plugin.script.enabled` | boolean | true | script plugin switch on this node; review published tasks and active work before disabling. |
| `app.plugin.sql.enabled` | boolean | true | sql plugin switch on this node; review published tasks and active work before disabling. |
| `app.plugin.file-transfer.enabled` | boolean | true | file-transfer plugin switch on this node; review published tasks and active work before disabling. |
| `app.plugin.api-call.enabled` | boolean | true | api-call plugin switch on this node; review published tasks and active work before disabling. |
| `app.plugin.push.enabled` | boolean | true | push plugin switch on this node; review published tasks and active work before disabling. |
| `app.plugin.data-check.enabled` | boolean | true | data-check plugin switch on this node; review published tasks and active work before disabling. |
| `app.plugin.server-monitor.enabled` | boolean | true | server-monitor plugin switch on this node; review published tasks and active work before disabling. |
| `sa-token.timeout` | session-seconds | 1800 | Total session token lifetime in seconds; tune to session policy. |
| `sa-token.active-timeout` | session-seconds | 300 | Idle session timeout in seconds; distinct from total lifetime. |
| `app.notify.email.enabled` | boolean | true | Email channel implementation switch, enabled when absent; SMTP and credentials belong in mail-channel UI. |

<!-- /SETTINGS_TABLE -->

## Launch settings and database connections

Program/data paths and launch language belong in [instance.conf](installation/instance-launcher.md), separate from application.properties. Platform DB properties apply to standalone and cluster nodes; plugin business DBs are configured as resources. See [Platform database](configuration/platform-database.md).
