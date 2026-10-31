# 最低运行条件与兼容性

[English](../../en/installation/requirements.md) · [手册首页](../README.md)

选择安装方式之前，先确认操作系统、运行时、平台库和任务依赖。本文适用于 iJob 1.0.0，列出运行要求和已有兼容性参考组合；正式发行包的版本清单决定该包的具体范围。

## 操作系统与运行时

| 维度 | 运行要求 | 具体参考配置 | 使用边界 |
| --- | --- | --- | --- |
| Windows 原生 | x64、Java 21、PowerShell 启动入口 | Windows Server 2022，build 20348；Oracle JDK 21.0.10 x64 | Server Core 的服务托管与桌面版的业务组合覆盖分别看待；Windows 10/11、其他 Server 版本和 ARM64 未列入已确认范围 |
| Linux 原生 | Java 21、Bash、util-linux 的 flock | Ubuntu 24.04 amd64 | 不据此推广到所有 Linux 发行版；systemd 托管需 systemd |
| Linux Docker | Linux 容器、Bash、Docker Engine 和 `docker compose` 插件 | 已核对的软件输入为 Engine/CLI 29.9.1、Compose 插件 5.6.0、Ubuntu 24.04 amd64 | 软件版本已核对不等于任意业务组合均适用；不是 Windows Docker Desktop 支持声明 |
| Java | 主要运行要求为 21 | 当前 Linux 镜像构建固定 Temurin 21.0.13+9；Windows 兼容性样本为 21.0.10 | 样本补丁版本不代表持续安全更新建议；安装时核对实际包和运行时 |
| 任务解释器 | 对应执行机器上可用的解释器 | Windows BAT/PowerShell；Linux SHELL；按任务配置 Python | 默认容器不包含所有解释器；SSH 执行只支持 SHELL/PYTHON |

Compose 文档中的“插件式入口”指 `docker compose` 命令，不限制为某个 2.x 版本号。安装前运行 `java -version`、`docker version` 和 `docker compose version`，与包说明核对。浏览器所在机器不必安装任务解释器。

## 五种平台数据库

“支持部署模式”表示产品的连接与协调能力；版本列表示具体参考组合，不能外推任意旧版或新版。平台库保存定义、运行历史和协调状态，业务库由插件资源单独配置。

| 平台数据库 | 版本参考 | 单实例 | CLUSTER | 等级与边界 |
| --- | --- | --- | --- | --- |
| SQLite | 3.49.1，JDBC 3.49.1.0 | 支持 | 不支持 | 内嵌单实例平台库；不等于页面提供 SQLite 业务数据库资源 |
| MySQL | Windows 8.0.35；Linux 8.4.11 Community | 支持 | 支持 | 服务数据库；Windows 单节点与 Linux 双节点属于不同组合 |
| PostgreSQL | 18.6 | 支持 | 支持 | 服务数据库；部署前核对 schema、权限和连接 |
| SQL Server | Windows 2022 Developer RTM 16.00.1000；Linux 2022 Developer 16.0.4305.5 | 支持 | 支持 | Developer 为兼容性样本，生产版别和许可另行选择；排序规则需满足平台要求 |
| Oracle | AI Database 26ai Free 23.26.3.0.0 | 支持 | 支持 | Free 为参考版本；不构成对 11g 等旧版本的承诺 |

服务数据库的集群节点必须连接同一逻辑库与 schema；禁止 STANDALONE/CLUSTER 同库混跑。Oracle 平台 schema 需要相应 `SYS.DBMS_LOCK` 执行权限，不以授予 DBA 代替最小权限。更多配置见[平台数据库](../configuration/platform-database.md)。

## 浏览器范围

| 浏览器 | 当前范围 |
| --- | --- |
| Chromium | 产品浏览器自动化配置覆盖的内核；具体发行包应附浏览器版本 |
| Chrome / Edge | 尚未单独声明品牌浏览器的最低版本或完整兼容区间；不能仅凭 Chromium 覆盖视为所有版本通过 |
| Firefox / Safari | 尚未列入已确认的产品浏览器范围 |
| 移动浏览器 | 文档站适配窄屏；不等于产品完整移动端操作已确认 |

## 节点拓扑

| 场景 | 角色安排 | 需要注意 |
| --- | --- | --- |
| 学习与轻量运行 | 同一实例包含 API、SCHEDULER、WORKER、MAINTENANCE | 默认四角色合并，降低部署复杂度 |
| 隔离执行负载 | Worker 独立部署；其他职责按负载组合 | Worker 上独立准备脚本、文件、Local JAR 与资源访问 |
| 多节点协作 | 完整覆盖四种角色，共享逻辑平台库 | 每节点独立身份、数据目录、日志及兼容配置 |
| 物理高可用 | 多故障域、冗余职责和数据库可用性设计 | 同宿主多进程不能消除宿主故障 |

[角色与集群配置](../operations/standalone-cluster.md)说明职责和一致性要求。API 处理管理与触发；SCHEDULER 扫描计划；WORKER 领取执行；MAINTENANCE 负责后台维护。

## CPU、内存、磁盘和连接预算

尚无经过容量测量的统一最低硬件或生产吞吐承诺。已有 Windows 参考环境为 **4 vCPU、8 GiB 内存、96 GiB 磁盘**，这是一组环境规格，不是最低要求。任务类型、并发、外部数据库和日志保留会改变需求。

| 项目 | 计算与配置方式 |
| --- | --- |
| CPU | 同时考虑平台线程与任务子进程；先运行代表性负载，再确定并发 |
| 内存 | JVM 堆及原生内存 + 并发脚本进程峰值 + ETL 缓冲 + 系统/容器/数据库 + 余量 |
| 磁盘 | 程序与保留升级包 + 平台库与索引 + 日志日增量×保留天数 + 临时文件峰值 + 备份空间 |
| 数据库连接 | 所有平台节点连接池上限合计 + 业务资源连接 + 监控和运维余量 |
| 默认连接/执行设置 | 平台连接池上限 4、Worker 根实例并发上限 20、调度线程 8；这些是不同限制，不代表可以无条件同时运行 20 个重任务 |
| Docker 默认限制 | 生成的 backend 上限 1 GiB / 2 CPU，web 内存上限 128 MiB；它们是容器限制，不是宿主最低规格 |

## 端口与日志

| 项目 | 默认值 / 规划 |
| --- | --- |
| 原生 HTTP / Docker 宿主入口 | 18080；同机不同实例使用不同端口 |
| Docker 内部 web / backend | 8080；容器间连接按 Compose 网络配置 |
| Monitor Agent 容器 | 8081；按监控资源实际地址配置 |
| 原生应用日志 | 单文件 10 MB、归档历史 14 天、归档总量 200 MB；总量上限可能提前清理文件 |
| 容器日志 | 每服务 10 MB × 5 文件轮转 |
| 平台业务历史 | 与应用日志分开；默认保留清理关闭，dry-run=true，不会自动按标称天数删除 |

数据库、SSH、HTTP、MQ、文件服务使用它们各自的实际端口，不要求所有插件端口一并开放。依据节点职责只开放需要的连接。

[安装选择](../installation.md) · [容量配置](../configuration/capacity.md) · [保留策略](../configuration/scheduling-retention.md) · [生产部署](../operations/production.md)
