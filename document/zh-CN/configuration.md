# 用户配置：位置、分级与生效

[English](../en/configuration.md) · [手册首页](README.md)

## 先分清三种配置

| 类型 | 修改入口 | 生效 |
| --- | --- | --- |
| 平台启动配置 | IJOB_HOME/application.properties | 原生重启；Docker 使用 start-docker.sh --recreate |
| 宿主/容器配置 | 环境变量、同一 home 的 compose.yaml | 重新启动对应进程或重建容器 |
| 任务/作业/资源/调度等业务配置 | 管理页面 | 按页面保存/发布/启用；一般不重启平台 |

外部文件覆盖包内默认值，但命令行、JVM 属性、适用环境变量仍可能覆盖文件。同一个键只在一个位置维护。properties 中 Windows 路径推荐 `/`；反斜杠须按 properties 转义。不要将示例中的占位路径原样用于业务。

## 按风险和用途阅读

| 级别 | 建议 | 入口 |
| --- | --- | --- |
| A 常用 | 允许用户按业务调整 | [基础设置](configuration/common.md)、[分组与范围](configuration/routing.md)、[插件开关](configuration/plugins.md) |
| B 高级 | 理解容量和联动后调整 | [调度与保留](configuration/scheduling-retention.md)、[容量](configuration/capacity.md)、[部署与 Agent](configuration/deployment-agent.md) |
| C 受控 | 允许按备份、变更、验证流程调整 | [受控项](configuration/controlled.md)及运维流程 |
| D 内部 | 不建议作为普通用户参数 | [内部附录](configuration/internals.md) |

[完整清单](configuration-catalog.md)保留 254 条来源、默认值和分级；不是要求用户逐项填写。新安装会覆盖部分源码默认值，例如 native 端口 18080、分组 default、调度开启；不要直接把源码 YAML 当作安装后的配置。

## 安全地修改一项

1. 确认访问节点、程序版本和 IJOB_HOME；备份当前私有配置。
2. 阅读该项作用范围和联动限制，仅修改需要的键，避免重复键。
3. 在程序目录运行下方检查器，再按平台文档重启。
4. 用工作台/实际小任务验证效果；有问题恢复原配置并重启。

```sh
java -jar ijob-setup-tool-1.0.0.jar settings --lang zh
java -jar ijob-setup-tool-1.0.0.jar check-settings --home /absolute/ijob-home --lang zh
```

Windows 将 --home 改为自己的绝对目录。检查器列出并检查下表 54 项的格式、重复键和部分范围；不输出配置秘密。**它不验证数据库可达、角色组合、分组能否领取任务、目录权限，也不验证全部 254 项。** 未列出的高级项仍需应用启动校验和实际验证。空白表示空集合的项按相应章节处理。

<!-- SETTINGS_TABLE -->

| 配置项 | 类型 | 新安装默认值 | 说明 |
| --- | --- | --- | --- |
| `server.address` | text | 127.0.0.1 | 原生监听地址；Docker 容器内部保持 0.0.0.0。 |
| `server.port` | port | 18080 | 原生 HTTP 端口；Docker 保持内部 8080，修改宿主端口映射。 |
| `spring.datasource.url` | text | generated | 初始化生成的数据库地址；更换数据库产品必须另行迁移。 |
| `spring.datasource.username` | text | generated | 数据库账号，SQLite 不使用；不要将凭据写入连接地址。 |
| `spring.datasource.password` | secret | generated | 仅在受限 application.properties 内修改，不通过命令行传递。 |
| `spring.datasource.hikari.maximum-pool-size` | positive-int | 4 | 连接池大小，应与数据库可承受连接数一起规划。 |
| `app.schedule.enabled` | boolean | true | 全局调度扫描开关；具体调度仍需在页面启用。 |
| `app.plugin.etl.enabled` | boolean | true | 启用 ETL 任务。 |
| `app.plugin.batch-etl.enabled` | boolean | true | 启用批量 ETL 任务。 |
| `app.plugin.local-jar.enabled` | boolean | false | 准备好目录和依赖后，按需启用可信 Local JAR 插件。 |
| `app.plugin.local-jar.root-directory` | text | empty | 执行节点的插件绝对目录；Docker 需要显式挂载。 |
| `app.resource-browser.local-roots` | text | empty | 允许浏览 LOCAL 资源草稿的绝对根目录，多个目录用逗号分隔。 |
| `app.http.connect-timeout` | duration | 5s | 共用 HTTP 客户端连接超时，不代表所有外部插件的超时。 |
| `app.http.read-timeout` | duration | 30s | 共用 HTTP 客户端读取超时。 |
| `app.notification.locale` | notification-locale | en | 通知默认语言，可被具体通知配置覆盖。 |
| `app.event.ingress.enabled` | boolean | false | 启用事件接入；仍要求登录和 EVENT_PUBLISH 权限。 |
| `app.retention.enabled` | boolean | false | 核对保留规则后再启用历史清理。 |
| `app.retention.dry-run` | boolean | true | 保持 true 时只演练；清理开启且为 false 时允许删除。 |
| `ijob.logging.max-file-size` | size | 10MB | 原生日志单文件滚动大小；Docker 日志在 Compose 中配置。 |
| `ijob.logging.max-history` | positive-int | 14 | 原生日志归档保留天数。 |
| `ijob.logging.total-size-cap` | size | 200MB | 原生日志归档总容量上限，可能提前清理未到期归档。 |
| `spring.web.locale` | locale | en | 服务端缺省语言；Accept-Language 和前端偏好另有作用。 |
| `app.scheduling.pool-size` | positive-int | 8 | 内部周期扫描线程数量；不是业务任务最大并发数。 |
| `app.runtime.instance-name` | text | generated | 节点标识；集群各节点取不同名称，便于追踪运行。 |
| `app.runtime.roles` | roles | API,SCHEDULER,WORKER,MAINTENANCE | API/SCHEDULER/WORKER/MAINTENANCE 职责；部署前确保所需角色仍有人承担。 |
| `app.runtime.groups` | groups | default | 节点执行分组；与发布定义的分组匹配，空节点分组并不表示能接所有分组。 |
| `app.runtime.allowed-task-ids` | ids | empty | 允许领取的根触发目标任务 ID；与作业白名单共同限制范围，见组合规则。 |
| `app.runtime.allowed-job-ids` | ids | empty | 允许领取的根触发目标作业 ID；不等价于对子作业内每个任务逐一授权。 |
| `app.runtime.work-queue.max-inflight-roots` | positive-int | 20 | 节点在途根运行上限；结合线程池、任务并发和资源容量评估。 |
| `app.schedule.zone-id` | zone | Asia/Shanghai | 调度默认业务时区；与用户显示时区不同，核验单条调度设置。 |
| `app.schedule.misfire-policy` | misfire | FIRE_ONCE | 错过触发默认策略 SKIP/FIRE_ONCE/CATCH_UP_BOUNDED；可能产生实际补发。 |
| `app.schedule.misfire-grace` | nonnegative-duration | 60s | 判断错过触发的宽限时间；联动补发策略验证。 |
| `app.schedule.max-catchup-window` | duration | 24h | 补发回溯时间上限；避免恢复后集中补发过多历史运行。 |
| `app.schedule.max-catchup-count` | positive-int | 10 | 补发次数上限；与时间窗口共同约束。 |
| `app.retention.interval` | duration | 1h | 历史清理：运行间隔。结合负载、恢复或清理策略验证后调整。 |
| `app.retention.batch-size` | positive-int | 500 | 历史清理：批次大小。结合负载、恢复或清理策略验证后调整。 |
| `app.retention.max-batches-per-run` | positive-int | 10 | 历史清理：单轮最大批次数。结合负载、恢复或清理策略验证后调整。 |
| `app.retention.execution` | duration | 90d | 执行历史保留时长；与清理总开关和试运行共同生效。 |
| `app.retention.notification` | duration | 90d | 通知历史保留时长；与清理总开关和试运行共同生效。 |
| `app.retention.event` | duration | 180d | 事件历史保留时长；与清理总开关和试运行共同生效。 |
| `app.retention.app-run` | duration | 30d | 节点运行历史保留时长；与清理总开关和试运行共同生效。 |
| `app.retention.audit` | duration | 90d | 审计历史保留时长；与清理总开关和试运行共同生效。 |
| `app.execution.shutdown-timeout` | duration | 30s | 执行器关闭等待；与容器停止宽限和 Spring 关闭时限一起规划。 |
| `app.plugin.event.enabled` | boolean | true | event 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| `app.plugin.script.enabled` | boolean | true | script 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| `app.plugin.sql.enabled` | boolean | true | sql 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| `app.plugin.file-transfer.enabled` | boolean | true | file-transfer 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| `app.plugin.api-call.enabled` | boolean | true | api-call 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| `app.plugin.push.enabled` | boolean | true | push 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| `app.plugin.data-check.enabled` | boolean | true | data-check 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| `app.plugin.server-monitor.enabled` | boolean | true | server-monitor 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| `sa-token.timeout` | session-seconds | 1800 | 登录令牌总有效期，单位秒；按会话策略调整。 |
| `sa-token.active-timeout` | session-seconds | 300 | 空闲会话超时，单位秒；区别于总有效期。 |
| `app.notify.email.enabled` | boolean | true | 邮件通道实现开关，缺省也启用；SMTP 连接与凭据在邮件通道页面维护。 |

<!-- /SETTINGS_TABLE -->

## 启动设置与数据库连接

程序/数据目录、提示语言放在[instance.conf](installation/instance-launcher.md)，与 application.properties 分开。平台数据库参数同时适用于单实例和集群节点；插件业务库在资源页面配置，详见[平台数据库](configuration/platform-database.md)。
