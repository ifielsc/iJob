# User configuration

[简体中文](../zh-CN/configuration.md) · [Manual home](README.md)

Settings live in IJOB_HOME/application.properties, created with restricted permissions by setup. Do not unpack/edit the JAR or rebuild to change settings. This file contains password hashes, database credentials and the AES key; never upload, screenshot or share it as an example. Stop this installation and back up first, edit existing keys instead of adding duplicates, then restart and verify readiness and behavior.

List supported user adjustments for this version:

```sh
java -jar ijob-setup-tool-1.0.0.jar settings --lang en
java -jar ijob-setup-tool-1.0.0.jar check-settings --home /absolute/installation-a --lang en
```

On Windows use the actual absolute home path. Validation checks documented formats and duplicate keys only. It does not connect to the database, validate advanced keys or infer effective command-line/environment overrides. It never prints actual configuration values. Application startup validation and runtime acceptance remain necessary.

For example, change the native port and shared HTTP timeouts:

```properties
server.port=18082
app.http.connect-timeout=10s
app.http.read-timeout=60s
```

The following table is generated from the bilingual settings catalog. Ordinary operators do not need to adjust internal coordination or migration parameters.

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
| `app.notification.locale` | locale | en | Default notification language; notification-specific choices can override it. |
| `app.event.ingress.enabled` | boolean | false | Enable event ingress; authentication and EVENT_PUBLISH permission still apply. |
| `app.retention.enabled` | boolean | false | Enable history retention only after reviewing the retention rules. |
| `app.retention.dry-run` | boolean | true | Keep true for a dry run; false permits deletion when retention is enabled. |
| `ijob.logging.max-file-size` | size | 10MB | Native log rotation size; Docker logging is configured in Compose. |
| `ijob.logging.max-history` | positive-int | 14 | Native log archive retention in days. |
| `ijob.logging.total-size-cap` | size | 200MB | Native archive size cap; may remove archives before the day limit. |

<!-- /SETTINGS_TABLE -->

## Application and maintenance boundaries

The external file overrides packaged defaults. Explicit launcher arguments, JVM properties and applicable environment variables can still override it. Avoid maintaining the same value in multiple places. Restart after editing; ordinary configuration files are not hot-reloaded.

Docker keeps server.port=8080 and address 0.0.0.0 inside backend. Change published ports, limits, log rotation, mounts and container environment in IJOB_HOME/compose.yaml or its deployment environment, then run docker compose ... up -d for the same project. If your editor replaced a mounted file, use up -d --force-recreate backend to rebind it. Do not delete data volumes.

Manage tasks, jobs, resources, schedules, calendars, notifications and parameter templates through the UI; these usually require no restart. Global switches do not replace business configuration.

Authentication changes, database-product migration, key rotation and cluster setup are separate procedures described in [operations](operations.md). Preserve the original AES key. Do not bypass errors by rerunning setup, weakening migration safeguards or clearing database records.
