# 内部配置附录

[English](../../en/configuration/internals.md) · [手册首页](../README.md)

D 级项用于框架、协调协议、数据库方言/迁移和实现边界。普通使用保留默认，不将其复制成用户必填配置。清单保留它们是为了审计和定位，并不代表所有组合都受支持。

定位问题时先收集版本、节点、数据库类型、时间与脱敏错误，再对照[排障](../operations/troubleshooting.md)。不要通过关闭迁移、安全校验、心跳/租约保护来让错误消失。若确需调整，先在独立环境复现、验证故障和恢复路径并形成变更记录。

## 本主题配置索引

下列为源码/实现默认值；初始化覆盖、来源和完整说明见[完整清单](../configuration-catalog.md)。

| ID / 配置键 | 级别 / 类型 | 默认值来源 | 说明 |
| --- | --- | --- | --- |
| C001 / `spring.application.name` | D / text | ijob | 应用标识；影响日志/指标标识，不作为业务名称设置。 |
| C002 / `spring.messages.fallback-to-system-locale` | D / boolean | false | 禁用跟随宿主语言的隐式回退，保持国际化一致。 |
| C003 / `spring.messages.encoding` | D / text | UTF-8 | 消息资源 UTF-8 编码，保持默认。 |
| C004 / `spring.profiles.default` | D / text | local | 未指定活动 profile 时的回退；正式启动器显式选择 profile。 |
| C005 / `spring.config.import` | D / text | optional:classpath:database/${IJOB_PLATFORM_DB:mysql}.yml | 选择数据库专用配置；由数据库产品和启动方式决定。 |
| C006 / `spring.jackson.default-property-inclusion` | D / text | non_null | API 空值序列化契约。 |
| C007 / `spring.jackson.serialization.write-dates-as-timestamps` | D / boolean | false | API 日期序列化契约。 |
| C009 / `spring.web.locale-resolver` | D / text | accept-header | 依据请求头解析服务端语言的既有规则。 |
| C011 / `spring.sql.init.mode` | D / text | never | 禁止另一套 SQL 初始化与正式迁移并行运行。 |
| C012 / `spring.flyway.enabled` | D / boolean | ${IJOB_FLYWAY_ENABLED:false} | 正式运行要求 Flyway 校验；不能关闭来绕过不兼容库。 |
| C013 / `spring.flyway.locations` | D / text | classpath:db/release/${IJOB_PLATFORM_DB:mysql} | 正式 V1 迁移目录按数据库选择。 |
| C014 / `spring.flyway.table` | D / text | ifj_schema_history | 正式迁移历史表身份。 |
| C015 / `spring.flyway.baseline-on-migrate` | D / boolean | false | 禁止隐式把未知旧库认作基线。 |
| C016 / `spring.flyway.validate-on-migrate` | D / boolean | true | 保持迁移校验，防止历史内容漂移。 |
| C017 / `spring.flyway.clean-disabled` | D / boolean | true | 禁止清库，保持保护开启。 |
| C019 / `server.shutdown` | D / text | graceful | 优雅关闭模式；不要为加快退出绕过运行排空。 |
| C020 / `mybatis.mapper-locations` | D / text | classpath*:mapper/**/*.xml | 数据库映射文件定位。 |
| C021 / `mybatis.configuration.map-underscore-to-camel-case` | D / boolean | true | 数据库列名到模型字段的映射契约。 |
| C027 / `app.notification.outbox.enabled` | D / boolean | ${IJOB_NOTIFICATION_OUTBOX_ENABLED:true} | 持久通知投递机制；CLUSTER 要求开启，不能用作业务通知停用按钮。 |
| C029 / `app.notification.outbox.renew-interval` | D / duration | ${IJOB_NOTIFICATION_OUTBOX_RENEW_INTERVAL:10s} | 通知投递：租约续期间隔。涉及协调一致性，保留默认。 |
| C030 / `app.notification.outbox.lease-duration` | D / duration | ${IJOB_NOTIFICATION_OUTBOX_LEASE_DURATION:30s} | 通知投递：租约时长。涉及协调一致性，保留默认。 |
| C034 / `app.cache-consistency.max-staleness` | D / duration | ${IJOB_CACHE_MAX_STALENESS:5s} | 跨节点缓存最大陈旧窗口；属于一致性协议的运行参数。 |
| C041 / `app.runtime.singleton-lock-name` | D / text | ${IJOB_SINGLETON_LOCK_NAME:ijob:standalone} | 单实例互斥锁身份；改变它可能破坏同库单实例保护。 |
| C042 / `app.runtime.heartbeat-interval` | D / duration | ${IJOB_RUNTIME_HEARTBEAT_INTERVAL:5s} | 节点心跳周期；与失效判定和协调时间共同约束。 |
| C043 / `app.runtime.id-segment-size` | D / integer | ${IJOB_ID_SEGMENT_SIZE:1000} | 运行表 ID 分段大小；不改变正式数据库或协议版本。 |
| C044 / `app.runtime.work-queue.scan-interval` | D / duration | ${IJOB_WORK_QUEUE_SCAN_INTERVAL:1s} | 持久工作队列：扫描周期。涉及协调一致性，保留默认。 |
| C045 / `app.runtime.work-queue.max-idle-scan-interval` | D / duration | ${IJOB_WORK_QUEUE_MAX_IDLE_SCAN_INTERVAL:5s} | 持久工作队列：空闲扫描间隔上限。涉及协调一致性，保留默认。 |
| C046 / `app.runtime.work-queue.scan-jitter` | D / duration | ${IJOB_WORK_QUEUE_SCAN_JITTER:200ms} | 持久工作队列：扫描抖动范围。涉及协调一致性，保留默认。 |
| C047 / `app.runtime.work-queue.renew-interval` | D / duration | ${IJOB_WORK_QUEUE_RENEW_INTERVAL:5s} | 持久工作队列：租约续期间隔。涉及协调一致性，保留默认。 |
| C048 / `app.runtime.work-queue.scan-batch-size` | D / integer | ${IJOB_WORK_QUEUE_SCAN_BATCH_SIZE:100} | 持久工作队列：每轮扫描批量。涉及协调一致性，保留默认。 |
| C050 / `app.runtime.work-queue.lease-duration` | D / duration | ${IJOB_WORK_QUEUE_LEASE_DURATION:30s} | 持久工作队列：租约时长。涉及协调一致性，保留默认。 |
| C051 / `app.runtime.work-queue.retry-delay` | D / duration | ${IJOB_WORK_QUEUE_RETRY_DELAY:5s} | 持久工作队列：重试等待间隔。涉及协调一致性，保留默认。 |
| C052 / `app.runtime.work-queue.max-attempts` | D / integer | ${IJOB_WORK_QUEUE_MAX_ATTEMPTS:5} | 持久工作队列：内部处理尝试次数上限。涉及协调一致性，保留默认。 |
| C053 / `app.runtime.command.scan-interval` | D / duration | ${IJOB_RUNTIME_COMMAND_SCAN_INTERVAL:250ms} | 运行命令协调：扫描周期。涉及协调一致性，保留默认。 |
| C054 / `app.runtime.command.scan-batch-size` | D / integer | ${IJOB_RUNTIME_COMMAND_SCAN_BATCH_SIZE:100} | 运行命令协调：每轮扫描批量。涉及协调一致性，保留默认。 |
| C055 / `app.runtime.command.retry-delay` | D / duration | ${IJOB_RUNTIME_COMMAND_RETRY_DELAY:250ms} | 运行命令协调：重试等待间隔。涉及协调一致性，保留默认。 |
| C056 / `app.runtime.command.timeout` | D / duration | ${IJOB_RUNTIME_COMMAND_TIMEOUT:5m} | 运行命令协调：内部命令超时。涉及协调一致性，保留默认。 |
| C057 / `app.runtime.concurrency-permit.wait-timeout` | D / duration | ${IJOB_CONCURRENCY_PERMIT_WAIT_TIMEOUT:5m} | 并发许可协调：等待并发许可的时限。涉及协调一致性，保留默认。 |
| C058 / `app.runtime.concurrency-permit.retry-interval` | D / duration | ${IJOB_CONCURRENCY_PERMIT_RETRY_INTERVAL:100ms} | 并发许可协调：重试间隔。涉及协调一致性，保留默认。 |
| C059 / `app.runtime.concurrency-permit.max-retry-interval` | D / duration | ${IJOB_CONCURRENCY_PERMIT_MAX_RETRY_INTERVAL:2s} | 并发许可协调：重试间隔上限。涉及协调一致性，保留默认。 |
| C060 / `app.runtime.concurrency-permit.retry-jitter` | D / duration | ${IJOB_CONCURRENCY_PERMIT_RETRY_JITTER:100ms} | 并发许可协调：重试抖动范围。涉及协调一致性，保留默认。 |
| C061 / `app.runtime.concurrency-permit.renew-interval` | D / duration | ${IJOB_CONCURRENCY_PERMIT_RENEW_INTERVAL:5s} | 并发许可协调：租约续期间隔。涉及协调一致性，保留默认。 |
| C062 / `app.runtime.concurrency-permit.lease-duration` | D / duration | ${IJOB_CONCURRENCY_PERMIT_LEASE_DURATION:30s} | 并发许可协调：租约时长。涉及协调一致性，保留默认。 |
| C063 / `app.runtime.maintenance.enabled` | D / boolean | ${IJOB_MAINTENANCE_ENABLED:true} | 运行维护：运行维护恢复开关。涉及协调一致性，保留默认。 |
| C064 / `app.runtime.maintenance.scan-interval` | D / duration | ${IJOB_MAINTENANCE_SCAN_INTERVAL:5s} | 运行维护：扫描周期。涉及协调一致性，保留默认。 |
| C065 / `app.runtime.maintenance.renew-interval` | D / duration | ${IJOB_MAINTENANCE_RENEW_INTERVAL:10s} | 运行维护：租约续期间隔。涉及协调一致性，保留默认。 |
| C066 / `app.runtime.maintenance.lease-duration` | D / duration | ${IJOB_MAINTENANCE_LEASE_DURATION:30s} | 运行维护：租约时长。涉及协调一致性，保留默认。 |
| C067 / `app.runtime.maintenance.stale-run-timeout` | D / duration | ${IJOB_MAINTENANCE_STALE_RUN_TIMEOUT:30s} | 运行维护：过期节点判定时限。涉及协调一致性，保留默认。 |
| C068 / `app.runtime.maintenance.orphan-grace` | D / duration | ${IJOB_MAINTENANCE_ORPHAN_GRACE:1m} | 运行维护：孤立运行恢复宽限。涉及协调一致性，保留默认。 |
| C069 / `app.runtime.maintenance.batch-size` | D / integer | ${IJOB_MAINTENANCE_BATCH_SIZE:100} | 运行维护：批次大小。涉及协调一致性，保留默认。 |
| C070 / `app.runtime.maintenance.max-batches-per-run` | D / integer | ${IJOB_MAINTENANCE_MAX_BATCHES_PER_RUN:10} | 运行维护：单轮最大批次数。涉及协调一致性，保留默认。 |
| C088 / `app.event.recovery.enabled` | D / boolean | ${IJOB_EVENT_RECOVERY_ENABLED:true} | 持久事件恢复机制；关闭会改变等待事件的恢复能力。 |
| C089 / `app.event.recovery.scan-interval` | D / duration | ${IJOB_EVENT_RECOVERY_SCAN_INTERVAL:1s} | 事件恢复：扫描周期。涉及协调一致性，保留默认。 |
| C090 / `app.event.recovery.scan-batch-size` | D / integer | ${IJOB_EVENT_RECOVERY_SCAN_BATCH_SIZE:100} | 事件恢复：每轮扫描批量。涉及协调一致性，保留默认。 |
| C145 / `sa-token.is-concurrent` | D / boolean | false | 同账号并发登录策略；属于已验证的认证契约，不作为普通调参。 |
| C146 / `sa-token.is-share` | D / boolean | false | 会话共享策略；属于已验证的认证契约，不作为普通调参。 |
| C147 / `sa-token.is-read-cookie` | D / boolean | false | Cookie 读取策略；属于已验证的认证契约，不作为普通调参。 |
| C148 / `sa-token.is-read-header` | D / boolean | true | 请求头令牌读取；属于已验证的认证契约，不作为普通调参。 |
| C149 / `sa-token.is-write-header` | D / boolean | false | 响应头令牌写入；属于已验证的认证契约，不作为普通调参。 |
| C150 / `sa-token.token-style` | D / text | uuid | 令牌生成格式；属于已验证的认证契约，不作为普通调参。 |
| C151 / `sa-token.is-log` | D / boolean | false | 认证组件日志开关；属于已验证的认证契约，不作为普通调参。 |
| C152 / `logging.pattern.level` | D / text | %5p [traceId=%X{traceId:-} trigger=%X{triggerId:-} root=%X{rootInstanceId:-} instance=%X{instanceId:-} command=%X{commandId:-} outbox=%X{outboxId:-} attempt=%X{attempt:-} taskAttempt=%X{taskAttempt:-} fence=%X{fence:-}] | 运行关联 ID 的日志格式；排障依赖这些字段。 |
| C155 / `management.metrics.tags.application` | D / text | ${spring.application.name} | 指标应用标签，默认引用应用名称。 |
| C157 / `management.endpoint.health.probes.enabled` | D / boolean | true | 开启就绪与存活探针能力。 |
| C158 / `management.endpoint.health.group.readiness.include` | D / text | readinessState,runtime,singletonLock,coordination,db | 就绪聚合条件，包含运行、单实例锁、协调与数据库健康。 |
| C160 / `management.health.livenessstate.enabled` | D / boolean | true | 存活状态探针，保留部署探测契约。 |
| C161 / `management.health.readinessstate.enabled` | D / boolean | true | 就绪状态探针，保留部署探测契约。 |
| C162 / `spring.config.activate.on-profile` | D / text | mail | 邮件 profile 的激活条件，属于配置结构。 |
| C164 / `spring.datasource.hikari.connection-init-sql` | D / text | SET time_zone = '+00:00'; ALTER SESSION SET TIME_ZONE = 'UTC'; SET TIME ZONE 'UTC'; PRAGMA foreign_keys = ON; SET DATEFORMAT ymd | 数据库会话初始化，涉及 UTC、日期格式或外键约束。 |
| C165 / `pagehelper.helper-dialect` | D / text | mysql; oracle; postgresql; sqlite; sqlserver2012 | 数据库分页方言，与数据库产品一致。 |
| C166 / `spring.datasource.driver-class-name` | D / class | oracle.jdbc.OracleDriver; org.sqlite.JDBC; com.microsoft.sqlserver.jdbc.SQLServerDriver | 初始化器按数据库产品选择驱动，不独立修改。 |
| C169 / `mybatis.configuration.jdbc-type-for-null` | D / text | NULL | Oracle 空值绑定规则。 |
| C173 / `spring.datasource.hikari.data-source-properties.journal_mode` | D / enum | WAL | WAL 日志模式；仅 SQLite，评估并发和持久性影响。 |
| C174 / `spring.datasource.hikari.data-source-properties.synchronous` | D / enum | NORMAL | 同步持久性策略；仅 SQLite，评估并发和持久性影响。 |
| C175 / `spring.datasource.hikari.data-source-properties.transaction_mode` | D / enum | IMMEDIATE | IMMEDIATE 事务模式；仅 SQLite，评估并发和持久性影响。 |
| C176 / `spring.application.name` | D / text | ijob-monitor-agent | Agent 应用标识。 |
| C186 / `spring.profiles.active` | D / text-list | release | 启动器选择 release/native-background 或容器配置，非日常功能开关。 |
| C187 / `app.web.enabled` | D / boolean | 原生为 `true`；Docker 使用独立 Web 服务 | 原生静态前端托管开关；由安装方式决定，Docker 使用独立 Web 容器。 |
| C188 / `app.web.directory` | D / path | 原生为 `<bundle>/web-dist` | 前端资源目录；原生启动器按当前包传入，换目录不会重建前端。 |
| C189 / `spring.config.additional-location` | D / uri | 启动器指定的外部私有配置 URI | 启动器指定私有外部配置的位置，勿同时维护多个冲突入口。 |
| C190 / `logging.config` | D / uri | 启动器指定 `<bundle>/support/logging-native.xml` | 原生日志配置资源位置，由启动器设置。 |
| C191 / `ijob.native.home` | D / path | 启动器使用 `IJOB_HOME` 标记进程身份 | 进程身份校验标记；启动器生成，不可冒用或独立改写。 |
| C192 / `ijob.native.log-directory` | D / path | 启动器指定 `<IJOB_HOME>/logs` | 日志目录由 IJOB_HOME 派生，随安装目录管理。 |
| C193 / `ijob.native.log-session` | D / text | 启动器为每次启动生成唯一标识 | 本次启动的就绪日志标记，由启动器生成。 |
| C198 / `spring.config.import` | D / uri | 镜像中为 `configtree:/run/secrets/` | 镜像私有 agent.token 文件导入方式，保留与挂载一致。 |
| C208 / `IJOB_IMAGE_TAG` | D / text | 1.0.0 | 仅高级 Compose 标签；交付包按镜像 ID 锁启动，标签不替代来源校验。 |
| C212 / `IJOB_MAVEN_IMAGE` | D / image | 固定在 `develop/deployment/docker/images.env` | 构建基础镜像锁；按依赖升级审核更新，不作为运行调参。 |
| C213 / `IJOB_JRE_IMAGE` | D / image | 固定在 `develop/deployment/docker/images.env` | 构建基础镜像锁；按依赖升级审核更新，不作为运行调参。 |
| C214 / `IJOB_NODE_IMAGE` | D / image | 固定在 `develop/deployment/docker/images.env` | 构建基础镜像锁；按依赖升级审核更新，不作为运行调参。 |
| C215 / `IJOB_NGINX_IMAGE` | D / image | 固定在 `develop/deployment/docker/images.env` | 构建基础镜像锁；按依赖升级审核更新，不作为运行调参。 |
| C218 / `SPRING_PROFILES_ACTIVE` | D / text | backend 镜像使用 `container`；首次初始化覆盖为 `release` | 由镜像/启动器选择配置 profile；不当作普通环境开关。 |
| C219 / `SPRING_CONFIG_ADDITIONAL_LOCATION` | D / uri | 镜像使用 `/run/secrets/ijob.yaml`；首次初始化使用 `/run/secrets/application.properties` | Docker 外部配置挂载读取位置，必须与实际挂载一致。 |
| C220 / `SPRING_CONFIG_IMPORT` | D / uri | Agent 镜像使用 `configtree:/run/secrets/` | Agent 镜像通过配置树读取私有 token 文件。 |
| C221 / `SERVER_PORT` | D / port | Agent 镜像使用 `8081` | Agent 镜像设置内部端口；需要与探针和连接配置同步。 |
| C222 / `IJOB_BACKEND_IMAGE` | D / image-id | 生成并写入 `docker-images.lock` 的 sha256 镜像 ID | 交付包生成的镜像锁记录，不是可覆盖启动镜像的普通环境变量。 |
| C223 / `IJOB_WEB_IMAGE` | D / image-id | 生成并写入 `docker-images.lock` 的 sha256 镜像 ID | 交付包生成的镜像锁记录，不是可覆盖启动镜像的普通环境变量。 |
| C224 / `IJOB_AGENT_IMAGE` | D / image-id | 生成并写入 `docker-images.lock` 的 sha256 镜像 ID | 交付包生成的镜像锁记录，不是可覆盖启动镜像的普通环境变量。 |
| C226 / `services.<service>.image` | D / compose-field | 发行包按精确镜像 ID 覆盖 | 精确镜像锁归包管理；不要只改固定 1.0.0 标签升级。 |
| C238 / `services.<service>.read_only` | D / compose-field | true | 只读容器根文件系统约束，使用明确的数据挂载。 |
| C239 / `services.<service>.cap_drop` | D / compose-field | ALL | 保留最小容器权限，不为业务能力直接放开。 |
| C240 / `services.<service>.security_opt` | D / compose-field | no-new-privileges:true | 保留禁止提权约束。 |
| C241 / `services.<service>.init` | D / compose-field | backend/agent 为 `true`；web 未设置 | 容器进程信号与回收处理，保持经过验证的入口。 |
| C242 / `services.web.depends_on` | D / compose-field | 等待 backend 健康；集群还需等待 backend-b 健康 | 启动就绪依赖；不等于持续流量健康管理。 |
| C249 / `services.<service>.healthcheck.test` | D / command | backend 使用就绪探针；web 使用 `/healthz`；Agent 使用带认证的探针 | 镜像内健康检查命令；镜像默认值来自各 Dockerfile，覆盖后重新验证就绪判定。 |
