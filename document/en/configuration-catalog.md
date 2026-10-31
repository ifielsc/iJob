# Complete configuration inventory

[简体中文](../zh-CN/configuration-catalog.md) · [Manual home](README.md)

Applies to **iJob 1.0.0**. This inventory groups configuration sources, defaults, and change procedures by A/B/C/D level. The settings/check-settings tool covers 54 entries. Source defaults and fresh-install values are described separately; configuration sources are listed at the end.

## 1. Scope and reading guide

This inventory covers platform and Agent properties declared in the listed sources, plus startup variables, image locks and common Compose controls. It does not enumerate every Spring, JVM, OS or Nginx option. Test-only settings, generated installation.properties/PID markers are not user tuning options. UI business fields for tasks/jobs/resources/notifications belong in the operations reference, separately from application.properties.

Properties are identified by component plus key: both platform and Agent have server.port. Environment aliases are entry points for the same property, not separate features. `<service>` in Compose rows is a template placeholder, not a literal service name.

| Level | Recommended adjustment policy | Documentation treatment |
| --- | --- | --- |
| A Common | Deployment administrators may adjust as needed; this does not grant all logged-in users server access | Main reference with examples, restart and verification |
| B Advanced | Administrators with deployment/capacity knowledge may tune with related constraints | Advanced reference with verification and failure symptoms |
| C Controlled | Change through a specific operations procedure, including accounts, credentials, databases and mode changes | Dedicated steps, backup and recovery requirements; not a prohibition |
| D Internal | Not recommended for routine user tuning | Complete appendix with rationale; no recipes for bypassing safeguards |

A/B/C/D levels describe configuration purpose and change requirements; they do not grant server or page permissions. settings/check-settings covers 54 format checks; see [Configuration](configuration.md). It does not validate all 254 entries or cross-setting business constraints.

### Defaults and activation

- Source declarations preserve expressions such as `${VARIABLE:fallback}`. A declaration does not apply unless its file is loaded. Database-specific values apply only to the selected vendor. The mail-profile declaration applies when that profile loads, while the email implementation itself is enabled when the switch is absent.
- Fresh-install overrides distinguish setup and deployment modes. No extra override means the loaded configuration/implementation default applies; not every implicit framework default is enumerated. Java field initializers are not automatically installation defaults.
- Restart the installation after platform application.properties changes. For Docker, use the corresponding instance’s ijob.sh restart to refresh mounts and configuration. Restart Agent for Agent changes. Build variables apply only when rebuilding images/frontend.
- Some IJOB_* names are YAML placeholder inputs or script inputs, so they do not universally override external files. Explicit launch arguments, system properties and directly bound environment variables also influence effective values. Avoid maintaining competing copies.
- Secret entries show only variable names or generation rules, never live values. Ordinary runtime-configuration edits require no program rebuild. After saving the configuration, follow the relevant chapter to restart or recreate the affected process or containers.

### Types

duration includes a unit; seconds/milliseconds/days use their stated units; bytes is a byte count; size uses implementation-supported capacity units. Follow each row for enums/lists. Normalized groups allow at most 16 entries matching `[a-z0-9][a-z0-9._-]{0,63}`. Task/job lists each allow at most 100 positive IDs. Startup validation still enforces relational constraints; a type alone is not a complete range specification.

## 2. Behavior and scope

### Groups and task/job allowlists

Worker groups match published-definition groups. A definition without groups imposes no group restriction; a grouped definition requires at least one worker-group match. Empty worker groups therefore only match unrestricted definitions. Setup writes default for the node; it does not automatically assign default to every task.

| Task allowlist | Job allowlist | Eligible root trigger targets, subject to groups and other conditions |
| --- | --- | --- |
| Empty | Empty | No target-ID restriction |
| 101 | Empty | Task 101 only; no job root triggers |
| Empty | 201 | Job 201 only; no task root triggers |
| 101 | 201 | Task 101 or job 201 |

This filters persistent work-queue claims for root triggers. It is not login authorization, a sandbox for every plugin, or per-descendant DAG authorization. Example IDs must be replaced with real objects in the installation. Both groups and allowlists must match; also check WORKER role, plugin availability and business configuration.

### Default differences and deployment entry points

- app.runtime.groups: empty in source; default written by setup.
- app.schedule.enabled: false in source; true after setup. Individual schedules remain independently enabled/disabled.
- server.port: source 8080; fresh native 18080; container internal 8080; default host mapping 18080.
- Base images, advanced repository Compose and setup-generated Compose are different layers. IJOB_HTTP_BIND is only read by the advanced template; setting it alone does not change a first-run bundle's binding.
- Agent also supports agent.hostname from its Java binding class even though the source YAML does not explicitly declare it. It is included here.

## 3. Configuration index

**254 entries**: 190 platform properties, 8 Agent properties, 26 startup/build/lock entries 25 Compose fields and 5 instance-launcher fields. Levels: A 43, B 75, C 31, D 105. Counts include deployment entry points and aliases, not 254 independent business features.

- [Nodes, groups and execution scope](#routing) — 9
- [All plugin switches and Local JAR](#plugins) — 17
- [Database, pool and formal migrations](#database) — 21
- [Accounts, sessions and encryption keys](#auth) — 16
- [Schedules and events](#schedule) — 14
- [Notifications and email implementation](#notification) — 9
- [History retention](#retention) — 11
- [Execution capacity and scanner threads](#pools) — 23
- [Internal coordination, recovery and consistency](#coordination) — 28
- [Access, resource browsing, HTTP and locale](#access) — 8
- [Logging and management probes](#logging) — 14
- [Launcher integration and framework contracts](#framework) — 20
- [Standalone monitoring Agent](#agent) — 8
- [Startup, image-lock and development variables](#deployment) — 26
- [Supplementary Compose controls](#compose) — 25

<a id="routing"></a>

## Nodes, groups and execution scope

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C035<br>`app.runtime.mode`<br>enum | `${IJOB_RUNTIME_MODE:STANDALONE}` (S01) | STANDALONE | `IJOB_RUNTIME_MODE` | **C** Use the operations procedure for STANDALONE/CLUSTER changes; DEVELOPMENT is not a deployment mode. | Platform; restart installation |
| C036<br>`app.runtime.instance-name`<br>text | `${IJOB_RUNTIME_INSTANCE_NAME:}` (S01) | setup-&lt;random UUID&gt; | `IJOB_RUNTIME_INSTANCE_NAME` | **A** Node identity; use distinct names in a cluster for traceability. | Platform; restart installation |
| C037<br>`app.runtime.roles`<br>enum-list | `${IJOB_RUNTIME_ROLES:API,SCHEDULER,WORKER,MAINTENANCE}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RUNTIME_ROLES` | **B** API/SCHEDULER/WORKER/MAINTENANCE responsibilities; retain coverage of required roles. | Platform; restart installation |
| C038<br>`app.runtime.groups`<br>text-list | `${IJOB_RUNTIME_GROUPS:}` (S01) | default | `IJOB_RUNTIME_GROUPS` | **A** Worker groups match published definitions; empty worker groups do not mean all groups. | Platform; restart installation |
| C039<br>`app.runtime.allowed-task-ids`<br>id-list | `${IJOB_RUNTIME_ALLOWED_TASK_IDS:}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RUNTIME_ALLOWED_TASK_IDS` | **A** Task IDs eligible as root trigger targets; jointly restrict scope with the job allowlist. | Platform; restart installation |
| C040<br>`app.runtime.allowed-job-ids`<br>id-list | `${IJOB_RUNTIME_ALLOWED_JOB_IDS:}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RUNTIME_ALLOWED_JOB_IDS` | **A** Job IDs eligible as root trigger targets; not an individual authorization list for every descendant task. | Platform; restart installation |
| C041<br>`app.runtime.singleton-lock-name`<br>text | `${IJOB_SINGLETON_LOCK_NAME:ijob:standalone}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SINGLETON_LOCK_NAME` | **D** Standalone lock identity; changing it can defeat same-database singleton protection. | Platform; restart installation |
| C042<br>`app.runtime.heartbeat-interval`<br>duration | `${IJOB_RUNTIME_HEARTBEAT_INTERVAL:5s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RUNTIME_HEARTBEAT_INTERVAL` | **D** Node heartbeat interval; coupled to failure detection and coordination timing. | Platform; restart installation |
| C043<br>`app.runtime.id-segment-size`<br>integer | `${IJOB_ID_SEGMENT_SIZE:1000}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_ID_SEGMENT_SIZE` | **D** Runtime ID allocation segment size; not a schema or protocol version. | Platform; restart installation |

<a id="plugins"></a>

## All plugin switches and Local JAR

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C125<br>`app.plugin.event.enabled`<br>boolean | `${IJOB_PLUGIN_EVENT_ENABLED:true}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_EVENT_ENABLED` | **A** event plugin switch on this node; review published tasks and active work before disabling. | Platform; restart installation |
| C126<br>`app.plugin.script.enabled`<br>boolean | `${IJOB_PLUGIN_SCRIPT_ENABLED:true}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_SCRIPT_ENABLED` | **A** script plugin switch on this node; review published tasks and active work before disabling. | Platform; restart installation |
| C127<br>`app.plugin.sql.enabled`<br>boolean | `${IJOB_PLUGIN_SQL_ENABLED:true}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_SQL_ENABLED` | **A** sql plugin switch on this node; review published tasks and active work before disabling. | Platform; restart installation |
| C128<br>`app.plugin.file-transfer.enabled`<br>boolean | `${IJOB_PLUGIN_FILE_TRANSFER_ENABLED:true}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_FILE_TRANSFER_ENABLED` | **A** file-transfer plugin switch on this node; review published tasks and active work before disabling. | Platform; restart installation |
| C129<br>`app.plugin.api-call.enabled`<br>boolean | `${IJOB_PLUGIN_API_CALL_ENABLED:true}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_API_CALL_ENABLED` | **A** api-call plugin switch on this node; review published tasks and active work before disabling. | Platform; restart installation |
| C130<br>`app.plugin.push.enabled`<br>boolean | `${IJOB_PLUGIN_PUSH_ENABLED:true}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_PUSH_ENABLED` | **A** push plugin switch on this node; review published tasks and active work before disabling. | Platform; restart installation |
| C131<br>`app.plugin.data-check.enabled`<br>boolean | `${IJOB_PLUGIN_DATA_CHECK_ENABLED:true}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_DATA_CHECK_ENABLED` | **A** data-check plugin switch on this node; review published tasks and active work before disabling. | Platform; restart installation |
| C132<br>`app.plugin.server-monitor.enabled`<br>boolean | `${IJOB_PLUGIN_SERVER_MONITOR_ENABLED:true}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_SERVER_MONITOR_ENABLED` | **A** server-monitor plugin switch on this node; review published tasks and active work before disabling. | Platform; restart installation |
| C133<br>`app.plugin.etl.enabled`<br>boolean | `${IJOB_PLUGIN_ETL_ENABLED:true}` (S01) | true (explicitly written by setup) | `IJOB_PLUGIN_ETL_ENABLED` | **A** etl plugin switch on this node; review published tasks and active work before disabling. | Platform; restart installation |
| C134<br>`app.plugin.batch-etl.enabled`<br>boolean | `${IJOB_PLUGIN_BATCH_ETL_ENABLED:true}` (S01) | true (explicitly written by setup) | `IJOB_PLUGIN_BATCH_ETL_ENABLED` | **A** batch-etl plugin switch on this node; review published tasks and active work before disabling. | Platform; restart installation |
| C135<br>`app.plugin.local-jar.enabled`<br>boolean | `${IJOB_PLUGIN_LOCAL_JAR_ENABLED:false}` (S01) | false (explicitly written by setup) | `IJOB_PLUGIN_LOCAL_JAR_ENABLED` | **A** local-jar plugin switch on this node; review published tasks and active work before disabling. Also provide trusted JARs, dependencies and directory. | Platform; restart installation |
| C136<br>`app.plugin.local-jar.root-directory`<br>path | `${IJOB_PLUGIN_LOCAL_JAR_ROOT:}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_LOCAL_JAR_ROOT` | **A** Absolute trusted Local JAR directory; mount it explicitly in containers. | Platform; restart installation |
| C137<br>`app.plugin.local-jar.max-jar-bytes`<br>bytes | `${IJOB_PLUGIN_LOCAL_JAR_MAX_BYTES:67108864}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_LOCAL_JAR_MAX_BYTES` | **B** Maximum JAR file bytes; assess memory and archive safety before tuning. | Platform; restart installation |
| C138<br>`app.plugin.local-jar.max-jar-entries`<br>integer | `${IJOB_PLUGIN_LOCAL_JAR_MAX_ENTRIES:10000}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_LOCAL_JAR_MAX_ENTRIES` | **B** Maximum JAR entries; assess memory and archive safety before tuning. | Platform; restart installation |
| C139<br>`app.plugin.local-jar.max-expanded-bytes`<br>bytes | `${IJOB_PLUGIN_LOCAL_JAR_MAX_EXPANDED_BYTES:268435456}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_LOCAL_JAR_MAX_EXPANDED_BYTES` | **B** Maximum expanded bytes; assess memory and archive safety before tuning. | Platform; restart installation |
| C140<br>`app.plugin.local-jar.max-compression-ratio`<br>integer | `${IJOB_PLUGIN_LOCAL_JAR_MAX_COMPRESSION_RATIO:100}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_LOCAL_JAR_MAX_COMPRESSION_RATIO` | **B** Maximum compression ratio; assess memory and archive safety before tuning. | Platform; restart installation |
| C141<br>`app.plugin.local-jar.max-cached-loaders`<br>integer | `${IJOB_PLUGIN_LOCAL_JAR_MAX_CACHED_LOADERS:32}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLUGIN_LOCAL_JAR_MAX_CACHED_LOADERS` | **B** Cached classloader limit; assess memory and archive safety before tuning. | Platform; restart installation |

<a id="database"></a>

## Database, pool and formal migrations

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C012<br>`spring.flyway.enabled`<br>boolean | `${IJOB_FLYWAY_ENABLED:false}` (S01) | true | `IJOB_FLYWAY_ENABLED` | **D** Formal runtime requires Flyway validation; never disable to bypass incompatibility. | Platform; restart installation |
| C013<br>`spring.flyway.locations`<br>text | `classpath:db/release/${IJOB_PLATFORM_DB:mysql}` (S01) | classpath:db/release/&lt;vendor&gt; | `IJOB_PLATFORM_DB` | **D** Select formal V1 migration locations by database. | Platform; restart installation |
| C014<br>`spring.flyway.table`<br>text | `ifj_schema_history` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Formal migration history table identity. | Platform; restart installation |
| C015<br>`spring.flyway.baseline-on-migrate`<br>boolean | `false` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Prevent implicit baselining of an unknown old database. | Platform; restart installation |
| C016<br>`spring.flyway.validate-on-migrate`<br>boolean | `true` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Retain validation against migration history drift. | Platform; restart installation |
| C017<br>`spring.flyway.clean-disabled`<br>boolean | `true` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Keep database-clean protection enabled. | Platform; restart installation |
| C025<br>`app.platform-database.vendor`<br>enum | `${IJOB_PLATFORM_DB:mysql}` (S01) | Selected during setup | `IJOB_PLATFORM_DB` | **C** Database vendor; align driver, migration location, installation metadata and startup environment. | Platform; restart installation |
| C164<br>`spring.datasource.hikari.connection-init-sql`<br>text | `SET time_zone = '+00:00'` (S03)<br>`ALTER SESSION SET TIME_ZONE = 'UTC'` (S04)<br>`SET TIME ZONE 'UTC'` (S05)<br>`PRAGMA foreign_keys = ON` (S06)<br>`SET DATEFORMAT ymd` (S07) | No extra setup override; use loaded configuration/implementation default | — | **D** Database session initialization for UTC, date parsing or foreign keys. | Platform; restart installation |
| C165<br>`pagehelper.helper-dialect`<br>text | `mysql` (S03)<br>`oracle` (S04)<br>`postgresql` (S05)<br>`sqlite` (S06)<br>`sqlserver2012` (S07) | No extra setup override; use loaded configuration/implementation default | — | **D** Pagination dialect must match database vendor. | Platform; restart installation |
| C166<br>`spring.datasource.driver-class-name`<br>class | `oracle.jdbc.OracleDriver` (S04)<br>`org.sqlite.JDBC` (S06)<br>`com.microsoft.sqlserver.jdbc.SQLServerDriver` (S07) | Selected by setup for one of five supported databases | — | **D** Setup selects the matching database driver; do not change independently. | Platform; restart installation |
| C167<br>`spring.flyway.default-schema`<br>text | `${IJOB_PLATFORM_DB_SCHEMA:${spring.datasource.username:${IJOB_DB_USERNAME:ijob}}}` (S04)<br>`${IJOB_PLATFORM_DB_SCHEMA:public}` (S05)<br>`${IJOB_PLATFORM_DB_SCHEMA:dbo}` (S07) | No extra setup override; use loaded configuration/implementation default | `IJOB_DB_USERNAME`<br>`IJOB_PLATFORM_DB_SCHEMA` | **C** Migration schema; plan migration before changing an existing installation. | Platform; restart installation |
| C168<br>`app.platform-database.schema`<br>text | `${IJOB_PLATFORM_DB_SCHEMA:${spring.datasource.username:${IJOB_DB_USERNAME:ijob}}}` (S04)<br>`${IJOB_PLATFORM_DB_SCHEMA:public}` (S05)<br>`${IJOB_PLATFORM_DB_SCHEMA:dbo}` (S07) | No extra setup override; use loaded configuration/implementation default | `IJOB_DB_USERNAME`<br>`IJOB_PLATFORM_DB_SCHEMA` | **C** Platform schema; configure per vendor and align JDBC and Flyway. | Platform; restart installation |
| C170<br>`spring.datasource.hikari.schema`<br>text | `${IJOB_PLATFORM_DB_SCHEMA:public}` (S05)<br>`${IJOB_PLATFORM_DB_SCHEMA:dbo}` (S07) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLATFORM_DB_SCHEMA` | **C** Default schema; align platform/migration schemas and account permissions. | Platform; restart installation |
| C171<br>`spring.datasource.hikari.maximum-pool-size`<br>integer | `${IJOB_SQLITE_POOL_SIZE:4}` (S06) | 4 (all setup installations) | `IJOB_SQLITE_POOL_SIZE` | **B** Pool limit; coordinate with database capacity. More SQLite connections do not resolve write contention. | Platform; restart installation |
| C172<br>`spring.datasource.hikari.data-source-properties.busy_timeout`<br>milliseconds | `${IJOB_SQLITE_BUSY_TIMEOUT_MS:5000}` (S06) | No extra setup override; use loaded configuration/implementation default | `IJOB_SQLITE_BUSY_TIMEOUT_MS` | **B** Lock-busy wait in milliseconds; SQLite only, assess concurrency and durability. | SQLite only; restart installation |
| C173<br>`spring.datasource.hikari.data-source-properties.journal_mode`<br>enum | `WAL` (S06) | No extra setup override; use loaded configuration/implementation default | — | **D** WAL journal mode; SQLite only, assess concurrency and durability. | SQLite only; restart installation |
| C174<br>`spring.datasource.hikari.data-source-properties.synchronous`<br>enum | `NORMAL` (S06) | No extra setup override; use loaded configuration/implementation default | — | **D** Synchronous durability policy; SQLite only, assess concurrency and durability. | SQLite only; restart installation |
| C175<br>`spring.datasource.hikari.data-source-properties.transaction_mode`<br>enum | `IMMEDIATE` (S06) | No extra setup override; use loaded configuration/implementation default | — | **D** IMMEDIATE transaction mode; SQLite only, assess concurrency and durability. | SQLite only; restart installation |
| C183<br>`spring.datasource.url`<br>text | `generated from database selection` (S10) | Generated from selected database/input; SQLite uses this installation runtime/platform.db | — | **C** Platform database connection; changing database products requires migration, not only a URL edit. | Platform; restart installation |
| C184<br>`spring.datasource.username`<br>text | `user input; empty for SQLite` (S10) | Input; empty for SQLite | — | **C** Database account; maintain together with grants and password. | Platform; restart installation |
| C185<br>`spring.datasource.password`<br>secret | `private input; empty for SQLite` (S10) | Private input; empty for SQLite | — | **C** Database password; rotate in a restricted file and verify connectivity. | Platform; restart installation |

<a id="auth"></a>

## Accounts, sessions and encryption keys

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C071<br>`app.auth.mode`<br>enum | `${IJOB_AUTH_MODE:STATIC}` (S01) | STATIC | `IJOB_AUTH_MODE` | **C** STATIC/DATABASE account source; NONE is not for deployed installations. | Platform; restart installation |
| C072<br>`app.auth.username`<br>text | `${IJOB_AUTH_USERNAME:}` (S01) | Interactive input; no universal default account | `IJOB_AUTH_USERNAME` | **C** STATIC or initial DATABASE bootstrap account; manage normal database users in the UI. | Platform; restart installation |
| C073<br>`app.auth.password-hash`<br>secret | `${IJOB_AUTH_PASSWORD_HASH:}` (S01) | BCrypt derived from entered password; no universal default password | `IJOB_AUTH_PASSWORD_HASH` | **C** BCrypt password hash, not plaintext; generate through the password-change procedure. | Platform; restart installation |
| C074<br>`app.auth.bootstrap-enabled`<br>boolean | `${IJOB_AUTH_BOOTSTRAP_ENABLED:false}` (S01) | false | `IJOB_AUTH_BOOTSTRAP_ENABLED` | **C** Controlled first-admin bootstrap only for an empty user table; disable after completion. | Platform; restart installation |
| C075<br>`app.security.aes.key`<br>secret | `${IJOB_CONFIG_AES_KEY:}` (S01) | Randomly generated per installation | `IJOB_CONFIG_AES_KEY` | **C** Encrypts stored resource credentials; preserve in backups and never replace with an arbitrary key. | Platform; restart installation |
| C076<br>`app.security.aes.key-id`<br>text | `${IJOB_CONFIG_AES_KEY_ID:local}` (S01) | setup-key-1 | `IJOB_CONFIG_AES_KEY_ID` | **C** AES key identifier; manage as part of a complete rotation procedure. | Platform; restart installation |
| C142<br>`sa-token.token-name`<br>text | `${IJOB_AUTH_TOKEN_NAME:ijob-token}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_AUTH_TOKEN_NAME` | **C** Authentication header name; align clients, proxies and cluster nodes. | Platform; restart installation |
| C143<br>`sa-token.timeout`<br>seconds | `${IJOB_AUTH_TIMEOUT_SECONDS:1800}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_AUTH_TIMEOUT_SECONDS` | **B** Total session token lifetime in seconds; tune to session policy. | Platform; restart installation |
| C144<br>`sa-token.active-timeout`<br>seconds | `${IJOB_AUTH_ACTIVE_TIMEOUT_SECONDS:300}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_AUTH_ACTIVE_TIMEOUT_SECONDS` | **B** Idle session timeout in seconds; distinct from total lifetime. | Platform; restart installation |
| C145<br>`sa-token.is-concurrent`<br>boolean | `false` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Concurrent login policy; part of the verified authentication contract, not routine tuning. | Platform; restart installation |
| C146<br>`sa-token.is-share`<br>boolean | `false` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Session-sharing policy; part of the verified authentication contract, not routine tuning. | Platform; restart installation |
| C147<br>`sa-token.is-read-cookie`<br>boolean | `false` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Cookie-reading policy; part of the verified authentication contract, not routine tuning. | Platform; restart installation |
| C148<br>`sa-token.is-read-header`<br>boolean | `true` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Header token reading; part of the verified authentication contract, not routine tuning. | Platform; restart installation |
| C149<br>`sa-token.is-write-header`<br>boolean | `false` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Response-header token writing; part of the verified authentication contract, not routine tuning. | Platform; restart installation |
| C150<br>`sa-token.token-style`<br>text | `uuid` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Token generation style; part of the verified authentication contract, not routine tuning. | Platform; restart installation |
| C151<br>`sa-token.is-log`<br>boolean | `false` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Authentication component logging; part of the verified authentication contract, not routine tuning. | Platform; restart installation |

<a id="schedule"></a>

## Schedules and events

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C077<br>`app.schedule.enabled`<br>boolean | `${IJOB_SCHEDULE_ENABLED:false}` (S01) | true | `IJOB_SCHEDULE_ENABLED` | **A** Global schedule scanner switch; separate from per-schedule enabled state and node roles. | Platform; restart installation |
| C078<br>`app.schedule.scan-interval`<br>duration | `${IJOB_SCHEDULE_SCAN_INTERVAL:1s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCHEDULE_SCAN_INTERVAL` | **B** Schedule scanning: Scan interval. Validate against load, recovery or cleanup policy before tuning. | Platform; restart installation |
| C079<br>`app.schedule.initial-delay`<br>duration | `${IJOB_SCHEDULE_INITIAL_DELAY:5s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCHEDULE_INITIAL_DELAY` | **B** Schedule scanning: Initial delay. Validate against load, recovery or cleanup policy before tuning. | Platform; restart installation |
| C080<br>`app.schedule.scan-batch-size`<br>integer | `${IJOB_SCHEDULE_SCAN_BATCH_SIZE:100}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCHEDULE_SCAN_BATCH_SIZE` | **B** Schedule scanning: Scan batch size. Validate against load, recovery or cleanup policy before tuning. | Platform; restart installation |
| C081<br>`app.schedule.max-triggers-per-scan`<br>integer | `${IJOB_SCHEDULE_MAX_TRIGGERS_PER_SCAN:100}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCHEDULE_MAX_TRIGGERS_PER_SCAN` | **B** Maximum triggers per scan; affects throughput and recovery rate. | Platform; restart installation |
| C082<br>`app.schedule.zone-id`<br>zone-id | `${IJOB_SCHEDULE_ZONE_ID:Asia/Shanghai}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCHEDULE_ZONE_ID` | **B** Default scheduling time zone; distinct from display time zone. Check individual schedules. | Platform; restart installation |
| C083<br>`app.schedule.misfire-policy`<br>enum | `${IJOB_SCHEDULE_MISFIRE_POLICY:FIRE_ONCE}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCHEDULE_MISFIRE_POLICY` | **B** Default missed-fire policy SKIP/FIRE_ONCE/CATCH_UP_BOUNDED; may create real catch-up runs. | Platform; restart installation |
| C084<br>`app.schedule.misfire-grace`<br>duration | `${IJOB_SCHEDULE_MISFIRE_GRACE:60s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCHEDULE_MISFIRE_GRACE` | **B** Grace period for missed fires; verify alongside catch-up policy. | Platform; restart installation |
| C085<br>`app.schedule.max-catchup-window`<br>duration | `${IJOB_SCHEDULE_MAX_CATCHUP_WINDOW:24h}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCHEDULE_MAX_CATCHUP_WINDOW` | **B** Maximum catch-up lookback; bounds recovery bursts. | Platform; restart installation |
| C086<br>`app.schedule.max-catchup-count`<br>integer | `${IJOB_SCHEDULE_MAX_CATCHUP_COUNT:10}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCHEDULE_MAX_CATCHUP_COUNT` | **B** Maximum catch-up count; applies with the lookback window. | Platform; restart installation |
| C087<br>`app.event.ingress.enabled`<br>boolean | `${IJOB_EVENT_INGRESS_ENABLED:false}` (S01) | false (explicitly written by setup) | `IJOB_EVENT_INGRESS_ENABLED` | **A** Event ingress API switch; authentication and EVENT_PUBLISH permission still apply. | Platform; restart installation |
| C088<br>`app.event.recovery.enabled`<br>boolean | `${IJOB_EVENT_RECOVERY_ENABLED:true}` (S01) | true (explicitly written by setup) | `IJOB_EVENT_RECOVERY_ENABLED` | **D** Persisted-event recovery; disabling changes recovery of waiting events. | Platform; restart installation |
| C089<br>`app.event.recovery.scan-interval`<br>duration | `${IJOB_EVENT_RECOVERY_SCAN_INTERVAL:1s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_EVENT_RECOVERY_SCAN_INTERVAL` | **D** Event recovery: Scan interval. Coordination-sensitive; retain default. | Platform; restart installation |
| C090<br>`app.event.recovery.scan-batch-size`<br>integer | `${IJOB_EVENT_RECOVERY_SCAN_BATCH_SIZE:100}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_EVENT_RECOVERY_SCAN_BATCH_SIZE` | **D** Event recovery: Scan batch size. Coordination-sensitive; retain default. | Platform; restart installation |

<a id="notification"></a>

## Notifications and email implementation

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C026<br>`app.notification.locale`<br>enum | `${IJOB_NOTIFICATION_LOCALE:en}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_NOTIFICATION_LOCALE` | **A** Default notification language en/zh; separate from UI language. | Platform; restart installation |
| C027<br>`app.notification.outbox.enabled`<br>boolean | `${IJOB_NOTIFICATION_OUTBOX_ENABLED:true}` (S01) | true (explicitly written by setup) | `IJOB_NOTIFICATION_OUTBOX_ENABLED` | **D** Persistent notification delivery; required in CLUSTER, not a per-notification mute switch. | Platform; restart installation |
| C028<br>`app.notification.outbox.scan-interval`<br>duration | `${IJOB_NOTIFICATION_OUTBOX_SCAN_INTERVAL:1s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_NOTIFICATION_OUTBOX_SCAN_INTERVAL` | **B** Notification delivery: Scan interval. Validate against load, recovery or cleanup policy before tuning. | Platform; restart installation |
| C029<br>`app.notification.outbox.renew-interval`<br>duration | `${IJOB_NOTIFICATION_OUTBOX_RENEW_INTERVAL:10s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_NOTIFICATION_OUTBOX_RENEW_INTERVAL` | **D** Notification delivery: Lease renewal interval. Coordination-sensitive; retain default. | Platform; restart installation |
| C030<br>`app.notification.outbox.lease-duration`<br>duration | `${IJOB_NOTIFICATION_OUTBOX_LEASE_DURATION:30s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_NOTIFICATION_OUTBOX_LEASE_DURATION` | **D** Notification delivery: Lease duration. Coordination-sensitive; retain default. | Platform; restart installation |
| C031<br>`app.notification.outbox.retry-delay`<br>duration | `${IJOB_NOTIFICATION_OUTBOX_RETRY_DELAY:30s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_NOTIFICATION_OUTBOX_RETRY_DELAY` | **B** Notification delivery: Retry delay. Validate against load, recovery or cleanup policy before tuning. | Platform; restart installation |
| C032<br>`app.notification.outbox.scan-batch-size`<br>integer | `${IJOB_NOTIFICATION_OUTBOX_SCAN_BATCH_SIZE:100}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_NOTIFICATION_OUTBOX_SCAN_BATCH_SIZE` | **B** Notification delivery: Scan batch size. Validate against load, recovery or cleanup policy before tuning. | Platform; restart installation |
| C033<br>`app.notification.outbox.max-attempts`<br>integer | `${IJOB_NOTIFICATION_OUTBOX_MAX_ATTEMPTS:5}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_NOTIFICATION_OUTBOX_MAX_ATTEMPTS` | **B** Notification delivery: Internal processing attempt limit. Validate against load, recovery or cleanup policy before tuning. | Platform; restart installation |
| C163<br>`app.notify.email.enabled`<br>boolean | `true` (S02) | No extra setup override; use loaded configuration/implementation default | — | **B** Email channel implementation switch, enabled when absent; SMTP and credentials belong in mail-channel UI. | Platform; restart installation |

<a id="retention"></a>

## History retention

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C091<br>`app.retention.enabled`<br>boolean | `${IJOB_RETENTION_ENABLED:false}` (S01) | false (explicitly written by setup) | `IJOB_RETENTION_ENABLED` | **A** Business-history cleanup switch; review retention windows and backups before enabling. | Platform; restart installation |
| C092<br>`app.retention.dry-run`<br>boolean | `${IJOB_RETENTION_DRY_RUN:true}` (S01) | true (explicitly written by setup) | `IJOB_RETENTION_DRY_RUN` | **A** Cleanup dry run; disabling allows actual deletion when cleanup is enabled. | Platform; restart installation |
| C093<br>`app.retention.initial-delay`<br>duration | `${IJOB_RETENTION_INITIAL_DELAY:1m}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RETENTION_INITIAL_DELAY` | **B** History cleanup: Initial delay. Validate against load, recovery or cleanup policy before tuning. | Platform; restart installation |
| C094<br>`app.retention.interval`<br>duration | `${IJOB_RETENTION_INTERVAL:1h}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RETENTION_INTERVAL` | **B** History cleanup: Run interval. Validate against load, recovery or cleanup policy before tuning. | Platform; restart installation |
| C095<br>`app.retention.batch-size`<br>integer | `${IJOB_RETENTION_BATCH_SIZE:500}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RETENTION_BATCH_SIZE` | **B** History cleanup: Batch size. Validate against load, recovery or cleanup policy before tuning. | Platform; restart installation |
| C096<br>`app.retention.max-batches-per-run`<br>integer | `${IJOB_RETENTION_MAX_BATCHES_PER_RUN:10}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RETENTION_MAX_BATCHES_PER_RUN` | **B** History cleanup: Maximum batches per run. Validate against load, recovery or cleanup policy before tuning. | Platform; restart installation |
| C097<br>`app.retention.execution`<br>duration | `${IJOB_RETENTION_EXECUTION:90d}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RETENTION_EXECUTION` | **A** Execution history retention duration; applies with cleanup enabled and dry-run settings. | Platform; restart installation |
| C098<br>`app.retention.notification`<br>duration | `${IJOB_RETENTION_NOTIFICATION:90d}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RETENTION_NOTIFICATION` | **A** Notification history retention duration; applies with cleanup enabled and dry-run settings. | Platform; restart installation |
| C099<br>`app.retention.event`<br>duration | `${IJOB_RETENTION_EVENT:180d}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RETENTION_EVENT` | **A** Event history retention duration; applies with cleanup enabled and dry-run settings. | Platform; restart installation |
| C100<br>`app.retention.app-run`<br>duration | `${IJOB_RETENTION_APP_RUN:30d}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RETENTION_APP_RUN` | **A** Node-run history retention duration; applies with cleanup enabled and dry-run settings. | Platform; restart installation |
| C101<br>`app.retention.audit`<br>duration | `${IJOB_RETENTION_AUDIT:90d}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RETENTION_AUDIT` | **A** Audit history retention duration; applies with cleanup enabled and dry-run settings. | Platform; restart installation |

<a id="pools"></a>

## Execution capacity and scanner threads

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C023<br>`app.scheduling.pool-size`<br>integer | `${IJOB_SCHEDULING_POOL_SIZE:8}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCHEDULING_POOL_SIZE` | **B** Internal scheduled-scanner threads; not the maximum business-task concurrency. | Platform; restart installation |
| C024<br>`app.scheduling.shutdown-timeout`<br>duration | `${IJOB_SCHEDULING_SHUTDOWN_TIMEOUT:30s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCHEDULING_SHUTDOWN_TIMEOUT` | **B** Shutdown wait for scheduled-scanner executor. | Platform; restart installation |
| C104<br>`app.execution.shutdown-timeout`<br>duration | `${IJOB_EXECUTION_SHUTDOWN_TIMEOUT:30s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_EXECUTION_SHUTDOWN_TIMEOUT` | **B** Executor shutdown wait; coordinate with container grace and Spring shutdown limits. | Platform; restart installation |
| C105<br>`app.execution.trigger.core-size`<br>integer | `${IJOB_TRIGGER_POOL_CORE_SIZE:4}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_TRIGGER_POOL_CORE_SIZE` | **B** trigger executor Core threads; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C106<br>`app.execution.trigger.max-size`<br>integer | `${IJOB_TRIGGER_POOL_MAX_SIZE:20}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_TRIGGER_POOL_MAX_SIZE` | **B** trigger executor Maximum threads; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C107<br>`app.execution.trigger.queue-capacity`<br>integer | `${IJOB_TRIGGER_POOL_QUEUE_CAPACITY:0}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_TRIGGER_POOL_QUEUE_CAPACITY` | **B** trigger executor Queue capacity; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C108<br>`app.execution.trigger.keep-alive`<br>duration | `${IJOB_TRIGGER_POOL_KEEP_ALIVE:60s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_TRIGGER_POOL_KEEP_ALIVE` | **B** trigger executor Non-core idle-thread lifetime; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C109<br>`app.execution.job.core-size`<br>integer | `${IJOB_JOB_POOL_CORE_SIZE:4}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_JOB_POOL_CORE_SIZE` | **B** job executor Core threads; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C110<br>`app.execution.job.max-size`<br>integer | `${IJOB_JOB_POOL_MAX_SIZE:20}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_JOB_POOL_MAX_SIZE` | **B** job executor Maximum threads; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C111<br>`app.execution.job.queue-capacity`<br>integer | `${IJOB_JOB_POOL_QUEUE_CAPACITY:0}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_JOB_POOL_QUEUE_CAPACITY` | **B** job executor Queue capacity; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C112<br>`app.execution.job.keep-alive`<br>duration | `${IJOB_JOB_POOL_KEEP_ALIVE:60s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_JOB_POOL_KEEP_ALIVE` | **B** job executor Non-core idle-thread lifetime; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C113<br>`app.execution.dag.core-size`<br>integer | `${IJOB_DAG_POOL_CORE_SIZE:8}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_DAG_POOL_CORE_SIZE` | **B** dag executor Core threads; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C114<br>`app.execution.dag.max-size`<br>integer | `${IJOB_DAG_POOL_MAX_SIZE:32}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_DAG_POOL_MAX_SIZE` | **B** dag executor Maximum threads; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C115<br>`app.execution.dag.queue-capacity`<br>integer | `${IJOB_DAG_POOL_QUEUE_CAPACITY:500}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_DAG_POOL_QUEUE_CAPACITY` | **B** dag executor Queue capacity; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C116<br>`app.execution.dag.keep-alive`<br>duration | `${IJOB_DAG_POOL_KEEP_ALIVE:60s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_DAG_POOL_KEEP_ALIVE` | **B** dag executor Non-core idle-thread lifetime; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C117<br>`app.execution.task.core-size`<br>integer | `${IJOB_TASK_POOL_CORE_SIZE:8}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_TASK_POOL_CORE_SIZE` | **B** task executor Core threads; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C118<br>`app.execution.task.max-size`<br>integer | `${IJOB_TASK_POOL_MAX_SIZE:32}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_TASK_POOL_MAX_SIZE` | **B** task executor Maximum threads; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C119<br>`app.execution.task.queue-capacity`<br>integer | `${IJOB_TASK_POOL_QUEUE_CAPACITY:1000}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_TASK_POOL_QUEUE_CAPACITY` | **B** task executor Queue capacity; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C120<br>`app.execution.task.keep-alive`<br>duration | `${IJOB_TASK_POOL_KEEP_ALIVE:60s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_TASK_POOL_KEEP_ALIVE` | **B** task executor Non-core idle-thread lifetime; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C121<br>`app.execution.script.core-size`<br>integer | `${IJOB_SCRIPT_POOL_CORE_SIZE:4}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCRIPT_POOL_CORE_SIZE` | **B** script executor Core threads; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C122<br>`app.execution.script.max-size`<br>integer | `${IJOB_SCRIPT_POOL_MAX_SIZE:16}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCRIPT_POOL_MAX_SIZE` | **B** script executor Maximum threads; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C123<br>`app.execution.script.queue-capacity`<br>integer | `${IJOB_SCRIPT_POOL_QUEUE_CAPACITY:0}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCRIPT_POOL_QUEUE_CAPACITY` | **B** script executor Queue capacity; size jointly, distinct from business concurrency groups. | Platform; restart installation |
| C124<br>`app.execution.script.keep-alive`<br>duration | `${IJOB_SCRIPT_POOL_KEEP_ALIVE:60s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_SCRIPT_POOL_KEEP_ALIVE` | **B** script executor Non-core idle-thread lifetime; size jointly, distinct from business concurrency groups. | Platform; restart installation |

<a id="coordination"></a>

## Internal coordination, recovery and consistency

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C034<br>`app.cache-consistency.max-staleness`<br>duration | `${IJOB_CACHE_MAX_STALENESS:5s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_CACHE_MAX_STALENESS` | **D** Maximum cache staleness across nodes; part of consistency behavior. | Platform; restart installation |
| C044<br>`app.runtime.work-queue.scan-interval`<br>duration | `${IJOB_WORK_QUEUE_SCAN_INTERVAL:1s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_WORK_QUEUE_SCAN_INTERVAL` | **D** Persistent work queue: Scan interval. Coordination-sensitive; retain default. | Platform; restart installation |
| C045<br>`app.runtime.work-queue.max-idle-scan-interval`<br>duration | `${IJOB_WORK_QUEUE_MAX_IDLE_SCAN_INTERVAL:5s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_WORK_QUEUE_MAX_IDLE_SCAN_INTERVAL` | **D** Persistent work queue: Maximum idle scan interval. Coordination-sensitive; retain default. | Platform; restart installation |
| C046<br>`app.runtime.work-queue.scan-jitter`<br>duration | `${IJOB_WORK_QUEUE_SCAN_JITTER:200ms}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_WORK_QUEUE_SCAN_JITTER` | **D** Persistent work queue: Scan jitter. Coordination-sensitive; retain default. | Platform; restart installation |
| C047<br>`app.runtime.work-queue.renew-interval`<br>duration | `${IJOB_WORK_QUEUE_RENEW_INTERVAL:5s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_WORK_QUEUE_RENEW_INTERVAL` | **D** Persistent work queue: Lease renewal interval. Coordination-sensitive; retain default. | Platform; restart installation |
| C048<br>`app.runtime.work-queue.scan-batch-size`<br>integer | `${IJOB_WORK_QUEUE_SCAN_BATCH_SIZE:100}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_WORK_QUEUE_SCAN_BATCH_SIZE` | **D** Persistent work queue: Scan batch size. Coordination-sensitive; retain default. | Platform; restart installation |
| C049<br>`app.runtime.work-queue.max-inflight-roots`<br>integer | `${IJOB_WORK_QUEUE_MAX_INFLIGHT_ROOTS:20}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_WORK_QUEUE_MAX_INFLIGHT_ROOTS` | **B** In-flight root-run limit per node; size with executors, concurrency controls and capacity. | Platform; restart installation |
| C050<br>`app.runtime.work-queue.lease-duration`<br>duration | `${IJOB_WORK_QUEUE_LEASE_DURATION:30s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_WORK_QUEUE_LEASE_DURATION` | **D** Persistent work queue: Lease duration. Coordination-sensitive; retain default. | Platform; restart installation |
| C051<br>`app.runtime.work-queue.retry-delay`<br>duration | `${IJOB_WORK_QUEUE_RETRY_DELAY:5s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_WORK_QUEUE_RETRY_DELAY` | **D** Persistent work queue: Retry delay. Coordination-sensitive; retain default. | Platform; restart installation |
| C052<br>`app.runtime.work-queue.max-attempts`<br>integer | `${IJOB_WORK_QUEUE_MAX_ATTEMPTS:5}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_WORK_QUEUE_MAX_ATTEMPTS` | **D** Persistent work queue: Internal processing attempt limit. Coordination-sensitive; retain default. | Platform; restart installation |
| C053<br>`app.runtime.command.scan-interval`<br>duration | `${IJOB_RUNTIME_COMMAND_SCAN_INTERVAL:250ms}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RUNTIME_COMMAND_SCAN_INTERVAL` | **D** Runtime command coordination: Scan interval. Coordination-sensitive; retain default. | Platform; restart installation |
| C054<br>`app.runtime.command.scan-batch-size`<br>integer | `${IJOB_RUNTIME_COMMAND_SCAN_BATCH_SIZE:100}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RUNTIME_COMMAND_SCAN_BATCH_SIZE` | **D** Runtime command coordination: Scan batch size. Coordination-sensitive; retain default. | Platform; restart installation |
| C055<br>`app.runtime.command.retry-delay`<br>duration | `${IJOB_RUNTIME_COMMAND_RETRY_DELAY:250ms}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RUNTIME_COMMAND_RETRY_DELAY` | **D** Runtime command coordination: Retry delay. Coordination-sensitive; retain default. | Platform; restart installation |
| C056<br>`app.runtime.command.timeout`<br>duration | `${IJOB_RUNTIME_COMMAND_TIMEOUT:5m}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_RUNTIME_COMMAND_TIMEOUT` | **D** Runtime command coordination: Internal command timeout. Coordination-sensitive; retain default. | Platform; restart installation |
| C057<br>`app.runtime.concurrency-permit.wait-timeout`<br>duration | `${IJOB_CONCURRENCY_PERMIT_WAIT_TIMEOUT:5m}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_CONCURRENCY_PERMIT_WAIT_TIMEOUT` | **D** Concurrency permit coordination: Concurrency-permit wait timeout. Coordination-sensitive; retain default. | Platform; restart installation |
| C058<br>`app.runtime.concurrency-permit.retry-interval`<br>duration | `${IJOB_CONCURRENCY_PERMIT_RETRY_INTERVAL:100ms}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_CONCURRENCY_PERMIT_RETRY_INTERVAL` | **D** Concurrency permit coordination: Retry interval. Coordination-sensitive; retain default. | Platform; restart installation |
| C059<br>`app.runtime.concurrency-permit.max-retry-interval`<br>duration | `${IJOB_CONCURRENCY_PERMIT_MAX_RETRY_INTERVAL:2s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_CONCURRENCY_PERMIT_MAX_RETRY_INTERVAL` | **D** Concurrency permit coordination: Maximum retry interval. Coordination-sensitive; retain default. | Platform; restart installation |
| C060<br>`app.runtime.concurrency-permit.retry-jitter`<br>duration | `${IJOB_CONCURRENCY_PERMIT_RETRY_JITTER:100ms}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_CONCURRENCY_PERMIT_RETRY_JITTER` | **D** Concurrency permit coordination: Retry jitter. Coordination-sensitive; retain default. | Platform; restart installation |
| C061<br>`app.runtime.concurrency-permit.renew-interval`<br>duration | `${IJOB_CONCURRENCY_PERMIT_RENEW_INTERVAL:5s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_CONCURRENCY_PERMIT_RENEW_INTERVAL` | **D** Concurrency permit coordination: Lease renewal interval. Coordination-sensitive; retain default. | Platform; restart installation |
| C062<br>`app.runtime.concurrency-permit.lease-duration`<br>duration | `${IJOB_CONCURRENCY_PERMIT_LEASE_DURATION:30s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_CONCURRENCY_PERMIT_LEASE_DURATION` | **D** Concurrency permit coordination: Lease duration. Coordination-sensitive; retain default. | Platform; restart installation |
| C063<br>`app.runtime.maintenance.enabled`<br>boolean | `${IJOB_MAINTENANCE_ENABLED:true}` (S01) | true (explicitly written by setup) | `IJOB_MAINTENANCE_ENABLED` | **D** Runtime maintenance: Runtime maintenance/recovery switch. Coordination-sensitive; retain default. | Platform; restart installation |
| C064<br>`app.runtime.maintenance.scan-interval`<br>duration | `${IJOB_MAINTENANCE_SCAN_INTERVAL:5s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_MAINTENANCE_SCAN_INTERVAL` | **D** Runtime maintenance: Scan interval. Coordination-sensitive; retain default. | Platform; restart installation |
| C065<br>`app.runtime.maintenance.renew-interval`<br>duration | `${IJOB_MAINTENANCE_RENEW_INTERVAL:10s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_MAINTENANCE_RENEW_INTERVAL` | **D** Runtime maintenance: Lease renewal interval. Coordination-sensitive; retain default. | Platform; restart installation |
| C066<br>`app.runtime.maintenance.lease-duration`<br>duration | `${IJOB_MAINTENANCE_LEASE_DURATION:30s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_MAINTENANCE_LEASE_DURATION` | **D** Runtime maintenance: Lease duration. Coordination-sensitive; retain default. | Platform; restart installation |
| C067<br>`app.runtime.maintenance.stale-run-timeout`<br>duration | `${IJOB_MAINTENANCE_STALE_RUN_TIMEOUT:30s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_MAINTENANCE_STALE_RUN_TIMEOUT` | **D** Runtime maintenance: Stale-run timeout. Coordination-sensitive; retain default. | Platform; restart installation |
| C068<br>`app.runtime.maintenance.orphan-grace`<br>duration | `${IJOB_MAINTENANCE_ORPHAN_GRACE:1m}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_MAINTENANCE_ORPHAN_GRACE` | **D** Runtime maintenance: Orphan recovery grace. Coordination-sensitive; retain default. | Platform; restart installation |
| C069<br>`app.runtime.maintenance.batch-size`<br>integer | `${IJOB_MAINTENANCE_BATCH_SIZE:100}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_MAINTENANCE_BATCH_SIZE` | **D** Runtime maintenance: Batch size. Coordination-sensitive; retain default. | Platform; restart installation |
| C070<br>`app.runtime.maintenance.max-batches-per-run`<br>integer | `${IJOB_MAINTENANCE_MAX_BATCHES_PER_RUN:10}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_MAINTENANCE_MAX_BATCHES_PER_RUN` | **D** Runtime maintenance: Maximum batches per run. Coordination-sensitive; retain default. | Platform; restart installation |

<a id="access"></a>

## Access, resource browsing, HTTP and locale

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C008<br>`spring.web.locale`<br>locale | `${IJOB_DEFAULT_LOCALE:en}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_DEFAULT_LOCALE` | **A** Server fallback locale; Accept-Language and frontend preference also apply. | Platform; restart installation |
| C009<br>`spring.web.locale-resolver`<br>text | `accept-header` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Existing request-header locale resolution rule. | Platform; restart installation |
| C018<br>`server.port`<br>port | `${IJOB_SERVER_PORT:8080}` (S01) | Native 18080; Docker 8080 | `IJOB_SERVER_PORT` | **A** Native service port; retain backend container port 8080 and change host mapping. | Platform; restart installation |
| C019<br>`server.shutdown`<br>text | `graceful` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Graceful shutdown; do not bypass draining to exit faster. | Platform; restart installation |
| C022<br>`app.resource-browser.local-roots`<br>path-list | `[]` (S01) | No extra setup override; use loaded configuration/implementation default | — | **A** Absolute roots allowed for LOCAL draft browsing; not a filesystem sandbox for all scripts. | Platform; restart installation |
| C102<br>`app.http.connect-timeout`<br>duration | `${IJOB_HTTP_CONNECT_TIMEOUT:5s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_HTTP_CONNECT_TIMEOUT` | **A** Shared HTTP client connect timeout; does not override every plugin-specific timeout. | Platform; restart installation |
| C103<br>`app.http.read-timeout`<br>duration | `${IJOB_HTTP_READ_TIMEOUT:30s}` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_HTTP_READ_TIMEOUT` | **A** Shared HTTP client read timeout; distinct from overall task timeout. | Platform; restart installation |
| C182<br>`server.address`<br>text | `generated: native=127.0.0.1; docker=0.0.0.0` (S10) | Native 127.0.0.1; Docker 0.0.0.0 | — | **A** Native bind address; keep Docker binding consistent with probes and proxy. | Platform; restart installation |

<a id="logging"></a>

## Logging and management probes

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C152<br>`logging.pattern.level`<br>text | `%5p [traceId=%X{traceId:-} trigger=%X{triggerId:-} root=%X{rootInstanceId:-} instance=%X{instanceId:-} command=%X{commandId:-} outbox=%X{outboxId:-} attempt=%X{attempt:-} taskAttempt=%X{taskAttempt:-} fence=%X{fence:-}]` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Correlation-ID log pattern; diagnostics depend on these fields. | Platform; restart installation |
| C153<br>`logging.level.root`<br>enum | `INFO` (S01) | No extra setup override; use loaded configuration/implementation default | — | **B** Root log level; restore after diagnosis and assess volume. | Platform; restart installation |
| C154<br>`logging.level.com.ifielsc.ijob`<br>enum | `INFO` (S01) | No extra setup override; use loaded configuration/implementation default | — | **B** iJob log level; do not dump secret configuration for diagnosis. | Platform; restart installation |
| C155<br>`management.metrics.tags.application`<br>text | `${spring.application.name}` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Application metric tag defaults to application name. | Platform; restart installation |
| C156<br>`management.endpoints.web.exposure.include`<br>text-list | `health,info,metrics` (S01) | No extra setup override; use loaded configuration/implementation default | — | **C** Management endpoint exposure; review access controls before adding endpoints. | Platform; restart installation |
| C157<br>`management.endpoint.health.probes.enabled`<br>boolean | `true` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Enable readiness/liveness probe support. | Platform; restart installation |
| C158<br>`management.endpoint.health.group.readiness.include`<br>text | `readinessState,runtime,singletonLock,coordination,db` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Readiness aggregation includes runtime, singleton lock, coordination and database. | Platform; restart installation |
| C159<br>`management.endpoint.health.show-details`<br>enum | `never` (S01) | No extra setup override; use loaded configuration/implementation default | — | **C** Health detail visibility; design access controls before exposing details. | Platform; restart installation |
| C160<br>`management.health.livenessstate.enabled`<br>boolean | `true` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Liveness state probe; retain deployment probe contract. | Platform; restart installation |
| C161<br>`management.health.readinessstate.enabled`<br>boolean | `true` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Readiness state probe; retain deployment probe contract. | Platform; restart installation |
| C190<br>`logging.config`<br>uri | `launcher: <bundle>/support/logging-native.xml` (S11) | No extra setup override; use loaded configuration/implementation default | — | **D** Native logging configuration resource, set by launcher. | Native; restart installation |
| C194<br>`ijob.logging.max-file-size`<br>size | `10MB` (S12) | No extra setup override; use loaded configuration/implementation default | — | **A** Native log rollover size per file. | Native; restart installation |
| C195<br>`ijob.logging.max-history`<br>days | `14` (S12) | No extra setup override; use loaded configuration/implementation default | — | **A** Native log archive retention in days. | Native; restart installation |
| C196<br>`ijob.logging.total-size-cap`<br>size | `200MB` (S12) | No extra setup override; use loaded configuration/implementation default | — | **A** Native archive size cap; may remove archives before age limit. | Native; restart installation |

<a id="framework"></a>

## Launcher integration and framework contracts

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C001<br>`spring.application.name`<br>text | `ijob` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Application identity used for logs/metrics, not a business display name. | Platform; restart installation |
| C002<br>`spring.messages.fallback-to-system-locale`<br>boolean | `false` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Disable implicit host-locale fallback for consistent localization. | Platform; restart installation |
| C003<br>`spring.messages.encoding`<br>text | `UTF-8` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Message resource UTF-8 encoding; retain default. | Platform; restart installation |
| C004<br>`spring.profiles.default`<br>text | `local` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Fallback when no active profile is specified; deployment launchers select one explicitly. | Platform; restart installation |
| C005<br>`spring.config.import`<br>text | `optional:classpath:database/${IJOB_PLATFORM_DB:mysql}.yml` (S01) | No extra setup override; use loaded configuration/implementation default | `IJOB_PLATFORM_DB` | **D** Select database-specific configuration; determined by database and launcher. | Platform; restart installation |
| C006<br>`spring.jackson.default-property-inclusion`<br>text | `non_null` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** API null-value serialization contract. | Platform; restart installation |
| C007<br>`spring.jackson.serialization.write-dates-as-timestamps`<br>boolean | `false` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** API date serialization contract. | Platform; restart installation |
| C010<br>`spring.lifecycle.timeout-per-shutdown-phase`<br>duration | `30s` (S01) | No extra setup override; use loaded configuration/implementation default | — | **B** Wait per Spring shutdown phase; verify with executor and container limits. | Platform; restart installation |
| C011<br>`spring.sql.init.mode`<br>text | `never` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Prevent a second SQL initialization path alongside formal migrations. | Platform; restart installation |
| C020<br>`mybatis.mapper-locations`<br>text | `classpath*:mapper/**/*.xml` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Database mapper resource locations. | Platform; restart installation |
| C021<br>`mybatis.configuration.map-underscore-to-camel-case`<br>boolean | `true` (S01) | No extra setup override; use loaded configuration/implementation default | — | **D** Column-to-model naming contract. | Platform; restart installation |
| C162<br>`spring.config.activate.on-profile`<br>text | `mail` (S02) | No extra setup override; use loaded configuration/implementation default | — | **D** Mail-profile activation condition; configuration structure. | Platform; restart installation |
| C169<br>`mybatis.configuration.jdbc-type-for-null`<br>text | `NULL` (S04) | No extra setup override; use loaded configuration/implementation default | — | **D** Oracle null binding rule. | Platform; restart installation |
| C186<br>`spring.profiles.active`<br>text-list | `release` (S10) | Setup release; native launch adds native-background | — | **D** Launcher-selected release/native-background or container profile; not a normal feature switch. | Platform; restart installation |
| C187<br>`app.web.enabled`<br>boolean | `native=true; docker uses separate web service` (S10) | Native true; not generated for Docker | — | **D** Native static UI hosting switch; determined by deployment mode, with separate Docker web service. | Native; restart installation |
| C188<br>`app.web.directory`<br>path | `native=<bundle>/web-dist` (S10) | Native &lt;bundle&gt;/web-dist; Docker uses separate web service | — | **D** Static UI directory; native launcher supplies current bundle path, not a frontend rebuild. | Native; restart installation |
| C189<br>`spring.config.additional-location`<br>uri | `launcher: external private configuration URI` (S11) | No extra setup override; use loaded configuration/implementation default | — | **D** Launcher-selected private external configuration location; avoid competing entry points. | Platform; restart installation |
| C191<br>`ijob.native.home`<br>path | `launcher: IJOB_HOME process identity` (S11) | No extra setup override; use loaded configuration/implementation default | — | **D** Process ownership marker; launcher-managed, never spoof or edit independently. | Native; restart installation |
| C192<br>`ijob.native.log-directory`<br>path | `launcher: <IJOB_HOME>/logs` (S12) | No extra setup override; use loaded configuration/implementation default | — | **D** Log directory derived from IJOB_HOME; manage with the installation. | Native; restart installation |
| C193<br>`ijob.native.log-session`<br>text | `launcher-generated unique launch identity` (S12) | No extra setup override; use loaded configuration/implementation default | — | **D** Per-launch readiness log marker generated by launcher. | Native; restart installation |

<a id="agent"></a>

## Standalone monitoring Agent

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C176<br>`spring.application.name`<br>text | `ijob-monitor-agent` (S08) | No extra setup override; use loaded configuration/implementation default | — | **D** Agent application identity. | Standalone Agent; restart Agent |
| C177<br>`agent.token`<br>secret | `${IJOB_AGENT_TOKEN:}` (S08) | No extra setup override; use loaded configuration/implementation default | `IJOB_AGENT_TOKEN` | **C** Agent authentication token; align both ends, image imports a private token file. | Standalone Agent; restart Agent |
| C178<br>`agent.collect-timeout`<br>milliseconds | `${IJOB_AGENT_COLLECT_TIMEOUT:10000}` (S08) | No extra setup override; use loaded configuration/implementation default | `IJOB_AGENT_COLLECT_TIMEOUT` | **B** Collection timeout in milliseconds; assess monitored-host load. | Standalone Agent; restart Agent |
| C179<br>`agent.collect-concurrency`<br>integer | `${IJOB_AGENT_COLLECT_CONCURRENCY:2}` (S08) | No extra setup override; use loaded configuration/implementation default | `IJOB_AGENT_COLLECT_CONCURRENCY` | **B** Concurrent collections; assess target-host resources. | Standalone Agent; restart Agent |
| C180<br>`agent.collect-queue-capacity`<br>integer | `${IJOB_AGENT_COLLECT_QUEUE_CAPACITY:4}` (S08) | No extra setup override; use loaded configuration/implementation default | `IJOB_AGENT_COLLECT_QUEUE_CAPACITY` | **B** Collection queue capacity; excessive queues can delay responses. | Standalone Agent; restart Agent |
| C181<br>`agent.hostname`<br>text | `unset: detected automatically` (S09) | No extra setup override; use loaded configuration/implementation default | — | **A** Reported hostname; detected when omitted, may be set to identify the monitored host. | Standalone Agent; restart Agent |
| C197<br>`server.port`<br>port | `8081 in image; not explicitly set in Agent YAML` (S13) | No extra setup override; use loaded configuration/implementation default | — | **B** Agent image internal port 8081; update probes and platform resource connection together. | Standalone Agent; restart Agent |
| C198<br>`spring.config.import`<br>uri | `configtree:/run/secrets/ in image` (S13) | No extra setup override; use loaded configuration/implementation default | — | **D** Image import of private agent.token; align with mounts. | Standalone Agent; restart Agent |

<a id="deployment"></a>

## Startup, image-lock and development variables

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C199<br>`IJOB_HOME`<br>path | `Legacy launcher defaults: Windows uses sibling ijob-data; Linux uses sibling ijob-data-linux; Docker uses sibling ijob-data-docker` (S14) | See default and scope | — | **A** Installation data root; use consistently for all commands. Changing it does not migrate data. | Launch environment; next relevant command |
| C200<br>`IJOB_LANG`<br>enum | `en; zh selects Chinese` (S15) | See default and scope | — | **A** Setup/management language, distinct from UI preference and notification locale. | Launch environment; next relevant command |
| C201<br>`JAVA_HOME`<br>path | `unset: resolve Java from PATH` (S11) | See default and scope | — | **A** Java 21 installation directory for native launch. | Native; restart installation |
| C202<br>`IJOB_START_TIMEOUT`<br>seconds | `120` (S16) | See default and scope | — | **B** Readiness wait in seconds, range 1–600; Linux scripts only, Windows waits are currently fixed. | Linux native scripts only |
| C203<br>`IJOB_STOP_TIMEOUT`<br>seconds | `120` (S17) | See default and scope | — | **B** Graceful-stop wait in seconds, range 1–600; Linux scripts only, Windows waits are currently fixed. | Linux native scripts only |
| C204<br>`IJOB_HTTP_PORT`<br>port | `18080` (S10) | See default and scope | — | **A** Docker host published port; recreate using the same bundle after changing. | Docker; recreate affected containers |
| C205<br>`IJOB_HTTP_BIND`<br>text | `127.0.0.1` (S18) | See default and scope | — | **B** Only advanced repository Compose supports this variable; setup-generated Compose fixes loopback binding, so setting this alone has no effect. | Advanced repository Compose; recreate containers |
| C206<br>`IJOB_CONFIG_PATH`<br>path | `required; no default` (S18) | See default and scope | — | **C** Private YAML path for advanced Compose; first-run bundles use IJOB_HOME/application.properties. | Advanced repository Compose; recreate containers |
| C207<br>`IJOB_AGENT_TOKEN_PATH`<br>path | `required; no default` (S19) | See default and scope | — | **C** Path to private Agent token file, not the token value. | Agent Compose; recreate Agent |
| C208<br>`IJOB_IMAGE_TAG`<br>text | `1.0.0` (S18) | See default and scope | — | **D** Advanced Compose tag only; distributions launch pinned image IDs. A tag is not provenance verification. | Advanced repository Compose; recreate containers |
| C209<br>`IJOB_PLATFORM_DB`<br>enum | `setup selects vendor; repository YAML fallback mysql` (S10) | See default and scope | — | **C** Native launcher derives vendor from installation metadata; align backend Docker environment. | Launch environment; next relevant command |
| C210<br>`IJOB_PLATFORM_DB_SCHEMA`<br>text | `PostgreSQL public; SQL Server dbo; Oracle IJOB_DB_USERNAME or ijob` (S04) | See default and scope | — | **C** Schema placeholder in database configurations; align JDBC, platform and migration schemas. | Launch environment; next relevant command |
| C211<br>`IJOB_DB_USERNAME`<br>text | `ijob for Oracle schema fallback only` (S04) | See default and scope | — | **C** Only an Oracle schema fallback in the template; not a replacement for spring.datasource.username. | Launch environment; next relevant command |
| C212<br>`IJOB_MAVEN_IMAGE`<br>image | `pinned in develop/deployment/docker/images.env` (S20) | See default and scope | — | **D** Build base-image pin; update through dependency review, not runtime tuning. | Build time; rebuild image |
| C213<br>`IJOB_JRE_IMAGE`<br>image | `pinned in develop/deployment/docker/images.env` (S20) | See default and scope | — | **D** Build base-image pin; update through dependency review, not runtime tuning. | Build time; rebuild image |
| C214<br>`IJOB_NODE_IMAGE`<br>image | `pinned in develop/deployment/docker/images.env` (S20) | See default and scope | — | **D** Build base-image pin; update through dependency review, not runtime tuning. | Build time; rebuild image |
| C215<br>`IJOB_NGINX_IMAGE`<br>image | `pinned in develop/deployment/docker/images.env` (S20) | See default and scope | — | **D** Build base-image pin; update through dependency review, not runtime tuning. | Build time; rebuild image |
| C216<br>`JAVA_TOOL_OPTIONS`<br>text | `images: -XX:MaxRAMPercentage=70 -Duser.timezone=UTC` (S21) | See default and scope | — | **B** JVM options; retain UTC contract in containers and size with memory limits. build.py rejects inherited values. | JVM; restart process or recreate container |
| C217<br>`IJOB_DEV_API_TARGET`<br>uri | `unset: no proxy` (S22) | See default and scope | — | **B** Frontend development proxy target; not deployed runtime configuration. Avoid connecting demo environments. | Development; restart development server |
| C218<br>`SPRING_PROFILES_ACTIVE`<br>text | `backend image container; first-run override release` (S21) | See default and scope | — | **D** Image/launcher profile selection; not a routine environment toggle. | Docker; recreate affected containers |
| C219<br>`SPRING_CONFIG_ADDITIONAL_LOCATION`<br>uri | `image /run/secrets/ijob.yaml; first-run /run/secrets/application.properties` (S10) | See default and scope | — | **D** Docker external configuration location; must match mounts. | Docker; recreate affected containers |
| C220<br>`SPRING_CONFIG_IMPORT`<br>uri | `agent image configtree:/run/secrets/` (S13) | See default and scope | — | **D** Agent image reads private token through a configuration tree. | Agent image; recreate Agent container |
| C221<br>`SERVER_PORT`<br>port | `Agent image 8081` (S13) | See default and scope | — | **D** Agent image internal port; synchronize probes and connection settings. | Agent image; recreate Agent container |
| C222<br>`IJOB_BACKEND_IMAGE`<br>image-id | `generated sha256 image ID in docker-images.lock` (S23) | See default and scope | — | **D** Generated distribution image-lock record, not an ordinary environment override for launch. | Generated bundle record; rebuild distribution |
| C223<br>`IJOB_WEB_IMAGE`<br>image-id | `generated sha256 image ID in docker-images.lock` (S23) | See default and scope | — | **D** Generated distribution image-lock record, not an ordinary environment override for launch. | Generated bundle record; rebuild distribution |
| C224<br>`IJOB_AGENT_IMAGE`<br>image-id | `generated sha256 image ID in docker-images.lock` (S23) | See default and scope | — | **D** Generated distribution image-lock record, not an ordinary environment override for launch. | Generated bundle record; rebuild distribution |

<a id="compose"></a>

## Supplementary Compose controls

| ID / key / type | Source declaration | Fresh installation / override | Environment aliases | Recommendation and purpose | Scope / activation |
| --- | --- | --- | --- | --- | --- |
| C225<br>`name`<br>compose-field | `setup-generated project; advanced ijob-v1-candidate` (S10) | See default and scope | — | **C** Project identity; use an independent name for restore or multiple installations. | Corresponding Compose service; recreate container |
| C226<br>`services.<service>.image`<br>compose-field | `distribution exact image ID override` (S10) | See default and scope | — | **D** Bundle manages exact image pins; changing a fixed 1.0.0 tag is not a verified upgrade. | Corresponding Compose service; recreate container |
| C227<br>`services.web.ports`<br>compose-field | `127.0.0.1:<IJOB_HTTP_PORT>:8080` (S10) | See default and scope | — | **B** Host bind address and port; external access needs ingress and access control. | Corresponding Compose service; recreate container |
| C228<br>`services.<service>.volumes`<br>compose-field | `setup config/runtime binds; advanced named data volumes` (S10) | See default and scope | — | **C** Private config/data/plugin mounts; check source paths and permissions on restore. | Corresponding Compose service; recreate container |
| C229<br>`services.<service>.environment`<br>compose-field | `template-specific` (S10) | See default and scope | — | **C** Container environment; not automatic host-shell inheritance. Align with configuration files. | Corresponding Compose service; recreate container |
| C230<br>`services.<service>.mem_limit`<br>compose-field | `backend 1g; web 128m; agent 256m` (S18) | See default and scope | — | **B** Memory limit; size with JVM fraction and workload. | Corresponding Compose service; recreate container |
| C231<br>`services.<service>.cpus`<br>compose-field | `backend 2.0; advanced web/agent 1.0; setup web unset` (S18) | See default and scope | — | **B** CPU quota; setup web leaves it unset. Distinguish setup and advanced templates. | Corresponding Compose service; recreate container |
| C232<br>`services.<service>.restart`<br>compose-field | `unless-stopped` (S18) | See default and scope | — | **B** Process restart policy; not task recovery or idempotency assurance. | Corresponding Compose service; recreate container |
| C233<br>`services.<service>.stop_grace_period`<br>compose-field | `backend 120s; advanced web/agent 30s; setup web unset` (S18) | See default and scope | — | **B** Stop grace; setup web leaves it unset. Allow backend draining and shutdown. | Corresponding Compose service; recreate container |
| C234<br>`services.<service>.logging.driver`<br>compose-field | `json-file` (S18) | See default and scope | — | **B** Container log driver; verify collection and access when changing. | Corresponding Compose service; recreate container |
| C235<br>`services.<service>.logging.options.max-size`<br>compose-field | `10m` (S18) | See default and scope | — | **A** Container log rollover size. | Corresponding Compose service; recreate container |
| C236<br>`services.<service>.logging.options.max-file`<br>compose-field | `backend/web 5; agent 3` (S18) | See default and scope | — | **A** Retained container log files. | Corresponding Compose service; recreate container |
| C237<br>`services.<service>.tmpfs`<br>compose-field | `backend 256m; web 128m; agent 64m; template permissions` (S18) | See default and scope | — | **B** Temporary storage size, permissions and exec flag; review with plugin needs. | Corresponding Compose service; recreate container |
| C238<br>`services.<service>.read_only`<br>compose-field | `true` (S18) | See default and scope | — | **D** Read-only container root filesystem; use explicit data mounts. | Corresponding Compose service; recreate container |
| C239<br>`services.<service>.cap_drop`<br>compose-field | `ALL` (S18) | See default and scope | — | **D** Retain minimal container capabilities; do not broadly relax for task features. | Corresponding Compose service; recreate container |
| C240<br>`services.<service>.security_opt`<br>compose-field | `no-new-privileges:true` (S18) | See default and scope | — | **D** Retain no-new-privileges restriction. | Corresponding Compose service; recreate container |
| C241<br>`services.<service>.init`<br>compose-field | `backend/agent true; web not set` (S18) | See default and scope | — | **D** Container signal forwarding/reaping; retain verified entrypoint behavior. | Corresponding Compose service; recreate container |
| C242<br>`services.web.depends_on`<br>compose-field | `backend health; cluster also backend-b health` (S18) | See default and scope | — | **D** Startup health dependencies; not continuous traffic health management. | Corresponding Compose service; recreate container |
| C243<br>`secrets.<secret>.file`<br>compose-field | `advanced private config/token paths` (S18) | See default and scope | — | **C** Advanced Compose secret-file source; restrict permissions and keep outside Git. | Corresponding Compose service; recreate container |
| C244<br>`volumes.<volume>`<br>compose-field | `advanced backend-data / backend-b-data` (S18) | See default and scope | — | **C** Advanced template persistent-volume identity; do not reuse old environments or delete to reset. | Corresponding Compose service; recreate container |
| C245<br>`services.<service>.healthcheck.interval`<br>duration | `backend/web 15s; Agent 30s` (S21) | See default and scope | — | **B** Health-check interval; image defaults come from Dockerfiles. Revalidate readiness after overrides. | Docker; recreate affected containers |
| C246<br>`services.<service>.healthcheck.timeout`<br>duration | `backend/Agent 6s; web 5s` (S21) | See default and scope | — | **B** Per-probe timeout; image defaults come from Dockerfiles. Revalidate readiness after overrides. | Docker; recreate affected containers |
| C247<br>`services.<service>.healthcheck.start_period`<br>duration | `backend 90s; Agent 30s; web not explicitly set` (S21) | See default and scope | — | **B** Startup probe grace; image defaults come from Dockerfiles. Revalidate readiness after overrides. | Docker; recreate affected containers |
| C248<br>`services.<service>.healthcheck.retries`<br>integer | `3` (S21) | See default and scope | — | **B** Failure threshold; image defaults come from Dockerfiles. Revalidate readiness after overrides. | Docker; recreate affected containers |
| C249<br>`services.<service>.healthcheck.test`<br>command | `backend readiness; web /healthz; Agent authenticated probe` (S21) | See default and scope | — | **D** Image health-check command; image defaults come from Dockerfiles. Revalidate readiness after overrides. | Docker; recreate affected containers |

## Persistent instance launcher (instance.conf)

[Configuration and usage](installation/instance-launcher.md). These five control-directory fields do not belong in application.properties.

| ID / field | Level | Initial value | Description |
| --- | --- | --- | --- |
| C250 / `instance.conf:version` | D | 1 (S33/S34) | Control-file format version; currently 1, do not edit. |
| C251 / `instance.conf:bundle` | C | configure: program directory (S33/S34) | Complete program directory; change while stopped through the upgrade procedure. |
| C252 / `instance.conf:home` | C | configure: selected data directory (S33/S34) | Instance data directory; overrides inherited IJOB_HOME. Changing it does not migrate data. |
| C253 / `instance.conf:lang` | A | en; zh selects Chinese (S33/S34) | Instance launcher language; overrides inherited IJOB_LANG, without changing the browser language. |
| C254 / `instance.conf:mode` | C | Windows: windows; Bash: linux (or explicit docker) (S33/S34) | Deployment mode chosen at registration; editing it does not migrate the instance. Distinct from STANDALONE/CLUSTER. |

## Configuration sources

Paths are relative to the repository root. The shared machine inventory is `document/reference/configuration-review.json`, containing bilingual descriptions, source hashes and classifications. Both languages use the same entries; source IDs refer to the table below. Compare Agent-specific Compose values with compose.agent.yaml and cluster values with compose.cluster.yaml; check probe declarations in all three Dockerfiles.

| ID | Source path |
| --- | --- |
| S01 | `develop/api/ijob/ijob-starter/src/main/resources/application.yml` |
| S02 | `develop/api/ijob/ijob-starter/src/main/resources/application-mail.yml` |
| S03 | `develop/api/ijob/ijob-starter/src/main/resources/database/mysql.yml` |
| S04 | `develop/api/ijob/ijob-starter/src/main/resources/database/oracle.yml` |
| S05 | `develop/api/ijob/ijob-starter/src/main/resources/database/postgresql.yml` |
| S06 | `develop/api/ijob/ijob-starter/src/main/resources/database/sqlite.yml` |
| S07 | `develop/api/ijob/ijob-starter/src/main/resources/database/sqlserver.yml` |
| S08 | `develop/api/ijob-monitor-agent/src/main/resources/application.yaml` |
| S09 | `develop/api/ijob-monitor-agent/src/main/java/com/ifielsc/ijob/monitor/agent/config/AgentProperties.java` |
| S10 | `develop/api/ijob/ijob-setup-tool/src/main/java/com/ifielsc/ijob/setup/SetupTool.java` |
| S11 | `develop/deployment/first-run/support/manage.ps1` |
| S12 | `develop/deployment/first-run/support/logging-native.xml` |
| S13 | `develop/api/ijob-monitor-agent/Dockerfile` |
| S14 | `develop/deployment/first-run/support/native-common.sh` |
| S15 | `develop/deployment/first-run/legacy/start-docker.sh` |
| S16 | `develop/deployment/first-run/legacy/start.sh` |
| S17 | `develop/deployment/first-run/legacy/stop.sh` |
| S18 | `develop/deployment/docker/compose.yaml` |
| S19 | `develop/deployment/docker/compose.agent.yaml` |
| S20 | `develop/deployment/docker/images.env` |
| S21 | `develop/api/ijob/Dockerfile` |
| S22 | `develop/web/ijob/vite.config.ts` |
| S23 | `tools/distribution/build.py` |
| S24 | `develop/api/ijob/ijob-common/src/main/java/com/ifielsc/ijob/common/config/AppProperties.java` |
| S25 | `develop/api/ijob/ijob-plugin/src/main/java/com/ifielsc/ijob/plugin/PluginAvailabilityProperties.java` |
| S26 | `develop/api/ijob/ijob-plugin/src/main/java/com/ifielsc/ijob/plugin/localjar/LocalJarPluginProperties.java` |
| S27 | `develop/api/ijob/ijob-framework/src/main/java/com/ifielsc/ijob/framework/runtime/StartupConfigurationValidator.java` |
| S28 | `develop/api/ijob/ijob-infra/src/main/resources/mapper/coordination/TriggerWorkItemMapper.xml` |
| S29 | `develop/api/ijob/ijob-core/src/main/java/com/ifielsc/ijob/core/notify/channel/email/EmailNotifyChannel.java` |
| S30 | `develop/web/ijob/Dockerfile` |
| S31 | `develop/api/ijob/ijob-common/src/main/java/com/ifielsc/ijob/common/routing/ExecutionGroupCodes.java` |
| S32 | `develop/api/ijob/ijob-common/src/main/java/com/ifielsc/ijob/common/routing/ExecutionTargetIds.java` |
| S33 | `develop/deployment/first-run/support/instance.ps1` |
| S34 | `develop/deployment/first-run/ijob.sh` |

Use [Configuration](configuration.md) to choose routing, plugin, capacity, and other topics. Follow the corresponding operations procedure for database or credential changes. Business settings for tasks, resources, and other pages are covered in the [Page reference](user-guide/README.md).
