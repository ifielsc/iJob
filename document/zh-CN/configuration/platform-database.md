# 平台数据库：单实例、集群与业务资源

[English](../../en/configuration/platform-database.md) · [手册首页](../README.md)

## 先区分两种数据库连接

`spring.datasource.url`、`spring.datasource.username`、`spring.datasource.password` 指向 **iJob 平台数据库**，保存任务/作业定义、运行记录、调度、资源配置及集群协调数据。修改位置是每个节点数据目录中的 application.properties，属于受控变更。

SQL、ETL、PUSH 等插件访问的 **业务数据库** 在页面“资源 → 数据库资源”中配置，由任务选择。它与平台连接独立；不要为切换任务的数据源而修改 spring.datasource，也不要把平台库作为练习任务目标。

| 部署方式 | 平台数据库配置 |
| --- | --- |
| 单实例 STANDALONE | 一套实例连接自己的平台库；可用 SQLite 或受支持的 MySQL、PostgreSQL、SQL Server、Oracle。单实例不等于必须 SQLite。 |
| 多节点 CLUSTER | 每个节点在自己的 application.properties 中配置，连接同一个逻辑平台库及 schema；SQLite 不支持。连接地址字符串可因网络入口不同而不同，但实际平台数据必须相同。 |
| 多套独立系统 | 每套独立平台库/schema、数据目录、身份与端口，不因部署在同一台机器就组成集群。 |

## 集群还需要什么

数据库 URL 相同并不足以组成集群。各节点还须设置 CLUSTER、独立实例名、完整角色覆盖，并使用兼容版本、相同认证模式和要求一致的 BCrypt/AES/Token 配置。每个节点有独立 home/控制目录/日志；共享数据库不会复制脚本或文件。按[模式切换流程](../operations/runtime-mode-change.md)统一停机和验证，禁止 STANDALONE/CLUSTER 同库混跑。

| 参数 | 多节点时的含义 |
| --- | --- |
| spring.datasource.url | 指向共享平台库；主机地址必须从该节点或容器可达。容器 localhost 指容器自身。 |
| spring.datasource.username / password | 每个节点的数据库连接身份；需具有平台要求的权限，并连接同一个平台 schema。不是网页登录账号。 |
| spring.datasource.hikari.maximum-pool-size | 每节点连接池上限；数据库总容量需考虑所有节点的合计及其他连接。 |
| 数据库产品、驱动、平台/Flyway/default schema | 与连接和安装元数据保持一致；不能只改 URL 来更换产品或迁移数据。 |

新安装向导默认 STANDALONE，选择服务数据库不会自动开启集群。已有平台库改地址先区分“同一库的新地址”和“新库/跨产品迁移”，后者需单独迁移/恢复方案。按[备份恢复](../operations/backup-restore.md)保留原数据库、配置和 AES 密钥，变更后检查启动迁移、登录、旧定义/历史及无副作用任务。

## 与启动入口的关系

instance.conf 只定位程序包、home、语言和安装方式；数据库参数仍留在 home/application.properties。settings/check-settings 的 54 项检查不等于数据库连通或集群一致性验收，服务数据库是否可用要以启动和实际运行验证为准。
