# 用户配置

[English](../en/configuration.md) · [手册首页](README.md)

配置位于 `IJOB_HOME/application.properties`，由初始化器生成并限制权限。无需解包或修改 JAR，也不需要重新构建。该文件包含密码哈希、数据库口令和 AES 密钥，不应上传、截图或作为案例共享。编辑前停止本安装并备份，修改已有键而非重复追加，之后重新启动并核对就绪与功能。

先查看本版本支持的用户调整项：

```sh
java -jar ijob-setup-tool-1.0.0.jar settings --lang zh
java -jar ijob-setup-tool-1.0.0.jar check-settings --home /absolute/installation-a --lang zh
```

Windows 将 home 替换为实际绝对路径。检查只验证下表配置的格式和重复键，不连接数据库，不验证其他高级键，也不能推断命令行/环境覆盖后的实际值；不会输出实际配置值。应用启动校验与运行验收仍必须完成。

例如，修改原生端口和共用 HTTP 超时：

```properties
server.port=18082
app.http.connect-timeout=10s
app.http.read-timeout=60s
```

以下清单由双语配置目录生成，普通用户不需要调整未列出的内部协调或迁移参数。

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
| `app.notification.locale` | locale | en | 通知默认语言，可被具体通知配置覆盖。 |
| `app.event.ingress.enabled` | boolean | false | 启用事件接入；仍要求登录和 EVENT_PUBLISH 权限。 |
| `app.retention.enabled` | boolean | false | 核对保留规则后再启用历史清理。 |
| `app.retention.dry-run` | boolean | true | 保持 true 时只演练；清理开启且为 false 时允许删除。 |
| `ijob.logging.max-file-size` | size | 10MB | 原生日志单文件滚动大小；Docker 日志在 Compose 中配置。 |
| `ijob.logging.max-history` | positive-int | 14 | 原生日志归档保留天数。 |
| `ijob.logging.total-size-cap` | size | 200MB | 原生日志归档总容量上限，可能提前清理未到期归档。 |

<!-- /SETTINGS_TABLE -->

## 生效方式与维护边界

外部文件覆盖包内默认值；启动器明确传入的参数、JVM 系统属性和适用的环境变量仍可能覆盖文件。不要同时在多处维护同一个值。修改后需要重启，不支持普通配置文件自动热加载。

Docker 内 `server.port` 保持 8080、监听地址保持 0.0.0.0。宿主端口、资源限制、日志滚动、挂载和容器环境在 `IJOB_HOME/compose.yaml` 或对应部署环境中修改，再使用同一项目 `docker compose ... up -d`。若编辑器替换了挂载文件，使用 `up -d --force-recreate backend` 重新挂载；不要删除数据卷。

任务、作业、资源、调度、日历、通知和参数模板通过管理页面维护，通常不需要重启。平台配置中的全局开关不替代这些业务配置。

认证模式切换、数据库产品切换、密钥轮换和集群组建不是普通调参操作，见[运维手册](operations.md)。保留原 AES 密钥；不要通过重跑初始化、修改迁移保护开关或清空数据库来绕过错误。
