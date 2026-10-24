# Network, language and logs

[简体中文](../../zh-CN/configuration/common.md) · [Manual home](../README.md)

Native defaults are `server.address=127.0.0.1` and `server.port=18080`. Configure a controlled ingress for remote access. Docker keeps 0.0.0.0:8080 internally and changes the host mapping separately. After port changes, check firewall, reverse proxy and browser address.

Browser language/theme/display time zone are user preferences. `spring.web.locale` is the server fallback and `app.notification.locale=en/zh` is the notification default. Neither replaces the schedule's business time zone.

Native log size, days and total-size limits apply together; the total cap may remove younger archives. Shared HTTP timeouts do not describe every plugin-specific timeout. Resource-browser roots constrain the corresponding LOCAL browsing entry, not all script filesystem access.

Session timeout is total lifetime and active-timeout is idle lifetime, both in seconds. `-1` is a framework sentinel accepted by the checker; ordinary deployments should retain finite limits consistent with their session policy.

## Settings in this topic

These are source/implementation defaults; see the [complete catalog](../configuration-catalog.md) for setup overrides and provenance.

| ID / key | Level / type | Source defaults | Explanation |
| --- | --- | --- | --- |
| C008 / `spring.web.locale` | A / locale | ${IJOB_DEFAULT_LOCALE:en} | Server fallback locale; Accept-Language and frontend preference also apply. |
| C018 / `server.port` | A / port | ${IJOB_SERVER_PORT:8080} | Native service port; retain backend container port 8080 and change host mapping. |
| C022 / `app.resource-browser.local-roots` | A / path-list | [] | Absolute roots allowed for LOCAL draft browsing; not a filesystem sandbox for all scripts. |
| C102 / `app.http.connect-timeout` | A / duration | ${IJOB_HTTP_CONNECT_TIMEOUT:5s} | Shared HTTP client connect timeout; does not override every plugin-specific timeout. |
| C103 / `app.http.read-timeout` | A / duration | ${IJOB_HTTP_READ_TIMEOUT:30s} | Shared HTTP client read timeout; distinct from overall task timeout. |
| C105 / `app.execution.trigger.core-size` | B / integer | ${IJOB_TRIGGER_POOL_CORE_SIZE:4} | trigger executor Core threads; size jointly, distinct from business concurrency groups. |
| C106 / `app.execution.trigger.max-size` | B / integer | ${IJOB_TRIGGER_POOL_MAX_SIZE:20} | trigger executor Maximum threads; size jointly, distinct from business concurrency groups. |
| C108 / `app.execution.trigger.keep-alive` | B / duration | ${IJOB_TRIGGER_POOL_KEEP_ALIVE:60s} | trigger executor Non-core idle-thread lifetime; size jointly, distinct from business concurrency groups. |
| C109 / `app.execution.job.core-size` | B / integer | ${IJOB_JOB_POOL_CORE_SIZE:4} | job executor Core threads; size jointly, distinct from business concurrency groups. |
| C110 / `app.execution.job.max-size` | B / integer | ${IJOB_JOB_POOL_MAX_SIZE:20} | job executor Maximum threads; size jointly, distinct from business concurrency groups. |
| C112 / `app.execution.job.keep-alive` | B / duration | ${IJOB_JOB_POOL_KEEP_ALIVE:60s} | job executor Non-core idle-thread lifetime; size jointly, distinct from business concurrency groups. |
| C113 / `app.execution.dag.core-size` | B / integer | ${IJOB_DAG_POOL_CORE_SIZE:8} | dag executor Core threads; size jointly, distinct from business concurrency groups. |
| C114 / `app.execution.dag.max-size` | B / integer | ${IJOB_DAG_POOL_MAX_SIZE:32} | dag executor Maximum threads; size jointly, distinct from business concurrency groups. |
| C116 / `app.execution.dag.keep-alive` | B / duration | ${IJOB_DAG_POOL_KEEP_ALIVE:60s} | dag executor Non-core idle-thread lifetime; size jointly, distinct from business concurrency groups. |
| C117 / `app.execution.task.core-size` | B / integer | ${IJOB_TASK_POOL_CORE_SIZE:8} | task executor Core threads; size jointly, distinct from business concurrency groups. |
| C118 / `app.execution.task.max-size` | B / integer | ${IJOB_TASK_POOL_MAX_SIZE:32} | task executor Maximum threads; size jointly, distinct from business concurrency groups. |
| C120 / `app.execution.task.keep-alive` | B / duration | ${IJOB_TASK_POOL_KEEP_ALIVE:60s} | task executor Non-core idle-thread lifetime; size jointly, distinct from business concurrency groups. |
| C121 / `app.execution.script.core-size` | B / integer | ${IJOB_SCRIPT_POOL_CORE_SIZE:4} | script executor Core threads; size jointly, distinct from business concurrency groups. |
| C122 / `app.execution.script.max-size` | B / integer | ${IJOB_SCRIPT_POOL_MAX_SIZE:16} | script executor Maximum threads; size jointly, distinct from business concurrency groups. |
| C124 / `app.execution.script.keep-alive` | B / duration | ${IJOB_SCRIPT_POOL_KEEP_ALIVE:60s} | script executor Non-core idle-thread lifetime; size jointly, distinct from business concurrency groups. |
| C143 / `sa-token.timeout` | B / seconds | ${IJOB_AUTH_TIMEOUT_SECONDS:1800} | Total session token lifetime in seconds; tune to session policy. |
| C144 / `sa-token.active-timeout` | B / seconds | ${IJOB_AUTH_ACTIVE_TIMEOUT_SECONDS:300} | Idle session timeout in seconds; distinct from total lifetime. |
| C153 / `logging.level.root` | B / enum | INFO | Root log level; restore after diagnosis and assess volume. |
| C154 / `logging.level.com.ifielsc.ijob` | B / enum | INFO | iJob log level; do not dump secret configuration for diagnosis. |
| C172 / `spring.datasource.hikari.data-source-properties.busy_timeout` | B / milliseconds | ${IJOB_SQLITE_BUSY_TIMEOUT_MS:5000} | Lock-busy wait in milliseconds; SQLite only, assess concurrency and durability. |
| C182 / `server.address` | A / text | generated: native=127.0.0.1; docker=0.0.0.0 | Native bind address; keep Docker binding consistent with probes and proxy. |
| C194 / `ijob.logging.max-file-size` | A / size | 10MB | Native log rollover size per file. |
| C195 / `ijob.logging.max-history` | A / days | 14 | Native log archive retention in days. |
| C196 / `ijob.logging.total-size-cap` | A / size | 200MB | Native archive size cap; may remove archives before age limit. |
