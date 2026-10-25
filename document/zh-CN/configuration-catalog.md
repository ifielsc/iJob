# 完整配置清单

[English](../en/configuration-catalog.md) · [目录改进方案](documentation-plan.md) · [当前用户手册](README.md)

状态：目录与 A/B/C/D 分级已经确认；运行默认值保持不变，检查器扩展到 54 项。配置源码基线为 1.0.0 / `525070ea2a61919fd92dad4837f1ee8b5a8894a8`；当前来源哈希见共享清单。

## 1. 范围与阅读方式

这份清单覆盖所列源码中的平台与 Agent 配置属性，并补充启动变量、镜像锁和常用 Compose 运维字段。它不穷举 Spring、JVM、操作系统、Nginx 的全部选项。测试专用配置、历史迁移工具输入、自动生成的 installation.properties/PID 标记不作为用户调参项。任务/作业/资源/通知等页面里的业务字段另列入操作手册，不与 application.properties 混放。

平台属性和 Agent 属性以“组件 + 键名”区分；例如两者都有 server.port。环境变量别名是同一属性的入口，不算独立功能。Compose 的 `<service>` 是模板占位符，不是实际服务名称。

| 分级 | 是否建议允许调整 | 文档呈现方式 |
| --- | --- | --- |
| A 常用 | 允许部署管理员按需调整；并非给所有登录用户配置服务器的权限 | 正文提供示例、重启与验证方法 |
| B 高级 | 允许有容量/部署知识的管理员调整，说明联动条件 | 高级参考，给出验证方法和失败现象 |
| C 受控 | 允许按指定运维流程变更；包括账号、凭据、数据库与模式切换 | 专门操作步骤、备份和恢复要求；不是禁止修改 |
| D 内部 | 不建议作为普通用户调参入口 | 完整附录保留说明，不提供“绕过保护”的配置配方 |

分级是已确认的维护建议，不是新增权限控制。settings/check-settings 覆盖 54 项格式检查，详见[配置入口](configuration.md)；不代表已经校验全部 254 项或跨参数业务约束。

### 默认值和生效规则

- “源码声明”保留实际配置表达式，包括 `${变量:回退值}`；相关文件未加载时，该声明不会生效。数据库专用值只适用于选中的数据库；mail profile 中的值仅在该 profile 加载时写入，但邮件实现本身缺省也启用。
- “新安装/覆盖”列出初始化器和平台差异。未另写入表示使用实际加载的配置/实现默认值，不保证所有框架隐式默认值都在清单内。Java 配置类的初始值不应直接当作安装默认值。
- 平台 application.properties 修改后重启本安装；Docker 使用对应包的 start-docker.sh --recreate 重新建立挂载并读取配置。Agent 的配置需重启 Agent；构建变量只在重建镜像/前端时生效。
- IJOB_* 有些只是 YAML 占位符输入或脚本输入，不能笼统认定它们覆盖外部文件。明确的启动参数、系统属性、直接绑定的环境变量也可能参与最终值；不要重复维护同一参数。
- `secret` 项只列变量/生成规则，不提供实际值。普通配置变更不需要重新打包程序。此稿中的建议不改变现有生产或演示环境。

### 类型说明

duration 使用带单位时长；seconds/milliseconds/days 分别为秒/毫秒/天；bytes 是字节数；size 使用实现支持的容量单位。枚举及列表按每行说明填写。分组规范化后最多 16 个，每个匹配 `[a-z0-9][a-z0-9._-]{0,63}`；任务/作业 ID 分别最多 100 个，均须为正整数。整数的相互约束仍由启动校验器判断，不能仅凭类型列推断全部合法范围。

## 2. 已确认的行为边界

### 分组与任务/作业白名单

节点分组与发布定义的分组匹配：定义未配置分组时不施加该条件；有分组时，节点须至少命中一个分组。因此空节点分组只匹配不限制分组的定义。初始化器给节点写入 default，并不自动给所有任务打上 default。

| 任务白名单 | 作业白名单 | 根触发目标可领取范围（还需满足分组等其他条件） |
| --- | --- | --- |
| 空 | 空 | 不按目标 ID 限制 |
| 101 | 空 | 只允许任务 101，不允许作业根触发 |
| 空 | 201 | 只允许作业 201，不允许任务根触发 |
| 101 | 201 | 任务 101 或作业 201 |

这是持久工作队列领取根触发的筛选规则，不是登录用户权限、所有插件的安全沙箱或 DAG 子任务逐一授权表。ID 是示例，必须替换为本环境真实对象 ID。分组和白名单须同时满足；还要检查 WORKER 角色、插件可用性与业务配置。

### 默认值差异和部署入口

- app.runtime.groups：源码为空，首次安装写入 default。
- app.schedule.enabled：源码 false，首次安装 true；页面中单条调度仍独立启停。
- server.port：源码 8080，原生首次安装 18080，Docker 内部 8080，宿主映射缺省 18080。
- 基础镜像和仓库高级 Compose 与初始化器生成的 Compose 是不同配置层。IJOB_HTTP_BIND 仅在高级模板读取，不能对首次安装包只设置这个变量就认为绑定已改变。
- Agent 还支持源码 YAML 未显式列出的 agent.hostname；完整清单包含了 Java 绑定类的这项属性。

## 3. 配置目录

共 **254 条**：平台属性 190、Agent 属性 8、启动/构建/锁文件入口 26、Compose 字段 25、实例入口字段 5。分级 A 43、B 75、C 31、D 105。计数包含部署入口与别名，不代表 254 个独立业务功能。

- [节点、分组与执行范围](#routing) — 9
- [全部插件开关与 Local JAR](#plugins) — 17
- [数据库、连接池与正式迁移](#database) — 21
- [账号、会话与密钥](#auth) — 16
- [调度与事件](#schedule) — 14
- [通知与邮件实现](#notification) — 9
- [历史数据保留](#retention) — 11
- [执行容量与周期扫描线程](#pools) — 23
- [内部协调、恢复与一致性](#coordination) — 28
- [访问、资源浏览、HTTP 与语言](#access) — 8
- [日志与管理探针](#logging) — 14
- [启动托管与框架契约](#framework) — 20
- [独立监控 Agent](#agent) — 8
- [启动、镜像锁与开发变量](#deployment) — 26
- [Compose 运维字段补充](#compose) — 25

<a id="routing"></a>

## 节点、分组与执行范围

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C035<br>`app.runtime.mode`<br>枚举 | `${IJOB_RUNTIME_MODE:STANDALONE}` (S01) | STANDALONE | `IJOB_RUNTIME_MODE` | **C** STANDALONE/CLUSTER 切换走运维流程；DEVELOPMENT 不用于正式部署。 | 平台；重启本安装 |
| C036<br>`app.runtime.instance-name`<br>文本 | `${IJOB_RUNTIME_INSTANCE_NAME:}` (S01) | setup-&lt;随机 UUID&gt; | `IJOB_RUNTIME_INSTANCE_NAME` | **A** 节点标识；集群各节点取不同名称，便于追踪运行。 | 平台；重启本安装 |
| C037<br>`app.runtime.roles`<br>枚举列表 | `${IJOB_RUNTIME_ROLES:API,SCHEDULER,WORKER,MAINTENANCE}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RUNTIME_ROLES` | **B** API/SCHEDULER/WORKER/MAINTENANCE 职责；部署前确保所需角色仍有人承担。 | 平台；重启本安装 |
| C038<br>`app.runtime.groups`<br>文本列表 | `${IJOB_RUNTIME_GROUPS:}` (S01) | default | `IJOB_RUNTIME_GROUPS` | **A** 节点执行分组；与发布定义的分组匹配，空节点分组并不表示能接所有分组。 | 平台；重启本安装 |
| C039<br>`app.runtime.allowed-task-ids`<br>ID 列表 | `${IJOB_RUNTIME_ALLOWED_TASK_IDS:}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RUNTIME_ALLOWED_TASK_IDS` | **A** 允许领取的根触发目标任务 ID；与作业白名单共同限制范围，见组合规则。 | 平台；重启本安装 |
| C040<br>`app.runtime.allowed-job-ids`<br>ID 列表 | `${IJOB_RUNTIME_ALLOWED_JOB_IDS:}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RUNTIME_ALLOWED_JOB_IDS` | **A** 允许领取的根触发目标作业 ID；不等价于对子作业内每个任务逐一授权。 | 平台；重启本安装 |
| C041<br>`app.runtime.singleton-lock-name`<br>文本 | `${IJOB_SINGLETON_LOCK_NAME:ijob:standalone}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SINGLETON_LOCK_NAME` | **D** 单实例互斥锁身份；改变它可能破坏同库单实例保护。 | 平台；重启本安装 |
| C042<br>`app.runtime.heartbeat-interval`<br>时长 | `${IJOB_RUNTIME_HEARTBEAT_INTERVAL:5s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RUNTIME_HEARTBEAT_INTERVAL` | **D** 节点心跳周期；与失效判定和协调时间共同约束。 | 平台；重启本安装 |
| C043<br>`app.runtime.id-segment-size`<br>整数 | `${IJOB_ID_SEGMENT_SIZE:1000}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_ID_SEGMENT_SIZE` | **D** 运行表 ID 分段大小；不改变正式数据库或协议版本。 | 平台；重启本安装 |

<a id="plugins"></a>

## 全部插件开关与 Local JAR

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C125<br>`app.plugin.event.enabled`<br>布尔 | `${IJOB_PLUGIN_EVENT_ENABLED:true}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_EVENT_ENABLED` | **A** event 插件节点级开关；关闭前检查已发布任务和运行中工作。 | 平台；重启本安装 |
| C126<br>`app.plugin.script.enabled`<br>布尔 | `${IJOB_PLUGIN_SCRIPT_ENABLED:true}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_SCRIPT_ENABLED` | **A** script 插件节点级开关；关闭前检查已发布任务和运行中工作。 | 平台；重启本安装 |
| C127<br>`app.plugin.sql.enabled`<br>布尔 | `${IJOB_PLUGIN_SQL_ENABLED:true}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_SQL_ENABLED` | **A** sql 插件节点级开关；关闭前检查已发布任务和运行中工作。 | 平台；重启本安装 |
| C128<br>`app.plugin.file-transfer.enabled`<br>布尔 | `${IJOB_PLUGIN_FILE_TRANSFER_ENABLED:true}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_FILE_TRANSFER_ENABLED` | **A** file-transfer 插件节点级开关；关闭前检查已发布任务和运行中工作。 | 平台；重启本安装 |
| C129<br>`app.plugin.api-call.enabled`<br>布尔 | `${IJOB_PLUGIN_API_CALL_ENABLED:true}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_API_CALL_ENABLED` | **A** api-call 插件节点级开关；关闭前检查已发布任务和运行中工作。 | 平台；重启本安装 |
| C130<br>`app.plugin.push.enabled`<br>布尔 | `${IJOB_PLUGIN_PUSH_ENABLED:true}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_PUSH_ENABLED` | **A** push 插件节点级开关；关闭前检查已发布任务和运行中工作。 | 平台；重启本安装 |
| C131<br>`app.plugin.data-check.enabled`<br>布尔 | `${IJOB_PLUGIN_DATA_CHECK_ENABLED:true}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_DATA_CHECK_ENABLED` | **A** data-check 插件节点级开关；关闭前检查已发布任务和运行中工作。 | 平台；重启本安装 |
| C132<br>`app.plugin.server-monitor.enabled`<br>布尔 | `${IJOB_PLUGIN_SERVER_MONITOR_ENABLED:true}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_SERVER_MONITOR_ENABLED` | **A** server-monitor 插件节点级开关；关闭前检查已发布任务和运行中工作。 | 平台；重启本安装 |
| C133<br>`app.plugin.etl.enabled`<br>布尔 | `${IJOB_PLUGIN_ETL_ENABLED:true}` (S01) | true（初始化器显式写入） | `IJOB_PLUGIN_ETL_ENABLED` | **A** etl 插件节点级开关；关闭前检查已发布任务和运行中工作。 | 平台；重启本安装 |
| C134<br>`app.plugin.batch-etl.enabled`<br>布尔 | `${IJOB_PLUGIN_BATCH_ETL_ENABLED:true}` (S01) | true（初始化器显式写入） | `IJOB_PLUGIN_BATCH_ETL_ENABLED` | **A** batch-etl 插件节点级开关；关闭前检查已发布任务和运行中工作。 | 平台；重启本安装 |
| C135<br>`app.plugin.local-jar.enabled`<br>布尔 | `${IJOB_PLUGIN_LOCAL_JAR_ENABLED:false}` (S01) | false（初始化器显式写入） | `IJOB_PLUGIN_LOCAL_JAR_ENABLED` | **A** local-jar 插件节点级开关；关闭前检查已发布任务和运行中工作。 另需可信 JAR、依赖及目录。 | 平台；重启本安装 |
| C136<br>`app.plugin.local-jar.root-directory`<br>路径 | `${IJOB_PLUGIN_LOCAL_JAR_ROOT:}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_LOCAL_JAR_ROOT` | **A** 可信 Local JAR 所在绝对目录；容器需单独挂载。 | 平台；重启本安装 |
| C137<br>`app.plugin.local-jar.max-jar-bytes`<br>字节 | `${IJOB_PLUGIN_LOCAL_JAR_MAX_BYTES:67108864}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_LOCAL_JAR_MAX_BYTES` | **B** JAR 文件字节上限；评估内存与归档安全限制后调整。 | 平台；重启本安装 |
| C138<br>`app.plugin.local-jar.max-jar-entries`<br>整数 | `${IJOB_PLUGIN_LOCAL_JAR_MAX_ENTRIES:10000}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_LOCAL_JAR_MAX_ENTRIES` | **B** JAR 条目数上限；评估内存与归档安全限制后调整。 | 平台；重启本安装 |
| C139<br>`app.plugin.local-jar.max-expanded-bytes`<br>字节 | `${IJOB_PLUGIN_LOCAL_JAR_MAX_EXPANDED_BYTES:268435456}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_LOCAL_JAR_MAX_EXPANDED_BYTES` | **B** 解压内容字节上限；评估内存与归档安全限制后调整。 | 平台；重启本安装 |
| C140<br>`app.plugin.local-jar.max-compression-ratio`<br>整数 | `${IJOB_PLUGIN_LOCAL_JAR_MAX_COMPRESSION_RATIO:100}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_LOCAL_JAR_MAX_COMPRESSION_RATIO` | **B** 最大压缩比；评估内存与归档安全限制后调整。 | 平台；重启本安装 |
| C141<br>`app.plugin.local-jar.max-cached-loaders`<br>整数 | `${IJOB_PLUGIN_LOCAL_JAR_MAX_CACHED_LOADERS:32}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLUGIN_LOCAL_JAR_MAX_CACHED_LOADERS` | **B** 缓存类加载器数量；评估内存与归档安全限制后调整。 | 平台；重启本安装 |

<a id="database"></a>

## 数据库、连接池与正式迁移

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C012<br>`spring.flyway.enabled`<br>布尔 | `${IJOB_FLYWAY_ENABLED:false}` (S01) | true | `IJOB_FLYWAY_ENABLED` | **D** 正式运行要求 Flyway 校验；不能关闭来绕过不兼容库。 | 平台；重启本安装 |
| C013<br>`spring.flyway.locations`<br>文本 | `classpath:db/release/${IJOB_PLATFORM_DB:mysql}` (S01) | classpath:db/release/&lt;vendor&gt; | `IJOB_PLATFORM_DB` | **D** 正式 V1 迁移目录按数据库选择。 | 平台；重启本安装 |
| C014<br>`spring.flyway.table`<br>文本 | `ifj_schema_history` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 正式迁移历史表身份。 | 平台；重启本安装 |
| C015<br>`spring.flyway.baseline-on-migrate`<br>布尔 | `false` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 禁止隐式把未知旧库认作基线。 | 平台；重启本安装 |
| C016<br>`spring.flyway.validate-on-migrate`<br>布尔 | `true` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 保持迁移校验，防止历史内容漂移。 | 平台；重启本安装 |
| C017<br>`spring.flyway.clean-disabled`<br>布尔 | `true` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 禁止清库，保持保护开启。 | 平台；重启本安装 |
| C025<br>`app.platform-database.vendor`<br>枚举 | `${IJOB_PLATFORM_DB:mysql}` (S01) | 按初始化选择 | `IJOB_PLATFORM_DB` | **C** 数据库产品标识；与驱动、迁移目录、安装元数据及启动环境一致。 | 平台；重启本安装 |
| C164<br>`spring.datasource.hikari.connection-init-sql`<br>文本 | `SET time_zone = '+00:00'` (S03)<br>`ALTER SESSION SET TIME_ZONE = 'UTC'` (S04)<br>`SET TIME ZONE 'UTC'` (S05)<br>`PRAGMA foreign_keys = ON` (S06)<br>`SET DATEFORMAT ymd` (S07) | 未另写入；采用所加载配置/实现默认值 | — | **D** 数据库会话初始化，涉及 UTC、日期格式或外键约束。 | 平台；重启本安装 |
| C165<br>`pagehelper.helper-dialect`<br>文本 | `mysql` (S03)<br>`oracle` (S04)<br>`postgresql` (S05)<br>`sqlite` (S06)<br>`sqlserver2012` (S07) | 未另写入；采用所加载配置/实现默认值 | — | **D** 数据库分页方言，与数据库产品一致。 | 平台；重启本安装 |
| C166<br>`spring.datasource.driver-class-name`<br>驱动类 | `oracle.jdbc.OracleDriver` (S04)<br>`org.sqlite.JDBC` (S06)<br>`com.microsoft.sqlserver.jdbc.SQLServerDriver` (S07) | 由初始化器按五种数据库产品选择 | — | **D** 初始化器按数据库产品选择驱动，不独立修改。 | 平台；重启本安装 |
| C167<br>`spring.flyway.default-schema`<br>文本 | `${IJOB_PLATFORM_DB_SCHEMA:${IJOB_DB_USERNAME:ijob}}` (S04)<br>`${IJOB_PLATFORM_DB_SCHEMA:public}` (S05)<br>`${IJOB_PLATFORM_DB_SCHEMA:dbo}` (S07) | 未另写入；采用所加载配置/实现默认值 | `IJOB_DB_USERNAME`<br>`IJOB_PLATFORM_DB_SCHEMA` | **C** 迁移 schema；已有安装变更需制定迁移方案。 | 平台；重启本安装 |
| C168<br>`app.platform-database.schema`<br>文本 | `${IJOB_PLATFORM_DB_SCHEMA:${IJOB_DB_USERNAME:ijob}}` (S04)<br>`${IJOB_PLATFORM_DB_SCHEMA:public}` (S05)<br>`${IJOB_PLATFORM_DB_SCHEMA:dbo}` (S07) | 未另写入；采用所加载配置/实现默认值 | `IJOB_DB_USERNAME`<br>`IJOB_PLATFORM_DB_SCHEMA` | **C** 平台 schema；按数据库产品配置，与 JDBC 和 Flyway 保持一致。 | 平台；重启本安装 |
| C170<br>`spring.datasource.hikari.schema`<br>文本 | `${IJOB_PLATFORM_DB_SCHEMA:public}` (S05)<br>`${IJOB_PLATFORM_DB_SCHEMA:dbo}` (S07) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLATFORM_DB_SCHEMA` | **C** 默认 schema；与平台 schema、迁移 schema 和账号权限共同调整。 | 平台；重启本安装 |
| C171<br>`spring.datasource.hikari.maximum-pool-size`<br>整数 | `${IJOB_SQLITE_POOL_SIZE:4}` (S06) | 4（所有初始化安装） | `IJOB_SQLITE_POOL_SIZE` | **B** 连接池上限；结合数据库连接预算，SQLite 不靠增大连接池解决写竞争。 | 平台；重启本安装 |
| C172<br>`spring.datasource.hikari.data-source-properties.busy_timeout`<br>毫秒 | `${IJOB_SQLITE_BUSY_TIMEOUT_MS:5000}` (S06) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SQLITE_BUSY_TIMEOUT_MS` | **B** 锁繁忙等待毫秒数；仅 SQLite，评估并发和持久性影响。 | 仅 SQLite；重启本安装 |
| C173<br>`spring.datasource.hikari.data-source-properties.journal_mode`<br>枚举 | `WAL` (S06) | 未另写入；采用所加载配置/实现默认值 | — | **D** WAL 日志模式；仅 SQLite，评估并发和持久性影响。 | 仅 SQLite；重启本安装 |
| C174<br>`spring.datasource.hikari.data-source-properties.synchronous`<br>枚举 | `NORMAL` (S06) | 未另写入；采用所加载配置/实现默认值 | — | **D** 同步持久性策略；仅 SQLite，评估并发和持久性影响。 | 仅 SQLite；重启本安装 |
| C175<br>`spring.datasource.hikari.data-source-properties.transaction_mode`<br>枚举 | `IMMEDIATE` (S06) | 未另写入；采用所加载配置/实现默认值 | — | **D** IMMEDIATE 事务模式；仅 SQLite，评估并发和持久性影响。 | 仅 SQLite；重启本安装 |
| C183<br>`spring.datasource.url`<br>文本 | `generated from database selection` (S10) | 按所选数据库和输入生成；SQLite 为本安装 runtime/platform.db | — | **C** 平台数据库连接；换数据库产品属于迁移，不能只改 URL。 | 平台；重启本安装 |
| C184<br>`spring.datasource.username`<br>文本 | `user input; empty for SQLite` (S10) | 输入值；SQLite 为空 | — | **C** 数据库连接账号；与权限和数据库口令一起维护。 | 平台；重启本安装 |
| C185<br>`spring.datasource.password`<br>秘密 | `private input; empty for SQLite` (S10) | 私有输入；SQLite 为空 | — | **C** 数据库口令；在受限文件中轮换并验证连接。 | 平台；重启本安装 |

<a id="auth"></a>

## 账号、会话与密钥

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C071<br>`app.auth.mode`<br>枚举 | `${IJOB_AUTH_MODE:STATIC}` (S01) | STATIC | `IJOB_AUTH_MODE` | **C** STATIC/DATABASE 账号来源；NONE 不用于正式安装。 | 平台；重启本安装 |
| C072<br>`app.auth.username`<br>文本 | `${IJOB_AUTH_USERNAME:}` (S01) | 交互输入；无通用默认账号 | `IJOB_AUTH_USERNAME` | **C** STATIC 或首次 DATABASE 引导账号；数据库模式日常账号通过用户页面管理。 | 平台；重启本安装 |
| C073<br>`app.auth.password-hash`<br>秘密 | `${IJOB_AUTH_PASSWORD_HASH:}` (S01) | 输入密码生成 BCrypt；无通用默认密码 | `IJOB_AUTH_PASSWORD_HASH` | **C** BCrypt 密码哈希，不是明文密码；按改密流程生成。 | 平台；重启本安装 |
| C074<br>`app.auth.bootstrap-enabled`<br>布尔 | `${IJOB_AUTH_BOOTSTRAP_ENABLED:false}` (S01) | false | `IJOB_AUTH_BOOTSTRAP_ENABLED` | **C** 仅在数据库账号表为空时受控引导首个管理员，完成后关闭。 | 平台；重启本安装 |
| C075<br>`app.security.aes.key`<br>秘密 | `${IJOB_CONFIG_AES_KEY:}` (S01) | 每个安装独立随机生成 | `IJOB_CONFIG_AES_KEY` | **C** 加密已有资源凭据；备份必须保留原值，不能直接换随机值。 | 平台；重启本安装 |
| C076<br>`app.security.aes.key-id`<br>文本 | `${IJOB_CONFIG_AES_KEY_ID:local}` (S01) | setup-key-1 | `IJOB_CONFIG_AES_KEY_ID` | **C** 与 AES 密钥对应的标识；随完整轮换方案维护。 | 平台；重启本安装 |
| C142<br>`sa-token.token-name`<br>文本 | `${IJOB_AUTH_TOKEN_NAME:ijob-token}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_AUTH_TOKEN_NAME` | **C** 认证请求头名称；须与客户端、代理及集群节点保持一致。 | 平台；重启本安装 |
| C143<br>`sa-token.timeout`<br>秒 | `${IJOB_AUTH_TIMEOUT_SECONDS:1800}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_AUTH_TIMEOUT_SECONDS` | **B** 登录令牌总有效期，单位秒；按会话策略调整。 | 平台；重启本安装 |
| C144<br>`sa-token.active-timeout`<br>秒 | `${IJOB_AUTH_ACTIVE_TIMEOUT_SECONDS:300}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_AUTH_ACTIVE_TIMEOUT_SECONDS` | **B** 空闲会话超时，单位秒；区别于总有效期。 | 平台；重启本安装 |
| C145<br>`sa-token.is-concurrent`<br>布尔 | `false` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 同账号并发登录策略；属于已验证的认证契约，不作为普通调参。 | 平台；重启本安装 |
| C146<br>`sa-token.is-share`<br>布尔 | `false` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 会话共享策略；属于已验证的认证契约，不作为普通调参。 | 平台；重启本安装 |
| C147<br>`sa-token.is-read-cookie`<br>布尔 | `false` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** Cookie 读取策略；属于已验证的认证契约，不作为普通调参。 | 平台；重启本安装 |
| C148<br>`sa-token.is-read-header`<br>布尔 | `true` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 请求头令牌读取；属于已验证的认证契约，不作为普通调参。 | 平台；重启本安装 |
| C149<br>`sa-token.is-write-header`<br>布尔 | `false` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 响应头令牌写入；属于已验证的认证契约，不作为普通调参。 | 平台；重启本安装 |
| C150<br>`sa-token.token-style`<br>文本 | `uuid` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 令牌生成格式；属于已验证的认证契约，不作为普通调参。 | 平台；重启本安装 |
| C151<br>`sa-token.is-log`<br>布尔 | `false` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 认证组件日志开关；属于已验证的认证契约，不作为普通调参。 | 平台；重启本安装 |

<a id="schedule"></a>

## 调度与事件

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C077<br>`app.schedule.enabled`<br>布尔 | `${IJOB_SCHEDULE_ENABLED:false}` (S01) | true | `IJOB_SCHEDULE_ENABLED` | **A** 全局调度扫描开关；不替代单条调度启用状态和节点角色。 | 平台；重启本安装 |
| C078<br>`app.schedule.scan-interval`<br>时长 | `${IJOB_SCHEDULE_SCAN_INTERVAL:1s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCHEDULE_SCAN_INTERVAL` | **B** 调度扫描：扫描周期。结合负载、恢复或清理策略验证后调整。 | 平台；重启本安装 |
| C079<br>`app.schedule.initial-delay`<br>时长 | `${IJOB_SCHEDULE_INITIAL_DELAY:5s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCHEDULE_INITIAL_DELAY` | **B** 调度扫描：首次运行延迟。结合负载、恢复或清理策略验证后调整。 | 平台；重启本安装 |
| C080<br>`app.schedule.scan-batch-size`<br>整数 | `${IJOB_SCHEDULE_SCAN_BATCH_SIZE:100}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCHEDULE_SCAN_BATCH_SIZE` | **B** 调度扫描：每轮扫描批量。结合负载、恢复或清理策略验证后调整。 | 平台；重启本安装 |
| C081<br>`app.schedule.max-triggers-per-scan`<br>整数 | `${IJOB_SCHEDULE_MAX_TRIGGERS_PER_SCAN:100}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCHEDULE_MAX_TRIGGERS_PER_SCAN` | **B** 每轮产生触发的上限；影响吞吐及恢复速度。 | 平台；重启本安装 |
| C082<br>`app.schedule.zone-id`<br>时区 | `${IJOB_SCHEDULE_ZONE_ID:Asia/Shanghai}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCHEDULE_ZONE_ID` | **B** 调度默认业务时区；与用户显示时区不同，核验单条调度设置。 | 平台；重启本安装 |
| C083<br>`app.schedule.misfire-policy`<br>枚举 | `${IJOB_SCHEDULE_MISFIRE_POLICY:FIRE_ONCE}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCHEDULE_MISFIRE_POLICY` | **B** 错过触发默认策略 SKIP/FIRE_ONCE/CATCH_UP_BOUNDED；可能产生实际补发。 | 平台；重启本安装 |
| C084<br>`app.schedule.misfire-grace`<br>时长 | `${IJOB_SCHEDULE_MISFIRE_GRACE:60s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCHEDULE_MISFIRE_GRACE` | **B** 判断错过触发的宽限时间；联动补发策略验证。 | 平台；重启本安装 |
| C085<br>`app.schedule.max-catchup-window`<br>时长 | `${IJOB_SCHEDULE_MAX_CATCHUP_WINDOW:24h}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCHEDULE_MAX_CATCHUP_WINDOW` | **B** 补发回溯时间上限；避免恢复后集中补发过多历史运行。 | 平台；重启本安装 |
| C086<br>`app.schedule.max-catchup-count`<br>整数 | `${IJOB_SCHEDULE_MAX_CATCHUP_COUNT:10}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCHEDULE_MAX_CATCHUP_COUNT` | **B** 补发次数上限；与时间窗口共同约束。 | 平台；重启本安装 |
| C087<br>`app.event.ingress.enabled`<br>布尔 | `${IJOB_EVENT_INGRESS_ENABLED:false}` (S01) | false（初始化器显式写入） | `IJOB_EVENT_INGRESS_ENABLED` | **A** 事件接入 API 开关；仍要求登录与 EVENT_PUBLISH 权限。 | 平台；重启本安装 |
| C088<br>`app.event.recovery.enabled`<br>布尔 | `${IJOB_EVENT_RECOVERY_ENABLED:true}` (S01) | true（初始化器显式写入） | `IJOB_EVENT_RECOVERY_ENABLED` | **D** 持久事件恢复机制；关闭会改变等待事件的恢复能力。 | 平台；重启本安装 |
| C089<br>`app.event.recovery.scan-interval`<br>时长 | `${IJOB_EVENT_RECOVERY_SCAN_INTERVAL:1s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_EVENT_RECOVERY_SCAN_INTERVAL` | **D** 事件恢复：扫描周期。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C090<br>`app.event.recovery.scan-batch-size`<br>整数 | `${IJOB_EVENT_RECOVERY_SCAN_BATCH_SIZE:100}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_EVENT_RECOVERY_SCAN_BATCH_SIZE` | **D** 事件恢复：每轮扫描批量。涉及协调一致性，保留默认。 | 平台；重启本安装 |

<a id="notification"></a>

## 通知与邮件实现

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C026<br>`app.notification.locale`<br>枚举 | `${IJOB_NOTIFICATION_LOCALE:en}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_NOTIFICATION_LOCALE` | **A** 通知默认语言 en/zh；与界面语言分开管理。 | 平台；重启本安装 |
| C027<br>`app.notification.outbox.enabled`<br>布尔 | `${IJOB_NOTIFICATION_OUTBOX_ENABLED:true}` (S01) | true（初始化器显式写入） | `IJOB_NOTIFICATION_OUTBOX_ENABLED` | **D** 持久通知投递机制；CLUSTER 要求开启，不能用作业务通知停用按钮。 | 平台；重启本安装 |
| C028<br>`app.notification.outbox.scan-interval`<br>时长 | `${IJOB_NOTIFICATION_OUTBOX_SCAN_INTERVAL:1s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_NOTIFICATION_OUTBOX_SCAN_INTERVAL` | **B** 通知投递：扫描周期。结合负载、恢复或清理策略验证后调整。 | 平台；重启本安装 |
| C029<br>`app.notification.outbox.renew-interval`<br>时长 | `${IJOB_NOTIFICATION_OUTBOX_RENEW_INTERVAL:10s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_NOTIFICATION_OUTBOX_RENEW_INTERVAL` | **D** 通知投递：租约续期间隔。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C030<br>`app.notification.outbox.lease-duration`<br>时长 | `${IJOB_NOTIFICATION_OUTBOX_LEASE_DURATION:30s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_NOTIFICATION_OUTBOX_LEASE_DURATION` | **D** 通知投递：租约时长。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C031<br>`app.notification.outbox.retry-delay`<br>时长 | `${IJOB_NOTIFICATION_OUTBOX_RETRY_DELAY:30s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_NOTIFICATION_OUTBOX_RETRY_DELAY` | **B** 通知投递：重试等待间隔。结合负载、恢复或清理策略验证后调整。 | 平台；重启本安装 |
| C032<br>`app.notification.outbox.scan-batch-size`<br>整数 | `${IJOB_NOTIFICATION_OUTBOX_SCAN_BATCH_SIZE:100}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_NOTIFICATION_OUTBOX_SCAN_BATCH_SIZE` | **B** 通知投递：每轮扫描批量。结合负载、恢复或清理策略验证后调整。 | 平台；重启本安装 |
| C033<br>`app.notification.outbox.max-attempts`<br>整数 | `${IJOB_NOTIFICATION_OUTBOX_MAX_ATTEMPTS:5}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_NOTIFICATION_OUTBOX_MAX_ATTEMPTS` | **B** 通知投递：内部处理尝试次数上限。结合负载、恢复或清理策略验证后调整。 | 平台；重启本安装 |
| C163<br>`app.notify.email.enabled`<br>布尔 | `true` (S02) | 未另写入；采用所加载配置/实现默认值 | — | **B** 邮件通道实现开关，缺省也启用；SMTP 连接与凭据在邮件通道页面维护。 | 平台；重启本安装 |

<a id="retention"></a>

## 历史数据保留

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C091<br>`app.retention.enabled`<br>布尔 | `${IJOB_RETENTION_ENABLED:false}` (S01) | false（初始化器显式写入） | `IJOB_RETENTION_ENABLED` | **A** 业务历史清理总开关；开启前核对保留时间和备份。 | 平台；重启本安装 |
| C092<br>`app.retention.dry-run`<br>布尔 | `${IJOB_RETENTION_DRY_RUN:true}` (S01) | true（初始化器显式写入） | `IJOB_RETENTION_DRY_RUN` | **A** 清理试运行；关闭后才允许执行实际删除。 | 平台；重启本安装 |
| C093<br>`app.retention.initial-delay`<br>时长 | `${IJOB_RETENTION_INITIAL_DELAY:1m}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RETENTION_INITIAL_DELAY` | **B** 历史清理：首次运行延迟。结合负载、恢复或清理策略验证后调整。 | 平台；重启本安装 |
| C094<br>`app.retention.interval`<br>时长 | `${IJOB_RETENTION_INTERVAL:1h}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RETENTION_INTERVAL` | **B** 历史清理：运行间隔。结合负载、恢复或清理策略验证后调整。 | 平台；重启本安装 |
| C095<br>`app.retention.batch-size`<br>整数 | `${IJOB_RETENTION_BATCH_SIZE:500}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RETENTION_BATCH_SIZE` | **B** 历史清理：批次大小。结合负载、恢复或清理策略验证后调整。 | 平台；重启本安装 |
| C096<br>`app.retention.max-batches-per-run`<br>整数 | `${IJOB_RETENTION_MAX_BATCHES_PER_RUN:10}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RETENTION_MAX_BATCHES_PER_RUN` | **B** 历史清理：单轮最大批次数。结合负载、恢复或清理策略验证后调整。 | 平台；重启本安装 |
| C097<br>`app.retention.execution`<br>时长 | `${IJOB_RETENTION_EXECUTION:90d}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RETENTION_EXECUTION` | **A** 执行历史保留时长；与清理总开关和试运行共同生效。 | 平台；重启本安装 |
| C098<br>`app.retention.notification`<br>时长 | `${IJOB_RETENTION_NOTIFICATION:90d}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RETENTION_NOTIFICATION` | **A** 通知历史保留时长；与清理总开关和试运行共同生效。 | 平台；重启本安装 |
| C099<br>`app.retention.event`<br>时长 | `${IJOB_RETENTION_EVENT:180d}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RETENTION_EVENT` | **A** 事件历史保留时长；与清理总开关和试运行共同生效。 | 平台；重启本安装 |
| C100<br>`app.retention.app-run`<br>时长 | `${IJOB_RETENTION_APP_RUN:30d}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RETENTION_APP_RUN` | **A** 节点运行历史保留时长；与清理总开关和试运行共同生效。 | 平台；重启本安装 |
| C101<br>`app.retention.audit`<br>时长 | `${IJOB_RETENTION_AUDIT:90d}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RETENTION_AUDIT` | **A** 审计历史保留时长；与清理总开关和试运行共同生效。 | 平台；重启本安装 |

<a id="pools"></a>

## 执行容量与周期扫描线程

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C023<br>`app.scheduling.pool-size`<br>整数 | `${IJOB_SCHEDULING_POOL_SIZE:8}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCHEDULING_POOL_SIZE` | **B** 内部周期扫描线程数量；不是业务任务最大并发数。 | 平台；重启本安装 |
| C024<br>`app.scheduling.shutdown-timeout`<br>时长 | `${IJOB_SCHEDULING_SHUTDOWN_TIMEOUT:30s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCHEDULING_SHUTDOWN_TIMEOUT` | **B** 周期扫描执行器关闭等待。 | 平台；重启本安装 |
| C104<br>`app.execution.shutdown-timeout`<br>时长 | `${IJOB_EXECUTION_SHUTDOWN_TIMEOUT:30s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_EXECUTION_SHUTDOWN_TIMEOUT` | **B** 执行器关闭等待；与容器停止宽限和 Spring 关闭时限一起规划。 | 平台；重启本安装 |
| C105<br>`app.execution.trigger.core-size`<br>整数 | `${IJOB_TRIGGER_POOL_CORE_SIZE:4}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_TRIGGER_POOL_CORE_SIZE` | **B** trigger 执行池核心线程数；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C106<br>`app.execution.trigger.max-size`<br>整数 | `${IJOB_TRIGGER_POOL_MAX_SIZE:20}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_TRIGGER_POOL_MAX_SIZE` | **B** trigger 执行池最大线程数；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C107<br>`app.execution.trigger.queue-capacity`<br>整数 | `${IJOB_TRIGGER_POOL_QUEUE_CAPACITY:0}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_TRIGGER_POOL_QUEUE_CAPACITY` | **B** trigger 执行池队列容量；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C108<br>`app.execution.trigger.keep-alive`<br>时长 | `${IJOB_TRIGGER_POOL_KEEP_ALIVE:60s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_TRIGGER_POOL_KEEP_ALIVE` | **B** trigger 执行池非核心空闲线程保留时间；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C109<br>`app.execution.job.core-size`<br>整数 | `${IJOB_JOB_POOL_CORE_SIZE:4}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_JOB_POOL_CORE_SIZE` | **B** job 执行池核心线程数；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C110<br>`app.execution.job.max-size`<br>整数 | `${IJOB_JOB_POOL_MAX_SIZE:20}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_JOB_POOL_MAX_SIZE` | **B** job 执行池最大线程数；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C111<br>`app.execution.job.queue-capacity`<br>整数 | `${IJOB_JOB_POOL_QUEUE_CAPACITY:0}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_JOB_POOL_QUEUE_CAPACITY` | **B** job 执行池队列容量；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C112<br>`app.execution.job.keep-alive`<br>时长 | `${IJOB_JOB_POOL_KEEP_ALIVE:60s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_JOB_POOL_KEEP_ALIVE` | **B** job 执行池非核心空闲线程保留时间；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C113<br>`app.execution.dag.core-size`<br>整数 | `${IJOB_DAG_POOL_CORE_SIZE:8}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_DAG_POOL_CORE_SIZE` | **B** dag 执行池核心线程数；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C114<br>`app.execution.dag.max-size`<br>整数 | `${IJOB_DAG_POOL_MAX_SIZE:32}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_DAG_POOL_MAX_SIZE` | **B** dag 执行池最大线程数；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C115<br>`app.execution.dag.queue-capacity`<br>整数 | `${IJOB_DAG_POOL_QUEUE_CAPACITY:500}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_DAG_POOL_QUEUE_CAPACITY` | **B** dag 执行池队列容量；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C116<br>`app.execution.dag.keep-alive`<br>时长 | `${IJOB_DAG_POOL_KEEP_ALIVE:60s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_DAG_POOL_KEEP_ALIVE` | **B** dag 执行池非核心空闲线程保留时间；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C117<br>`app.execution.task.core-size`<br>整数 | `${IJOB_TASK_POOL_CORE_SIZE:8}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_TASK_POOL_CORE_SIZE` | **B** task 执行池核心线程数；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C118<br>`app.execution.task.max-size`<br>整数 | `${IJOB_TASK_POOL_MAX_SIZE:32}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_TASK_POOL_MAX_SIZE` | **B** task 执行池最大线程数；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C119<br>`app.execution.task.queue-capacity`<br>整数 | `${IJOB_TASK_POOL_QUEUE_CAPACITY:1000}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_TASK_POOL_QUEUE_CAPACITY` | **B** task 执行池队列容量；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C120<br>`app.execution.task.keep-alive`<br>时长 | `${IJOB_TASK_POOL_KEEP_ALIVE:60s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_TASK_POOL_KEEP_ALIVE` | **B** task 执行池非核心空闲线程保留时间；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C121<br>`app.execution.script.core-size`<br>整数 | `${IJOB_SCRIPT_POOL_CORE_SIZE:4}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCRIPT_POOL_CORE_SIZE` | **B** script 执行池核心线程数；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C122<br>`app.execution.script.max-size`<br>整数 | `${IJOB_SCRIPT_POOL_MAX_SIZE:16}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCRIPT_POOL_MAX_SIZE` | **B** script 执行池最大线程数；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C123<br>`app.execution.script.queue-capacity`<br>整数 | `${IJOB_SCRIPT_POOL_QUEUE_CAPACITY:0}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCRIPT_POOL_QUEUE_CAPACITY` | **B** script 执行池队列容量；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |
| C124<br>`app.execution.script.keep-alive`<br>时长 | `${IJOB_SCRIPT_POOL_KEEP_ALIVE:60s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_SCRIPT_POOL_KEEP_ALIVE` | **B** script 执行池非核心空闲线程保留时间；需联合评估，不等于业务并发组限制。 | 平台；重启本安装 |

<a id="coordination"></a>

## 内部协调、恢复与一致性

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C034<br>`app.cache-consistency.max-staleness`<br>时长 | `${IJOB_CACHE_MAX_STALENESS:5s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_CACHE_MAX_STALENESS` | **D** 跨节点缓存最大陈旧窗口；属于一致性协议的运行参数。 | 平台；重启本安装 |
| C044<br>`app.runtime.work-queue.scan-interval`<br>时长 | `${IJOB_WORK_QUEUE_SCAN_INTERVAL:1s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_WORK_QUEUE_SCAN_INTERVAL` | **D** 持久工作队列：扫描周期。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C045<br>`app.runtime.work-queue.max-idle-scan-interval`<br>时长 | `${IJOB_WORK_QUEUE_MAX_IDLE_SCAN_INTERVAL:5s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_WORK_QUEUE_MAX_IDLE_SCAN_INTERVAL` | **D** 持久工作队列：空闲扫描间隔上限。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C046<br>`app.runtime.work-queue.scan-jitter`<br>时长 | `${IJOB_WORK_QUEUE_SCAN_JITTER:200ms}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_WORK_QUEUE_SCAN_JITTER` | **D** 持久工作队列：扫描抖动范围。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C047<br>`app.runtime.work-queue.renew-interval`<br>时长 | `${IJOB_WORK_QUEUE_RENEW_INTERVAL:5s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_WORK_QUEUE_RENEW_INTERVAL` | **D** 持久工作队列：租约续期间隔。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C048<br>`app.runtime.work-queue.scan-batch-size`<br>整数 | `${IJOB_WORK_QUEUE_SCAN_BATCH_SIZE:100}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_WORK_QUEUE_SCAN_BATCH_SIZE` | **D** 持久工作队列：每轮扫描批量。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C049<br>`app.runtime.work-queue.max-inflight-roots`<br>整数 | `${IJOB_WORK_QUEUE_MAX_INFLIGHT_ROOTS:20}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_WORK_QUEUE_MAX_INFLIGHT_ROOTS` | **B** 节点在途根运行上限；结合线程池、任务并发和资源容量评估。 | 平台；重启本安装 |
| C050<br>`app.runtime.work-queue.lease-duration`<br>时长 | `${IJOB_WORK_QUEUE_LEASE_DURATION:30s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_WORK_QUEUE_LEASE_DURATION` | **D** 持久工作队列：租约时长。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C051<br>`app.runtime.work-queue.retry-delay`<br>时长 | `${IJOB_WORK_QUEUE_RETRY_DELAY:5s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_WORK_QUEUE_RETRY_DELAY` | **D** 持久工作队列：重试等待间隔。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C052<br>`app.runtime.work-queue.max-attempts`<br>整数 | `${IJOB_WORK_QUEUE_MAX_ATTEMPTS:5}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_WORK_QUEUE_MAX_ATTEMPTS` | **D** 持久工作队列：内部处理尝试次数上限。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C053<br>`app.runtime.command.scan-interval`<br>时长 | `${IJOB_RUNTIME_COMMAND_SCAN_INTERVAL:250ms}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RUNTIME_COMMAND_SCAN_INTERVAL` | **D** 运行命令协调：扫描周期。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C054<br>`app.runtime.command.scan-batch-size`<br>整数 | `${IJOB_RUNTIME_COMMAND_SCAN_BATCH_SIZE:100}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RUNTIME_COMMAND_SCAN_BATCH_SIZE` | **D** 运行命令协调：每轮扫描批量。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C055<br>`app.runtime.command.retry-delay`<br>时长 | `${IJOB_RUNTIME_COMMAND_RETRY_DELAY:250ms}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RUNTIME_COMMAND_RETRY_DELAY` | **D** 运行命令协调：重试等待间隔。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C056<br>`app.runtime.command.timeout`<br>时长 | `${IJOB_RUNTIME_COMMAND_TIMEOUT:5m}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_RUNTIME_COMMAND_TIMEOUT` | **D** 运行命令协调：内部命令超时。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C057<br>`app.runtime.concurrency-permit.wait-timeout`<br>时长 | `${IJOB_CONCURRENCY_PERMIT_WAIT_TIMEOUT:5m}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_CONCURRENCY_PERMIT_WAIT_TIMEOUT` | **D** 并发许可协调：等待并发许可的时限。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C058<br>`app.runtime.concurrency-permit.retry-interval`<br>时长 | `${IJOB_CONCURRENCY_PERMIT_RETRY_INTERVAL:100ms}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_CONCURRENCY_PERMIT_RETRY_INTERVAL` | **D** 并发许可协调：重试间隔。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C059<br>`app.runtime.concurrency-permit.max-retry-interval`<br>时长 | `${IJOB_CONCURRENCY_PERMIT_MAX_RETRY_INTERVAL:2s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_CONCURRENCY_PERMIT_MAX_RETRY_INTERVAL` | **D** 并发许可协调：重试间隔上限。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C060<br>`app.runtime.concurrency-permit.retry-jitter`<br>时长 | `${IJOB_CONCURRENCY_PERMIT_RETRY_JITTER:100ms}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_CONCURRENCY_PERMIT_RETRY_JITTER` | **D** 并发许可协调：重试抖动范围。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C061<br>`app.runtime.concurrency-permit.renew-interval`<br>时长 | `${IJOB_CONCURRENCY_PERMIT_RENEW_INTERVAL:5s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_CONCURRENCY_PERMIT_RENEW_INTERVAL` | **D** 并发许可协调：租约续期间隔。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C062<br>`app.runtime.concurrency-permit.lease-duration`<br>时长 | `${IJOB_CONCURRENCY_PERMIT_LEASE_DURATION:30s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_CONCURRENCY_PERMIT_LEASE_DURATION` | **D** 并发许可协调：租约时长。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C063<br>`app.runtime.maintenance.enabled`<br>布尔 | `${IJOB_MAINTENANCE_ENABLED:true}` (S01) | true（初始化器显式写入） | `IJOB_MAINTENANCE_ENABLED` | **D** 运行维护：运行维护恢复开关。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C064<br>`app.runtime.maintenance.scan-interval`<br>时长 | `${IJOB_MAINTENANCE_SCAN_INTERVAL:5s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_MAINTENANCE_SCAN_INTERVAL` | **D** 运行维护：扫描周期。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C065<br>`app.runtime.maintenance.renew-interval`<br>时长 | `${IJOB_MAINTENANCE_RENEW_INTERVAL:10s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_MAINTENANCE_RENEW_INTERVAL` | **D** 运行维护：租约续期间隔。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C066<br>`app.runtime.maintenance.lease-duration`<br>时长 | `${IJOB_MAINTENANCE_LEASE_DURATION:30s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_MAINTENANCE_LEASE_DURATION` | **D** 运行维护：租约时长。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C067<br>`app.runtime.maintenance.stale-run-timeout`<br>时长 | `${IJOB_MAINTENANCE_STALE_RUN_TIMEOUT:30s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_MAINTENANCE_STALE_RUN_TIMEOUT` | **D** 运行维护：过期节点判定时限。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C068<br>`app.runtime.maintenance.orphan-grace`<br>时长 | `${IJOB_MAINTENANCE_ORPHAN_GRACE:1m}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_MAINTENANCE_ORPHAN_GRACE` | **D** 运行维护：孤立运行恢复宽限。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C069<br>`app.runtime.maintenance.batch-size`<br>整数 | `${IJOB_MAINTENANCE_BATCH_SIZE:100}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_MAINTENANCE_BATCH_SIZE` | **D** 运行维护：批次大小。涉及协调一致性，保留默认。 | 平台；重启本安装 |
| C070<br>`app.runtime.maintenance.max-batches-per-run`<br>整数 | `${IJOB_MAINTENANCE_MAX_BATCHES_PER_RUN:10}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_MAINTENANCE_MAX_BATCHES_PER_RUN` | **D** 运行维护：单轮最大批次数。涉及协调一致性，保留默认。 | 平台；重启本安装 |

<a id="access"></a>

## 访问、资源浏览、HTTP 与语言

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C008<br>`spring.web.locale`<br>语言 | `${IJOB_DEFAULT_LOCALE:en}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_DEFAULT_LOCALE` | **A** 服务端缺省语言；Accept-Language 和前端偏好另有作用。 | 平台；重启本安装 |
| C009<br>`spring.web.locale-resolver`<br>文本 | `accept-header` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 依据请求头解析服务端语言的既有规则。 | 平台；重启本安装 |
| C018<br>`server.port`<br>端口 | `${IJOB_SERVER_PORT:8080}` (S01) | 原生 18080；Docker 8080 | `IJOB_SERVER_PORT` | **A** 原生服务端口；Docker 后端内部保持 8080，另改宿主映射。 | 平台；重启本安装 |
| C019<br>`server.shutdown`<br>文本 | `graceful` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 优雅关闭模式；不要为加快退出绕过运行排空。 | 平台；重启本安装 |
| C022<br>`app.resource-browser.local-roots`<br>路径列表 | `[]` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **A** LOCAL 资源草稿允许浏览的绝对根目录；不等价于所有脚本的文件系统沙箱。 | 平台；重启本安装 |
| C102<br>`app.http.connect-timeout`<br>时长 | `${IJOB_HTTP_CONNECT_TIMEOUT:5s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_HTTP_CONNECT_TIMEOUT` | **A** 共用 HTTP 客户端连接超时；不覆盖所有插件自身的超时。 | 平台；重启本安装 |
| C103<br>`app.http.read-timeout`<br>时长 | `${IJOB_HTTP_READ_TIMEOUT:30s}` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_HTTP_READ_TIMEOUT` | **A** 共用 HTTP 客户端读取超时；与任务总超时区分。 | 平台；重启本安装 |
| C182<br>`server.address`<br>文本 | `generated: native=127.0.0.1; docker=0.0.0.0` (S10) | 原生 127.0.0.1；Docker 0.0.0.0 | — | **A** 原生监听地址；Docker 内部必须与健康检查和代理保持一致。 | 平台；重启本安装 |

<a id="logging"></a>

## 日志与管理探针

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C152<br>`logging.pattern.level`<br>文本 | `%5p [traceId=%X{traceId:-} trigger=%X{triggerId:-} root=%X{rootInstanceId:-} instance=%X{instanceId:-} command=%X{commandId:-} outbox=%X{outboxId:-} attempt=%X{attempt:-} taskAttempt=%X{taskAttempt:-} fence=%X{fence:-}]` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 运行关联 ID 的日志格式；排障依赖这些字段。 | 平台；重启本安装 |
| C153<br>`logging.level.root`<br>枚举 | `INFO` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **B** 全局日志级别；排障临时调整后恢复，评估日志量。 | 平台；重启本安装 |
| C154<br>`logging.level.com.ifielsc.ijob`<br>枚举 | `INFO` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **B** iJob 日志级别；不要输出含凭据的配置来排障。 | 平台；重启本安装 |
| C155<br>`management.metrics.tags.application`<br>文本 | `${spring.application.name}` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 指标应用标签，默认引用应用名称。 | 平台；重启本安装 |
| C156<br>`management.endpoints.web.exposure.include`<br>文本列表 | `health,info,metrics` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **C** 管理端点暴露列表；新增端点需检查访问控制，不能公开配置秘密。 | 平台；重启本安装 |
| C157<br>`management.endpoint.health.probes.enabled`<br>布尔 | `true` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 开启就绪与存活探针能力。 | 平台；重启本安装 |
| C158<br>`management.endpoint.health.group.readiness.include`<br>文本 | `readinessState,runtime,singletonLock,coordination,db` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 就绪聚合条件，包含运行、单实例锁、协调与数据库健康。 | 平台；重启本安装 |
| C159<br>`management.endpoint.health.show-details`<br>枚举 | `never` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **C** 健康细节展示；按访问权限设计，不直接对外开放。 | 平台；重启本安装 |
| C160<br>`management.health.livenessstate.enabled`<br>布尔 | `true` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 存活状态探针，保留部署探测契约。 | 平台；重启本安装 |
| C161<br>`management.health.readinessstate.enabled`<br>布尔 | `true` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 就绪状态探针，保留部署探测契约。 | 平台；重启本安装 |
| C190<br>`logging.config`<br>URI | `launcher: <bundle>/logging-native.xml` (S11) | 未另写入；采用所加载配置/实现默认值 | — | **D** 原生日志配置资源位置，由启动器设置。 | 原生安装；重启本安装 |
| C194<br>`ijob.logging.max-file-size`<br>容量 | `10MB` (S12) | 未另写入；采用所加载配置/实现默认值 | — | **A** 原生日志单文件滚动大小。 | 原生安装；重启本安装 |
| C195<br>`ijob.logging.max-history`<br>天 | `14` (S12) | 未另写入；采用所加载配置/实现默认值 | — | **A** 原生日志归档保留天数。 | 原生安装；重启本安装 |
| C196<br>`ijob.logging.total-size-cap`<br>容量 | `200MB` (S12) | 未另写入；采用所加载配置/实现默认值 | — | **A** 原生日志归档总容量，可能先于天数触发清理。 | 原生安装；重启本安装 |

<a id="framework"></a>

## 启动托管与框架契约

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C001<br>`spring.application.name`<br>文本 | `ijob` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 应用标识；影响日志/指标标识，不作为业务名称设置。 | 平台；重启本安装 |
| C002<br>`spring.messages.fallback-to-system-locale`<br>布尔 | `false` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 禁用跟随宿主语言的隐式回退，保持国际化一致。 | 平台；重启本安装 |
| C003<br>`spring.messages.encoding`<br>文本 | `UTF-8` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 消息资源 UTF-8 编码，保持默认。 | 平台；重启本安装 |
| C004<br>`spring.profiles.default`<br>文本 | `local` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 未指定活动 profile 时的回退；正式启动器显式选择 profile。 | 平台；重启本安装 |
| C005<br>`spring.config.import`<br>文本 | `optional:classpath:database/${IJOB_PLATFORM_DB:mysql}.yml` (S01) | 未另写入；采用所加载配置/实现默认值 | `IJOB_PLATFORM_DB` | **D** 选择数据库专用配置；由数据库产品和启动方式决定。 | 平台；重启本安装 |
| C006<br>`spring.jackson.default-property-inclusion`<br>文本 | `non_null` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** API 空值序列化契约。 | 平台；重启本安装 |
| C007<br>`spring.jackson.serialization.write-dates-as-timestamps`<br>布尔 | `false` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** API 日期序列化契约。 | 平台；重启本安装 |
| C010<br>`spring.lifecycle.timeout-per-shutdown-phase`<br>时长 | `30s` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **B** 每个 Spring 关闭阶段的等待时间，与执行器和容器时限共同验证。 | 平台；重启本安装 |
| C011<br>`spring.sql.init.mode`<br>文本 | `never` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 禁止另一套 SQL 初始化与正式迁移并行运行。 | 平台；重启本安装 |
| C020<br>`mybatis.mapper-locations`<br>文本 | `classpath*:mapper/**/*.xml` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 数据库映射文件定位。 | 平台；重启本安装 |
| C021<br>`mybatis.configuration.map-underscore-to-camel-case`<br>布尔 | `true` (S01) | 未另写入；采用所加载配置/实现默认值 | — | **D** 数据库列名到模型字段的映射契约。 | 平台；重启本安装 |
| C162<br>`spring.config.activate.on-profile`<br>文本 | `mail` (S02) | 未另写入；采用所加载配置/实现默认值 | — | **D** 邮件 profile 的激活条件，属于配置结构。 | 平台；重启本安装 |
| C169<br>`mybatis.configuration.jdbc-type-for-null`<br>文本 | `NULL` (S04) | 未另写入；采用所加载配置/实现默认值 | — | **D** Oracle 空值绑定规则。 | 平台；重启本安装 |
| C186<br>`spring.profiles.active`<br>文本列表 | `release` (S10) | 初始化 release；原生启动追加 native-background | — | **D** 启动器选择 release/native-background 或容器配置，非日常功能开关。 | 平台；重启本安装 |
| C187<br>`app.web.enabled`<br>布尔 | `native=true; docker uses separate web service` (S10) | 原生 true；Docker 不生成 | — | **D** 原生静态前端托管开关；由安装方式决定，Docker 使用独立 Web 容器。 | 原生安装；重启本安装 |
| C188<br>`app.web.directory`<br>路径 | `native=<bundle>/web-dist` (S10) | 原生 &lt;bundle&gt;/web-dist；Docker 独立 Web 服务 | — | **D** 前端资源目录；原生启动器按当前包传入，换目录不会重建前端。 | 原生安装；重启本安装 |
| C189<br>`spring.config.additional-location`<br>URI | `launcher: external private configuration URI` (S11) | 未另写入；采用所加载配置/实现默认值 | — | **D** 启动器指定私有外部配置的位置，勿同时维护多个冲突入口。 | 平台；重启本安装 |
| C191<br>`ijob.native.home`<br>路径 | `launcher: IJOB_HOME process identity` (S11) | 未另写入；采用所加载配置/实现默认值 | — | **D** 进程身份校验标记；启动器生成，不可冒用或独立改写。 | 原生安装；重启本安装 |
| C192<br>`ijob.native.log-directory`<br>路径 | `launcher: <IJOB_HOME>/logs` (S12) | 未另写入；采用所加载配置/实现默认值 | — | **D** 日志目录由 IJOB_HOME 派生，随安装目录管理。 | 原生安装；重启本安装 |
| C193<br>`ijob.native.log-session`<br>文本 | `launcher-generated unique launch identity` (S12) | 未另写入；采用所加载配置/实现默认值 | — | **D** 本次启动的就绪日志标记，由启动器生成。 | 原生安装；重启本安装 |

<a id="agent"></a>

## 独立监控 Agent

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C176<br>`spring.application.name`<br>文本 | `ijob-monitor-agent` (S08) | 未另写入；采用所加载配置/实现默认值 | — | **D** Agent 应用标识。 | 独立 Agent；重启 Agent |
| C177<br>`agent.token`<br>秘密 | `${IJOB_AGENT_TOKEN:}` (S08) | 未另写入；采用所加载配置/实现默认值 | `IJOB_AGENT_TOKEN` | **C** Agent 认证令牌；部署双方一致，镜像从私有 token 文件导入。 | 独立 Agent；重启 Agent |
| C178<br>`agent.collect-timeout`<br>毫秒 | `${IJOB_AGENT_COLLECT_TIMEOUT:10000}` (S08) | 未另写入；采用所加载配置/实现默认值 | `IJOB_AGENT_COLLECT_TIMEOUT` | **B** 单次采集超时，毫秒；按被监控主机负载评估。 | 独立 Agent；重启 Agent |
| C179<br>`agent.collect-concurrency`<br>整数 | `${IJOB_AGENT_COLLECT_CONCURRENCY:2}` (S08) | 未另写入；采用所加载配置/实现默认值 | `IJOB_AGENT_COLLECT_CONCURRENCY` | **B** 并行采集数；结合目标主机资源评估。 | 独立 Agent；重启 Agent |
| C180<br>`agent.collect-queue-capacity`<br>整数 | `${IJOB_AGENT_COLLECT_QUEUE_CAPACITY:4}` (S08) | 未另写入；采用所加载配置/实现默认值 | `IJOB_AGENT_COLLECT_QUEUE_CAPACITY` | **B** 采集等待队列容量；过大可能延迟返回。 | 独立 Agent；重启 Agent |
| C181<br>`agent.hostname`<br>文本 | `unset: detected automatically` (S09) | 未另写入；采用所加载配置/实现默认值 | — | **A** 上报的主机名称；不配置时自动探测，可用于明确监控对象身份。 | 独立 Agent；重启 Agent |
| C197<br>`server.port`<br>端口 | `8081 in image; not explicitly set in Agent YAML` (S13) | 未另写入；采用所加载配置/实现默认值 | — | **B** Agent 镜像内部 8081；修改需同步探针和平台资源连接。 | 独立 Agent；重启 Agent |
| C198<br>`spring.config.import`<br>URI | `configtree:/run/secrets/ in image` (S13) | 未另写入；采用所加载配置/实现默认值 | — | **D** 镜像私有 agent.token 文件导入方式，保留与挂载一致。 | 独立 Agent；重启 Agent |

<a id="deployment"></a>

## 启动、镜像锁与开发变量

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C199<br>`IJOB_HOME`<br>路径 | `Windows: sibling ijob-data; Linux: sibling ijob-data-linux; Docker: sibling ijob-data-docker` (S14) | 见默认值及适用范围 | — | **A** 安装数据根目录；所有管理命令一致，更改路径不会自动搬迁数据。 | 启动环境；下一次对应命令生效 |
| C200<br>`IJOB_LANG`<br>枚举 | `en; zh selects Chinese` (S15) | 见默认值及适用范围 | — | **A** 初始化/管理命令语言，不是界面偏好或通知语言。 | 启动环境；下一次对应命令生效 |
| C201<br>`JAVA_HOME`<br>路径 | `unset: resolve Java from PATH` (S11) | 见默认值及适用范围 | — | **A** 原生启动的 Java 21 安装目录。 | 原生安装；重启本安装 |
| C202<br>`IJOB_START_TIMEOUT`<br>秒 | `120` (S16) | 见默认值及适用范围 | — | **B** 等待启动就绪的秒数，范围 1–600；仅 Linux 启动脚本使用，Windows 当前固定等待。 | 仅 Linux 原生脚本 |
| C203<br>`IJOB_STOP_TIMEOUT`<br>秒 | `120` (S17) | 见默认值及适用范围 | — | **B** 等待优雅停止的秒数，范围 1–600；仅 Linux 启动脚本使用，Windows 当前固定等待。 | 仅 Linux 原生脚本 |
| C204<br>`IJOB_HTTP_PORT`<br>端口 | `18080` (S10) | 见默认值及适用范围 | — | **A** Docker 宿主映射端口，改变后用同一安装包 --recreate。 | Docker；重建对应容器 |
| C205<br>`IJOB_HTTP_BIND`<br>文本 | `127.0.0.1` (S18) | 见默认值及适用范围 | — | **B** 仅仓库高级 Compose 模板支持；初始化器生成的 Compose 固定回环绑定，不能只设置此变量。 | 仓库高级 Compose；重建对应容器 |
| C206<br>`IJOB_CONFIG_PATH`<br>路径 | `required; no default` (S18) | 见默认值及适用范围 | — | **C** 高级 Compose 私有 YAML 路径；首次安装包使用 IJOB_HOME/application.properties。 | 仓库高级 Compose；重建对应容器 |
| C207<br>`IJOB_AGENT_TOKEN_PATH`<br>路径 | `required; no default` (S19) | 见默认值及适用范围 | — | **C** Agent 私有令牌文件路径，不是令牌值。 | Agent Compose；重建 Agent |
| C208<br>`IJOB_IMAGE_TAG`<br>文本 | `1.0.0` (S18) | 见默认值及适用范围 | — | **D** 仅高级 Compose 标签；交付包按镜像 ID 锁启动，标签不替代来源校验。 | 仓库高级 Compose；重建对应容器 |
| C209<br>`IJOB_PLATFORM_DB`<br>枚举 | `setup selects vendor; repository YAML fallback mysql` (S10) | 见默认值及适用范围 | — | **C** 原生启动器从安装元数据传入；Docker 需在 backend environment 中一致配置。 | 启动环境；下一次对应命令生效 |
| C210<br>`IJOB_PLATFORM_DB_SCHEMA`<br>文本 | `PostgreSQL public; SQL Server dbo; Oracle IJOB_DB_USERNAME or ijob` (S04) | 见默认值及适用范围 | — | **C** 数据库专用配置的 schema 占位符；对齐 JDBC、平台及迁移 schema。 | 启动环境；下一次对应命令生效 |
| C211<br>`IJOB_DB_USERNAME`<br>文本 | `ijob for Oracle schema fallback only` (S04) | 见默认值及适用范围 | — | **C** 在 Oracle 模板中仅作为 schema 回退，不会替代 spring.datasource.username。 | 启动环境；下一次对应命令生效 |
| C212<br>`IJOB_MAVEN_IMAGE`<br>镜像 | `pinned in develop/deployment/docker/images.env` (S20) | 见默认值及适用范围 | — | **D** 构建基础镜像锁；按依赖升级审核更新，不作为运行调参。 | 构建阶段；重建镜像 |
| C213<br>`IJOB_JRE_IMAGE`<br>镜像 | `pinned in develop/deployment/docker/images.env` (S20) | 见默认值及适用范围 | — | **D** 构建基础镜像锁；按依赖升级审核更新，不作为运行调参。 | 构建阶段；重建镜像 |
| C214<br>`IJOB_NODE_IMAGE`<br>镜像 | `pinned in develop/deployment/docker/images.env` (S20) | 见默认值及适用范围 | — | **D** 构建基础镜像锁；按依赖升级审核更新，不作为运行调参。 | 构建阶段；重建镜像 |
| C215<br>`IJOB_NGINX_IMAGE`<br>镜像 | `pinned in develop/deployment/docker/images.env` (S20) | 见默认值及适用范围 | — | **D** 构建基础镜像锁；按依赖升级审核更新，不作为运行调参。 | 构建阶段；重建镜像 |
| C216<br>`JAVA_TOOL_OPTIONS`<br>文本 | `images: -XX:MaxRAMPercentage=70 -Duser.timezone=UTC` (S21) | 见默认值及适用范围 | — | **B** JVM 启动参数；容器内保留 UTC 约定，结合内存限制调整。build.py 拒绝继承此变量。 | JVM；重启进程或重建容器 |
| C217<br>`IJOB_DEV_API_TARGET`<br>URI | `unset: no proxy` (S22) | 见默认值及适用范围 | — | **B** 前端开发服务代理目标；不写入正式包的运行配置，不能误连演示环境。 | 开发服务；重启开发服务 |
| C218<br>`SPRING_PROFILES_ACTIVE`<br>文本 | `backend image container; first-run override release` (S21) | 见默认值及适用范围 | — | **D** 由镜像/启动器选择配置 profile；不当作普通环境开关。 | Docker；重建对应容器 |
| C219<br>`SPRING_CONFIG_ADDITIONAL_LOCATION`<br>URI | `image /run/secrets/ijob.yaml; first-run /run/secrets/application.properties` (S10) | 见默认值及适用范围 | — | **D** Docker 外部配置挂载读取位置，必须与实际挂载一致。 | Docker；重建对应容器 |
| C220<br>`SPRING_CONFIG_IMPORT`<br>URI | `agent image configtree:/run/secrets/` (S13) | 见默认值及适用范围 | — | **D** Agent 镜像通过配置树读取私有 token 文件。 | Agent 镜像；重建 Agent 容器 |
| C221<br>`SERVER_PORT`<br>端口 | `Agent image 8081` (S13) | 见默认值及适用范围 | — | **D** Agent 镜像设置内部端口；需要与探针和连接配置同步。 | Agent 镜像；重建 Agent 容器 |
| C222<br>`IJOB_BACKEND_IMAGE`<br>镜像 ID | `generated sha256 image ID in docker-images.lock` (S23) | 见默认值及适用范围 | — | **D** 交付包生成的镜像锁记录，不是可覆盖启动镜像的普通环境变量。 | 包生成记录；重建交付包 |
| C223<br>`IJOB_WEB_IMAGE`<br>镜像 ID | `generated sha256 image ID in docker-images.lock` (S23) | 见默认值及适用范围 | — | **D** 交付包生成的镜像锁记录，不是可覆盖启动镜像的普通环境变量。 | 包生成记录；重建交付包 |
| C224<br>`IJOB_AGENT_IMAGE`<br>镜像 ID | `generated sha256 image ID in docker-images.lock` (S23) | 见默认值及适用范围 | — | **D** 交付包生成的镜像锁记录，不是可覆盖启动镜像的普通环境变量。 | 包生成记录；重建交付包 |

<a id="compose"></a>

## Compose 运维字段补充

| 编号 / 键 / 类型 | 源码声明与来源 | 新安装 / 覆盖 | 环境别名 | 建议与说明 | 范围 / 生效 |
| --- | --- | --- | --- | --- | --- |
| C225<br>`name`<br>Compose 字段 | `setup-generated project; advanced ijob-v1-candidate` (S10) | 见默认值及适用范围 | — | **C** 项目身份；恢复或多实例必须使用独立项目名。 | 对应 Compose 服务；重建容器 |
| C226<br>`services.<service>.image`<br>Compose 字段 | `distribution exact image ID override` (S10) | 见默认值及适用范围 | — | **D** 精确镜像锁归包管理；不要只改固定 1.0.0 标签升级。 | 对应 Compose 服务；重建容器 |
| C227<br>`services.web.ports`<br>Compose 字段 | `127.0.0.1:<IJOB_HTTP_PORT>:8080` (S10) | 见默认值及适用范围 | — | **B** 宿主绑定地址与映射端口；对外访问需配套入口和权限。 | 对应 Compose 服务；重建容器 |
| C228<br>`services.<service>.volumes`<br>Compose 字段 | `setup config/runtime binds; advanced named data volumes` (S10) | 见默认值及适用范围 | — | **C** 私有配置、数据和插件挂载；恢复时逐项检查源目录及权限。 | 对应 Compose 服务；重建容器 |
| C229<br>`services.<service>.environment`<br>Compose 字段 | `template-specific` (S10) | 见默认值及适用范围 | — | **C** 容器环境变量；不是宿主 shell 的自动继承，保持与配置文件一致。 | 对应 Compose 服务；重建容器 |
| C230<br>`services.<service>.mem_limit`<br>Compose 字段 | `backend 1g; web 128m; agent 256m` (S18) | 见默认值及适用范围 | — | **B** 内存上限；与 JVM 比例和工作负载共同调整。 | 对应 Compose 服务；重建容器 |
| C231<br>`services.<service>.cpus`<br>Compose 字段 | `backend 2.0; advanced web/agent 1.0; setup web unset` (S18) | 见默认值及适用范围 | — | **B** CPU 配额；初始化 Web 未显式设置，高级模板与初始化模板需区分。 | 对应 Compose 服务；重建容器 |
| C232<br>`services.<service>.restart`<br>Compose 字段 | `unless-stopped` (S18) | 见默认值及适用范围 | — | **B** 进程重启策略；不等于任务恢复或幂等保证。 | 对应 Compose 服务；重建容器 |
| C233<br>`services.<service>.stop_grace_period`<br>Compose 字段 | `backend 120s; advanced web/agent 30s; setup web unset` (S18) | 见默认值及适用范围 | — | **B** 停止宽限；初始化 Web 未显式设置，后端应覆盖排空与关闭所需时间。 | 对应 Compose 服务；重建容器 |
| C234<br>`services.<service>.logging.driver`<br>Compose 字段 | `json-file` (S18) | 见默认值及适用范围 | — | **B** 容器日志驱动；更换时验证采集和查看方式。 | 对应 Compose 服务；重建容器 |
| C235<br>`services.<service>.logging.options.max-size`<br>Compose 字段 | `10m` (S18) | 见默认值及适用范围 | — | **A** 每份容器日志滚动大小。 | 对应 Compose 服务；重建容器 |
| C236<br>`services.<service>.logging.options.max-file`<br>Compose 字段 | `backend/web 5; agent 3` (S18) | 见默认值及适用范围 | — | **A** 保留容器日志文件数量。 | 对应 Compose 服务；重建容器 |
| C237<br>`services.<service>.tmpfs`<br>Compose 字段 | `backend 256m; web 128m; agent 64m; template permissions` (S18) | 见默认值及适用范围 | — | **B** 临时空间大小、权限及 exec 标志；与插件运行需要一起审核。 | 对应 Compose 服务；重建容器 |
| C238<br>`services.<service>.read_only`<br>Compose 字段 | `true` (S18) | 见默认值及适用范围 | — | **D** 只读容器根文件系统约束，使用明确的数据挂载。 | 对应 Compose 服务；重建容器 |
| C239<br>`services.<service>.cap_drop`<br>Compose 字段 | `ALL` (S18) | 见默认值及适用范围 | — | **D** 保留最小容器权限，不为业务能力直接放开。 | 对应 Compose 服务；重建容器 |
| C240<br>`services.<service>.security_opt`<br>Compose 字段 | `no-new-privileges:true` (S18) | 见默认值及适用范围 | — | **D** 保留禁止提权约束。 | 对应 Compose 服务；重建容器 |
| C241<br>`services.<service>.init`<br>Compose 字段 | `backend/agent true; web not set` (S18) | 见默认值及适用范围 | — | **D** 容器进程信号与回收处理，保持经过验证的入口。 | 对应 Compose 服务；重建容器 |
| C242<br>`services.web.depends_on`<br>Compose 字段 | `backend health; cluster also backend-b health` (S18) | 见默认值及适用范围 | — | **D** 启动就绪依赖；不等于持续流量健康管理。 | 对应 Compose 服务；重建容器 |
| C243<br>`secrets.<secret>.file`<br>Compose 字段 | `advanced private config/token paths` (S18) | 见默认值及适用范围 | — | **C** 高级 Compose 秘密文件源；限制权限，不进仓库。 | 对应 Compose 服务；重建容器 |
| C244<br>`volumes.<volume>`<br>Compose 字段 | `advanced backend-data / backend-b-data` (S18) | 见默认值及适用范围 | — | **C** 高级模板持久卷身份；勿指向旧环境或删除以重置。 | 对应 Compose 服务；重建容器 |
| C245<br>`services.<service>.healthcheck.interval`<br>时长 | `backend/web 15s; Agent 30s` (S21) | 见默认值及适用范围 | — | **B** 健康检查周期；镜像默认值来自各 Dockerfile，覆盖后重新验证就绪判定。 | Docker；重建对应容器 |
| C246<br>`services.<service>.healthcheck.timeout`<br>时长 | `backend/Agent 6s; web 5s` (S21) | 见默认值及适用范围 | — | **B** 单次健康检查超时；镜像默认值来自各 Dockerfile，覆盖后重新验证就绪判定。 | Docker；重建对应容器 |
| C247<br>`services.<service>.healthcheck.start_period`<br>时长 | `backend 90s; Agent 30s; web not explicitly set` (S21) | 见默认值及适用范围 | — | **B** 启动探测宽限；镜像默认值来自各 Dockerfile，覆盖后重新验证就绪判定。 | Docker；重建对应容器 |
| C248<br>`services.<service>.healthcheck.retries`<br>整数 | `3` (S21) | 见默认值及适用范围 | — | **B** 失败次数阈值；镜像默认值来自各 Dockerfile，覆盖后重新验证就绪判定。 | Docker；重建对应容器 |
| C249<br>`services.<service>.healthcheck.test`<br>命令 | `backend readiness; web /healthz; Agent authenticated probe` (S21) | 见默认值及适用范围 | — | **D** 镜像内健康检查命令；镜像默认值来自各 Dockerfile，覆盖后重新验证就绪判定。 | Docker；重建对应容器 |

## 实例持久入口（instance.conf）

[配置和使用步骤](installation/instance-launcher.md)。以下 5 项属于控制目录，不写进 application.properties。

| ID / 字段 | 级别 | 初始值 | 说明 |
| --- | --- | --- | --- |
| C250 / `instance.conf:version` | D | 1 (S33/S34) | 控制文件格式版本；当前为 1，不手动调整。 |
| C251 / `instance.conf:bundle` | C | configure: program directory (S33/S34) | 完整程序目录；停机后按升级流程调整。 |
| C252 / `instance.conf:home` | C | configure: required explicit data directory (S33/S34) | 本实例数据目录；覆盖继承的 IJOB_HOME，修改不会搬迁数据。 |
| C253 / `instance.conf:lang` | A | en; zh selects Chinese (S33/S34) | 实例启动提示语言；覆盖继承的 IJOB_LANG，不改变网页语言。 |
| C254 / `instance.conf:mode` | C | Windows: windows; Bash: linux (or explicit docker) (S33/S34) | 创建时确定的部署方式；不能只改此值迁移实例，不是 STANDALONE/CLUSTER。 |

## 源码依据与维护

来源路径均相对仓库根。共享机器清单位于 `document/reference/configuration-review.json`，含双语说明、来源哈希及分类。两种语言由同一批条目生成；表内来源 ID 对应下表。Compose 中 Agent 差异还应对照 compose.agent.yaml，集群差异对照 compose.cluster.yaml；构建时按三个 Dockerfile 的探针声明核对。

| ID | 源码路径 |
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
| S11 | `develop/deployment/first-run/manage.ps1` |
| S12 | `develop/deployment/first-run/logging-native.xml` |
| S13 | `develop/api/ijob-monitor-agent/Dockerfile` |
| S14 | `develop/deployment/first-run/native-common.sh` |
| S15 | `develop/deployment/first-run/start-docker.sh` |
| S16 | `develop/deployment/first-run/start.sh` |
| S17 | `develop/deployment/first-run/stop.sh` |
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
| S33 | `develop/deployment/first-run/instance.ps1` |
| S34 | `develop/deployment/first-run/ijob.sh` |

分组、白名单和全部插件开关按已确认分级进入正文；数据库/凭据使用受控流程，内部参数保留附录。详见[配置正文](configuration.md)与[页面手册](user-guide/README.md)。
