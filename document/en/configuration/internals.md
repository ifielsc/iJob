# Internal configuration appendix

[简体中文](../../zh-CN/configuration/internals.md) · [Manual home](../README.md)

Level D covers framework settings, coordination protocols, database dialect/migration and implementation boundaries. Ordinary deployments retain defaults; these are not required user inputs. Inclusion supports audit and diagnosis, not support for every combination.

Record version, node, database type, timestamps and sanitized errors before consulting [Troubleshooting](../operations/troubleshooting.md). Do not disable migration, security checks or heartbeat/lease protection to suppress a failure. Any necessary tuning needs isolated reproduction, failure/recovery validation and a change record.

## Settings in this topic

These are source/implementation defaults; see the [complete catalog](../configuration-catalog.md) for setup overrides and provenance.

| ID / key | Level / type | Source defaults | Explanation |
| --- | --- | --- | --- |
| C001 / `spring.application.name` | D / text | ijob | Application identity used for logs/metrics, not a business display name. |
| C002 / `spring.messages.fallback-to-system-locale` | D / boolean | false | Disable implicit host-locale fallback for consistent localization. |
| C003 / `spring.messages.encoding` | D / text | UTF-8 | Message resource UTF-8 encoding; retain default. |
| C004 / `spring.profiles.default` | D / text | local | Fallback when no active profile is specified; deployment launchers select one explicitly. |
| C005 / `spring.config.import` | D / text | optional:classpath:database/${IJOB_PLATFORM_DB:mysql}.yml | Select database-specific configuration; determined by database and launcher. |
| C006 / `spring.jackson.default-property-inclusion` | D / text | non_null | API null-value serialization contract. |
| C007 / `spring.jackson.serialization.write-dates-as-timestamps` | D / boolean | false | API date serialization contract. |
| C009 / `spring.web.locale-resolver` | D / text | accept-header | Existing request-header locale resolution rule. |
| C011 / `spring.sql.init.mode` | D / text | never | Prevent a second SQL initialization path alongside formal migrations. |
| C012 / `spring.flyway.enabled` | D / boolean | ${IJOB_FLYWAY_ENABLED:false} | Formal runtime requires Flyway validation; never disable to bypass incompatibility. |
| C013 / `spring.flyway.locations` | D / text | classpath:db/release/${IJOB_PLATFORM_DB:mysql} | Select formal V1 migration locations by database. |
| C014 / `spring.flyway.table` | D / text | ifj_schema_history | Formal migration history table identity. |
| C015 / `spring.flyway.baseline-on-migrate` | D / boolean | false | Prevent implicit baselining of an unknown old database. |
| C016 / `spring.flyway.validate-on-migrate` | D / boolean | true | Retain validation against migration history drift. |
| C017 / `spring.flyway.clean-disabled` | D / boolean | true | Keep database-clean protection enabled. |
| C019 / `server.shutdown` | D / text | graceful | Graceful shutdown; do not bypass draining to exit faster. |
| C020 / `mybatis.mapper-locations` | D / text | classpath*:mapper/**/*.xml | Database mapper resource locations. |
| C021 / `mybatis.configuration.map-underscore-to-camel-case` | D / boolean | true | Column-to-model naming contract. |
| C027 / `app.notification.outbox.enabled` | D / boolean | ${IJOB_NOTIFICATION_OUTBOX_ENABLED:true} | Persistent notification delivery; required in CLUSTER, not a per-notification mute switch. |
| C029 / `app.notification.outbox.renew-interval` | D / duration | ${IJOB_NOTIFICATION_OUTBOX_RENEW_INTERVAL:10s} | Notification delivery: Lease renewal interval. Coordination-sensitive; retain default. |
| C030 / `app.notification.outbox.lease-duration` | D / duration | ${IJOB_NOTIFICATION_OUTBOX_LEASE_DURATION:30s} | Notification delivery: Lease duration. Coordination-sensitive; retain default. |
| C034 / `app.cache-consistency.max-staleness` | D / duration | ${IJOB_CACHE_MAX_STALENESS:5s} | Maximum cache staleness across nodes; part of consistency behavior. |
| C041 / `app.runtime.singleton-lock-name` | D / text | ${IJOB_SINGLETON_LOCK_NAME:ijob:standalone} | Standalone lock identity; changing it can defeat same-database singleton protection. |
| C042 / `app.runtime.heartbeat-interval` | D / duration | ${IJOB_RUNTIME_HEARTBEAT_INTERVAL:5s} | Node heartbeat interval; coupled to failure detection and coordination timing. |
| C043 / `app.runtime.id-segment-size` | D / integer | ${IJOB_ID_SEGMENT_SIZE:1000} | Runtime ID allocation segment size; not a schema or protocol version. |
| C044 / `app.runtime.work-queue.scan-interval` | D / duration | ${IJOB_WORK_QUEUE_SCAN_INTERVAL:1s} | Persistent work queue: Scan interval. Coordination-sensitive; retain default. |
| C045 / `app.runtime.work-queue.max-idle-scan-interval` | D / duration | ${IJOB_WORK_QUEUE_MAX_IDLE_SCAN_INTERVAL:5s} | Persistent work queue: Maximum idle scan interval. Coordination-sensitive; retain default. |
| C046 / `app.runtime.work-queue.scan-jitter` | D / duration | ${IJOB_WORK_QUEUE_SCAN_JITTER:200ms} | Persistent work queue: Scan jitter. Coordination-sensitive; retain default. |
| C047 / `app.runtime.work-queue.renew-interval` | D / duration | ${IJOB_WORK_QUEUE_RENEW_INTERVAL:5s} | Persistent work queue: Lease renewal interval. Coordination-sensitive; retain default. |
| C048 / `app.runtime.work-queue.scan-batch-size` | D / integer | ${IJOB_WORK_QUEUE_SCAN_BATCH_SIZE:100} | Persistent work queue: Scan batch size. Coordination-sensitive; retain default. |
| C050 / `app.runtime.work-queue.lease-duration` | D / duration | ${IJOB_WORK_QUEUE_LEASE_DURATION:30s} | Persistent work queue: Lease duration. Coordination-sensitive; retain default. |
| C051 / `app.runtime.work-queue.retry-delay` | D / duration | ${IJOB_WORK_QUEUE_RETRY_DELAY:5s} | Persistent work queue: Retry delay. Coordination-sensitive; retain default. |
| C052 / `app.runtime.work-queue.max-attempts` | D / integer | ${IJOB_WORK_QUEUE_MAX_ATTEMPTS:5} | Persistent work queue: Internal processing attempt limit. Coordination-sensitive; retain default. |
| C053 / `app.runtime.command.scan-interval` | D / duration | ${IJOB_RUNTIME_COMMAND_SCAN_INTERVAL:250ms} | Runtime command coordination: Scan interval. Coordination-sensitive; retain default. |
| C054 / `app.runtime.command.scan-batch-size` | D / integer | ${IJOB_RUNTIME_COMMAND_SCAN_BATCH_SIZE:100} | Runtime command coordination: Scan batch size. Coordination-sensitive; retain default. |
| C055 / `app.runtime.command.retry-delay` | D / duration | ${IJOB_RUNTIME_COMMAND_RETRY_DELAY:250ms} | Runtime command coordination: Retry delay. Coordination-sensitive; retain default. |
| C056 / `app.runtime.command.timeout` | D / duration | ${IJOB_RUNTIME_COMMAND_TIMEOUT:5m} | Runtime command coordination: Internal command timeout. Coordination-sensitive; retain default. |
| C057 / `app.runtime.concurrency-permit.wait-timeout` | D / duration | ${IJOB_CONCURRENCY_PERMIT_WAIT_TIMEOUT:5m} | Concurrency permit coordination: Concurrency-permit wait timeout. Coordination-sensitive; retain default. |
| C058 / `app.runtime.concurrency-permit.retry-interval` | D / duration | ${IJOB_CONCURRENCY_PERMIT_RETRY_INTERVAL:100ms} | Concurrency permit coordination: Retry interval. Coordination-sensitive; retain default. |
| C059 / `app.runtime.concurrency-permit.max-retry-interval` | D / duration | ${IJOB_CONCURRENCY_PERMIT_MAX_RETRY_INTERVAL:2s} | Concurrency permit coordination: Maximum retry interval. Coordination-sensitive; retain default. |
| C060 / `app.runtime.concurrency-permit.retry-jitter` | D / duration | ${IJOB_CONCURRENCY_PERMIT_RETRY_JITTER:100ms} | Concurrency permit coordination: Retry jitter. Coordination-sensitive; retain default. |
| C061 / `app.runtime.concurrency-permit.renew-interval` | D / duration | ${IJOB_CONCURRENCY_PERMIT_RENEW_INTERVAL:5s} | Concurrency permit coordination: Lease renewal interval. Coordination-sensitive; retain default. |
| C062 / `app.runtime.concurrency-permit.lease-duration` | D / duration | ${IJOB_CONCURRENCY_PERMIT_LEASE_DURATION:30s} | Concurrency permit coordination: Lease duration. Coordination-sensitive; retain default. |
| C063 / `app.runtime.maintenance.enabled` | D / boolean | ${IJOB_MAINTENANCE_ENABLED:true} | Runtime maintenance: Runtime maintenance/recovery switch. Coordination-sensitive; retain default. |
| C064 / `app.runtime.maintenance.scan-interval` | D / duration | ${IJOB_MAINTENANCE_SCAN_INTERVAL:5s} | Runtime maintenance: Scan interval. Coordination-sensitive; retain default. |
| C065 / `app.runtime.maintenance.renew-interval` | D / duration | ${IJOB_MAINTENANCE_RENEW_INTERVAL:10s} | Runtime maintenance: Lease renewal interval. Coordination-sensitive; retain default. |
| C066 / `app.runtime.maintenance.lease-duration` | D / duration | ${IJOB_MAINTENANCE_LEASE_DURATION:30s} | Runtime maintenance: Lease duration. Coordination-sensitive; retain default. |
| C067 / `app.runtime.maintenance.stale-run-timeout` | D / duration | ${IJOB_MAINTENANCE_STALE_RUN_TIMEOUT:30s} | Runtime maintenance: Stale-run timeout. Coordination-sensitive; retain default. |
| C068 / `app.runtime.maintenance.orphan-grace` | D / duration | ${IJOB_MAINTENANCE_ORPHAN_GRACE:1m} | Runtime maintenance: Orphan recovery grace. Coordination-sensitive; retain default. |
| C069 / `app.runtime.maintenance.batch-size` | D / integer | ${IJOB_MAINTENANCE_BATCH_SIZE:100} | Runtime maintenance: Batch size. Coordination-sensitive; retain default. |
| C070 / `app.runtime.maintenance.max-batches-per-run` | D / integer | ${IJOB_MAINTENANCE_MAX_BATCHES_PER_RUN:10} | Runtime maintenance: Maximum batches per run. Coordination-sensitive; retain default. |
| C088 / `app.event.recovery.enabled` | D / boolean | ${IJOB_EVENT_RECOVERY_ENABLED:true} | Persisted-event recovery; disabling changes recovery of waiting events. |
| C089 / `app.event.recovery.scan-interval` | D / duration | ${IJOB_EVENT_RECOVERY_SCAN_INTERVAL:1s} | Event recovery: Scan interval. Coordination-sensitive; retain default. |
| C090 / `app.event.recovery.scan-batch-size` | D / integer | ${IJOB_EVENT_RECOVERY_SCAN_BATCH_SIZE:100} | Event recovery: Scan batch size. Coordination-sensitive; retain default. |
| C145 / `sa-token.is-concurrent` | D / boolean | false | Concurrent login policy; part of the verified authentication contract, not routine tuning. |
| C146 / `sa-token.is-share` | D / boolean | false | Session-sharing policy; part of the verified authentication contract, not routine tuning. |
| C147 / `sa-token.is-read-cookie` | D / boolean | false | Cookie-reading policy; part of the verified authentication contract, not routine tuning. |
| C148 / `sa-token.is-read-header` | D / boolean | true | Header token reading; part of the verified authentication contract, not routine tuning. |
| C149 / `sa-token.is-write-header` | D / boolean | false | Response-header token writing; part of the verified authentication contract, not routine tuning. |
| C150 / `sa-token.token-style` | D / text | uuid | Token generation style; part of the verified authentication contract, not routine tuning. |
| C151 / `sa-token.is-log` | D / boolean | false | Authentication component logging; part of the verified authentication contract, not routine tuning. |
| C152 / `logging.pattern.level` | D / text | %5p [traceId=%X{traceId:-} trigger=%X{triggerId:-} root=%X{rootInstanceId:-} instance=%X{instanceId:-} command=%X{commandId:-} outbox=%X{outboxId:-} attempt=%X{attempt:-} taskAttempt=%X{taskAttempt:-} fence=%X{fence:-}] | Correlation-ID log pattern; diagnostics depend on these fields. |
| C155 / `management.metrics.tags.application` | D / text | ${spring.application.name} | Application metric tag defaults to application name. |
| C157 / `management.endpoint.health.probes.enabled` | D / boolean | true | Enable readiness/liveness probe support. |
| C158 / `management.endpoint.health.group.readiness.include` | D / text | readinessState,runtime,singletonLock,coordination,db | Readiness aggregation includes runtime, singleton lock, coordination and database. |
| C160 / `management.health.livenessstate.enabled` | D / boolean | true | Liveness state probe; retain deployment probe contract. |
| C161 / `management.health.readinessstate.enabled` | D / boolean | true | Readiness state probe; retain deployment probe contract. |
| C162 / `spring.config.activate.on-profile` | D / text | mail | Mail-profile activation condition; configuration structure. |
| C164 / `spring.datasource.hikari.connection-init-sql` | D / text | SET time_zone = '+00:00'; ALTER SESSION SET TIME_ZONE = 'UTC'; SET TIME ZONE 'UTC'; PRAGMA foreign_keys = ON; SET DATEFORMAT ymd | Database session initialization for UTC, date parsing or foreign keys. |
| C165 / `pagehelper.helper-dialect` | D / text | mysql; oracle; postgresql; sqlite; sqlserver2012 | Pagination dialect must match database vendor. |
| C166 / `spring.datasource.driver-class-name` | D / class | oracle.jdbc.OracleDriver; org.sqlite.JDBC; com.microsoft.sqlserver.jdbc.SQLServerDriver | Setup selects the matching database driver; do not change independently. |
| C169 / `mybatis.configuration.jdbc-type-for-null` | D / text | NULL | Oracle null binding rule. |
| C173 / `spring.datasource.hikari.data-source-properties.journal_mode` | D / enum | WAL | WAL journal mode; SQLite only, assess concurrency and durability. |
| C174 / `spring.datasource.hikari.data-source-properties.synchronous` | D / enum | NORMAL | Synchronous durability policy; SQLite only, assess concurrency and durability. |
| C175 / `spring.datasource.hikari.data-source-properties.transaction_mode` | D / enum | IMMEDIATE | IMMEDIATE transaction mode; SQLite only, assess concurrency and durability. |
| C176 / `spring.application.name` | D / text | ijob-monitor-agent | Agent application identity. |
| C186 / `spring.profiles.active` | D / text-list | release | Launcher-selected release/native-background or container profile; not a normal feature switch. |
| C187 / `app.web.enabled` | D / boolean | native=true; docker uses separate web service | Native static UI hosting switch; determined by deployment mode, with separate Docker web service. |
| C188 / `app.web.directory` | D / path | native=<bundle>/web-dist | Static UI directory; native launcher supplies current bundle path, not a frontend rebuild. |
| C189 / `spring.config.additional-location` | D / uri | launcher: external private configuration URI | Launcher-selected private external configuration location; avoid competing entry points. |
| C190 / `logging.config` | D / uri | launcher: <bundle>/logging-native.xml | Native logging configuration resource, set by launcher. |
| C191 / `ijob.native.home` | D / path | launcher: IJOB_HOME process identity | Process ownership marker; launcher-managed, never spoof or edit independently. |
| C192 / `ijob.native.log-directory` | D / path | launcher: <IJOB_HOME>/logs | Log directory derived from IJOB_HOME; manage with the installation. |
| C193 / `ijob.native.log-session` | D / text | launcher-generated unique launch identity | Per-launch readiness log marker generated by launcher. |
| C198 / `spring.config.import` | D / uri | configtree:/run/secrets/ in image | Image import of private agent.token; align with mounts. |
| C208 / `IJOB_IMAGE_TAG` | D / text | 1.0.0 | Advanced Compose tag only; distributions launch pinned image IDs. A tag is not provenance verification. |
| C212 / `IJOB_MAVEN_IMAGE` | D / image | pinned in develop/deployment/docker/images.env | Build base-image pin; update through dependency review, not runtime tuning. |
| C213 / `IJOB_JRE_IMAGE` | D / image | pinned in develop/deployment/docker/images.env | Build base-image pin; update through dependency review, not runtime tuning. |
| C214 / `IJOB_NODE_IMAGE` | D / image | pinned in develop/deployment/docker/images.env | Build base-image pin; update through dependency review, not runtime tuning. |
| C215 / `IJOB_NGINX_IMAGE` | D / image | pinned in develop/deployment/docker/images.env | Build base-image pin; update through dependency review, not runtime tuning. |
| C218 / `SPRING_PROFILES_ACTIVE` | D / text | backend image container; first-run override release | Image/launcher profile selection; not a routine environment toggle. |
| C219 / `SPRING_CONFIG_ADDITIONAL_LOCATION` | D / uri | image /run/secrets/ijob.yaml; first-run /run/secrets/application.properties | Docker external configuration location; must match mounts. |
| C220 / `SPRING_CONFIG_IMPORT` | D / uri | agent image configtree:/run/secrets/ | Agent image reads private token through a configuration tree. |
| C221 / `SERVER_PORT` | D / port | Agent image 8081 | Agent image internal port; synchronize probes and connection settings. |
| C222 / `IJOB_BACKEND_IMAGE` | D / image-id | generated sha256 image ID in docker-images.lock | Generated distribution image-lock record, not an ordinary environment override for launch. |
| C223 / `IJOB_WEB_IMAGE` | D / image-id | generated sha256 image ID in docker-images.lock | Generated distribution image-lock record, not an ordinary environment override for launch. |
| C224 / `IJOB_AGENT_IMAGE` | D / image-id | generated sha256 image ID in docker-images.lock | Generated distribution image-lock record, not an ordinary environment override for launch. |
| C226 / `services.<service>.image` | D / compose-field | distribution exact image ID override | Bundle manages exact image pins; changing a fixed 1.0.0 tag is not a verified upgrade. |
| C238 / `services.<service>.read_only` | D / compose-field | true | Read-only container root filesystem; use explicit data mounts. |
| C239 / `services.<service>.cap_drop` | D / compose-field | ALL | Retain minimal container capabilities; do not broadly relax for task features. |
| C240 / `services.<service>.security_opt` | D / compose-field | no-new-privileges:true | Retain no-new-privileges restriction. |
| C241 / `services.<service>.init` | D / compose-field | backend/agent true; web not set | Container signal forwarding/reaping; retain verified entrypoint behavior. |
| C242 / `services.web.depends_on` | D / compose-field | backend health; cluster also backend-b health | Startup health dependencies; not continuous traffic health management. |
| C249 / `services.<service>.healthcheck.test` | D / command | backend readiness; web /healthz; Agent authenticated probe | Image health-check command; image defaults come from Dockerfiles. Revalidate readiness after overrides. |
