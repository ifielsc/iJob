# 隔离集成与发布准备

完整 Basic 保留七个 jobs、四片浏览器配置和覆盖范围；触发方式遵循[基础 CI 的选择规则](basic-ci.md)，push/PR 自动轻量检查，完整 Basic 按阶段手动运行。以下入口扩展本地验证，不把 Full 或五库运行验收混入 Basic 的成功结论。下文日期明确的历史结果保留其原提交/环境边界，不作为新候选自动通过的证明。

## P1：本地 HTTP/SQLite 与真实后端浏览器

先执行 `develop/api/ijob/tools/verify-backend.ps1`，用同一源码状态构建后端、SPI 和 Agent。再从仓库根目录执行以下两个入口，每次使用新 RunId：

本地三种验证入口均应设置 `-ReportRoot <workspace>/runtime/<env-id>/runs/<run-id>/reports` 与 `-TemporaryRoot <workspace>/runtime/<env-id>/runs/<run-id>/temp`。环境和资源先按[工作区规范](../operations/workspace-and-environments.md)登记。后端与专项入口自动从固定历史提交恢复 117 份 SQL 到本轮临时目录，并校验哈希；必须具有该提交的 Git 历史（CI backend 使用完整 checkout）。不需要旧数据库或旧工作区文件。

```powershell
pwsh -File tools/ci/verify-local-integration.ps1 -RunId local-integration-001
pwsh -File test/tools/verify-frontend.ps1 -RunId frontend-full-001 -Suite Full -BrowserWorkers 1
```

第一个入口精确选择 13 类专项：ConcurrencyHttpIT、DataCheckHttpIT、DiagnosticsHttpIT、EtlFileIncrementalIT、FilesHttpIT、InstanceLogSnapshotIT、PushHttpIT、SecurityCompletionIT、Stage9cLifecycleHttpIT、TableIdMigrationIT、TriggerBusinessTimeMigrationIT、UnifiedExperienceHttpIT、WorkbenchHttpIT。它明确使用 SQLite，为 Java 临时文件创建独立根目录；SecurityCompletionIT 的审计 fixture 显式设置 SQLite，防止缺参数而跳过。报告必须是本轮新生成的，每类均有用例、零失败、零错误、零跳过。Maven 失败时仍保留已生成的 JUnit，临时 fixture 目录保留供排查，不归档数据库。

Full 依次执行 Basic 和 security、batch-etl、diagnostics、etl、localjar、monitor、workbench 七套真实后端浏览器配置。Windows、JDK 21、Maven 3.9.11、Node 24.22.1、pnpm 11.28.8 和 Chromium 按基础入口要求准备；还须显式提供以下独立工具路径：

- `IJOB_TEST_PYTHON`：已安装 Paramiko 4.0.0 的 Python。
- `IJOB_TEST_SH`：Git Shell 的 sh 可执行文件。
- `IJOB_TEST_PG_BIN`：包含 initdb、pg_ctl、psql 的 PostgreSQL bin 目录。
- `IJOB_TEST_JAVA_HOME` 或 `JAVA_HOME`：JDK 21。可用 `IJOB_TEST_BACKEND_JAR`、`IJOB_TEST_PLUGIN_SPI_JAR` 和 `IJOB_TEST_CRYPTO_JAR` 精确指定同提交构件。

Full 拒绝继承业务 IJOB/SPRING 配置或现成测试目录，运行前检查 15438、18087、5176 未被监听；不停止占用端口的既有服务。Full 入口创建本轮独立临时根目录，同时设置并在结束后恢复 TEMP、TMP、TMPDIR，避免 Node 与 Python 的临时目录选择顺序不同。每套配置自己生成子目录和账号，使用新的 SQLite 文件；security 只通过 initdb 创建临时 PostgreSQL SQL 资源 fixture，不连接已安装的 PostgreSQL 服务。七套配置不得同机并发。所有 Full 浏览器报告均须零跳过。

真实浏览器后端使用 `db/release/sqlite` 正式 V1。历史迁移专项仍执行原 `db/migration` 升级链、验证数据和 mapper 行为，并断言正式应用拒绝直接启动历史库；不补造 release 状态、不改写冻结 SQL。正式 V1 的独立 ID 种子与启动检查另有正向回归。

## P2：隔离环境方案与阶段门禁

用户已选择全新、一次性 Linux amd64 虚拟机，当前容量规划为 8 vCPU、8 GiB 内存、初始化后至少 80 GiB 空闲磁盘；这是待实测的规划值，不是性能认证。每次只启动一个数据库产品及本阶段应用节点；Kafka/RabbitMQ 按用例另阶段运行，构建和测试错峰，复用主机和制品而不共用数据库内容。监测宿主与客体内存、OOM 和换页；容量不足时记录实测需求并与维护者确认调整，不自动增加内存或删减场景。测试主机不得挂载维护工作区、用户目录、QA/cluster 或旧发布数据，也不得获得生产网络和凭据。

备选方案是按数据库分开使用 GitHub 临时 runner。历史规划使用的私有仓库标准 Linux runner 规格为 2 CPU、8 GB 内存、14 GB SSD；重新选用前核对 [GitHub runner 规格](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)并实测容量，不能预先认定足以承担 Oracle 或双节点组合，不以增加超时或缩减场景替代资源核验。P2 独立入口采用手动触发，记录精确源码版本。

启动前必须形成环境清单：主机身份与生命周期、OS/架构、可用 CPU/内存/磁盘、Docker/Compose 版本、每种数据库的精确版本和镜像 digest、字符集/排序规则、数据库隔离配置、仅本轮使用的资源标签与根目录、端口表、秘密文件位置及日志脱敏策略。源码、JAR、静态文件、三镜像都绑定同一源码状态和哈希。运行秘密只在临时环境生成，不进入镜像、命令行日志或产物。

| 阶段 | 范围 | 验收与隔离要求 |
| --- | --- | --- |
| A | SQLite 单机运行冒烟、nginx、Agent | 实际启动和 readiness；登录、同源 API、SSE/上传；有效 token 成功、缺失/错误 token 被拒绝；重建保留本轮数据。SQLite 不组双节点 |
| B | MySQL、PostgreSQL、SQL Server、Oracle 单机与迁移 | 每种数据库独立临时实例；正式 V1 初始化与重复启动；历史升级/离线转换用另一组专用目标，验证版本、数据与审核边界，不能把旧链直接用于正式启动 |
| C | 四种服务数据库双节点 | A 就绪后启动 B；共享库和一致密钥、独立节点数据卷；跨节点登录、并发任务、单节点停止/故障接管、重加入、代理故障处理。记录实际覆盖的在途任务场景，不宣称同宿主等于物理 HA |
| D | 消息源与恢复 | 独立 Kafka/RabbitMQ 服务，显式执行两项真实消息源测试；故障/恢复仅作用本轮资源。磁盘、主库故障和恢复演练逐项记录，不沿用旧包证据 |

数据库专项必须显式选择类名和目标，禁止直接启用 `mysql-it` 后运行全部 IT：

- 四库 Provisioner 会 DROP/重建测试目标，名称正则并非数据安全证明；先核对独立数据库实例身份，再允许运行。
- `MysqlV19UpgradeIT` 使用固定 `ijob_it_upgrade_test`；Oracle Provisioner 固定 `IJOB_IT_ORA_PRIMARY/CLUSTER`；ID/log Oracle fixture 固定 `IJOB_ID_LOG_261008`。这些测试只能运行在本轮新建的数据库实例中，同类测试串行。
- `SecurityFoundationV27IT`、`RuntimeGovernanceV26IT` 的服务数据库路径包含固定回环端口/Oracle 服务名。使用独立执行环境或先适配显式地址，绝不直接运行到维护电脑的现有服务。
- `ParameterTemplateMigrationIT`、`TableIdServerMigrationIT` 按 vendor/目标建立矩阵。`RealCrossDatabaseMigrationIT` 当前验证 PostgreSQL 历史 V27 到 SQLite V27，源库必须由本轮 fixture 生成；不把它解释成当前版本跨库一键迁移。
- SQL Server 核对 RCSI OFF；Oracle 明确 service/schema、所需 DBMS_LOCK 权限及发行版差异。数据库镜像的获取与使用条件另行核定，不擅自接受额外条款。

清理前比对本轮资源清单、Compose project 和标签。只能删除本轮明确拥有的对象，禁止全局 prune；失败证据先保存。停止和清理均要核验结果，旧证据不覆盖。

2026-10-20 本轮功能矩阵已完成，使用官方 Ubuntu 24.04 原始镜像建立的新 8 GiB VM，未复制旧 VM 或业务数据。以下是实际验证版本，不代表其他版本也已验收；精确镜像 digest、实例身份、每次成功及失败报告保存在对应环境清单。

| 数据库 | 实际版本与关键配置 | 本轮范围 |
| --- | --- | --- |
| SQLite | 3.49.1，JDBC 3.49.1.0 | 单机、Agent 鉴权、持久化、独立备份恢复及受限文件系统磁盘满恢复 |
| MySQL | 8.4.11 Community，utf8mb4_bin，REPEATABLE READ | 正式 V1、历史升级、离线转换、单机/双节点、故障接管、重加入、主库中断及备份恢复 |
| PostgreSQL | 18.6，UTF8 / C，READ COMMITTED | 正式 V1、历史升级、离线转换、单机/双节点、故障接管与重加入 |
| SQL Server | 2022 Developer 16.0.4305.5，实例 SQL_Latin1_General_CP1_CI_AS，应用库 Latin1_General_100_BIN2，RCSI OFF | 正式 V1、历史升级、离线转换、单机/双节点、故障接管与重加入 |
| Oracle | AI Database 26ai Free 23.26.3.0.0，FREEPDB1，AL32UTF8 / AL16UTF16 | 正式 V1、历史升级、离线转换、单机/双节点、故障接管与重加入、ID/log 专项 |

四种服务数据库专项共 185 次 JUnit 执行，181 通过、4 项明确的 vendor 条件跳过；跳过项是 MySQL 2 项、SQL Server/Oracle 各 1 项，不计为通过。迁移修正专项另有 26 次执行全通过，包括真实 PostgreSQL 历史 V27 到 SQLite V27；独立 Kafka/RabbitMQ 真实消息专项 2 项均通过，补齐默认回归中未启动消息服务而跳过的两项。计数表示这些选定执行的合计，不宣称是互不重复的测试案例数量。

离线转换比较列名与逻辑类型，不依赖物理列顺序，仍要求列集合和主键顺序一致；四库到正式 SQLite 的转换均保留数据和 ID 水位，并维持 REVIEW_REQUIRED，未审核目标被实际应用拒绝启动。历史升级测试单独验证历史结构及正式启动的拒绝边界，不修改冻结 SQL 使旧库伪装成正式 V1。

四库在途故障均以可重试、幂等 GET 任务验证 SIGKILL 接管及更高 fencing token；执行器是至少一次语义，不代表任意外部写入 exactly-once。nginx 的 POST 不重试和 GET 故障重试另有代理专项。SSE 即时传输、2 MiB 上传完整性及 65 MiB 拒绝使用实际 nginx 和合成后端验证，只证明代理传输行为，不冒充产品 SSE 接口验收。MySQL 主库中断后节点因心跳丢失进入保护状态，恢复库后按 A 就绪、再 B 的顺序受控重启；不宣称自动解除保护。SQLite 磁盘满仅填满本轮独立 64 MiB 文件系统，原应用卷不参与破坏性演练。

SQL Server 曾以 BIN2 实例排序规则初始化，原生 sp_getapplock DLL 加载失败；失败实例卷及日志保留，重新建立独立实例后采用上表配置通过。不能把应用库 BIN2 的验证推广为任意实例排序规则。所有数据库串行运行；8 GiB 在本轮组合下未要求扩容，采样范围和内存/OOM 原始值写入环境报告，不构成生产容量或性能认证。最终归档源码、镜像及四 JAR 的提交/哈希绑定和冒烟报告必须与本轮矩阵证据一并保留。

## P3：依赖与许可清点

使用固定 CycloneDX Maven 插件 2.9.1 分别生成主工程和 Agent 的 aggregate BOM；使用锁定 pnpm 导出 `licenses list --json` 和 `licenses list --prod --json`。把四份输出分别命名为 `ijob-bom.json`、`ijob-monitor-agent-bom.json`、`frontend-licenses.json`、`frontend-production-licenses.json`，放入同一新证据目录，然后运行：

```powershell
python tools/ci/inventory-dependencies.py --input test/reports/DEPENDENCY_RUN --output test/reports/DEPENDENCY_RUN/inventory
```

清点工具无网络调用，记录来源哈希、组件坐标/版本/声明许可、前端本地许可文件哈希，以及后端/Agent/setup/migration JAR 中实际嵌套库和 LICENSE/NOTICE 文件哈希。输出已存在时拒绝覆盖。

2026-10-18 的初步清点为主工程 166 个、Agent 39 个第三方组件，合并去重 170 个；前端当前 Windows 安装集 272 个，其中生产依赖 102 个。另核对了 Python fixture 工具链的 9 个发行包。Maven/pnpm 这些清点记录均有声明许可，但实际四个可执行 JAR 都额外包含 `spring-boot-jarmode-tools-3.5.16.jar`，它没有进入 aggregate BOM；已记录嵌套文件及其 LICENSE/NOTICE 哈希，最终制品 SBOM 必须补齐这个构建注入项。

声明许可的清点不等于再分发签收。后续重点核对 MySQL Connector/J 的 GPL/FOSS Exception、Oracle JDBC/NLS 的 FUTC、多许可组件的选用方式、NOTICE/版权文本与自有素材来源；仍保持私有仓库。编译/运行 BOM 不包含完整测试工具链，当前 Windows pnpm 安装清单也不等于 Linux 可选包及最终 bundle 的精确组成。最终候选还须补齐镜像 OS 层、工具链、漏洞适用性与处置、第三方告知文件，并将清单绑定最终制品重新验收。


2026-10-20 自有源码 MIT 文件头、根 LICENSE、13 份包内 LICENSE 及元数据已落地。最终 JAR 清点补齐构建注入项；前端按实际输出及保守样式输入生成 49 个组件的告知。四个品牌图片按维护者的 ChatGPT 来源声明登记哈希，具体生成日期、模型未记录。构建/测试工具链和运行镜像的 OS 层分别生成清单，不混为产品依赖。

当前仍有公开再分发门禁：vdirs 0.1.8 缺可追溯的许可全文；MySQL Connector/J 与 Oracle JDBC/NLS 的组合分发条件尚未签收。漏洞记录保留原始发现和适用性：前端 npm 审计本轮零已知发现；运行镜像中未修复 OS 项和 Spring 条件性不适用判断不能表述为零漏洞。固定构建镜像中亦存在 HIGH/CRITICAL 发现，工具链更新和复核是后续发布前待办。当前交付范围是私有源码同步与测试准备，不包含公开仓库、发布镜像或完整再分发批准。

## 2026-10-26 服务就绪复验

本轮使用新隔离环境重新验证原生服务、Docker 恢复、外部插件、事件审批、子作业版本、调度补发、共享并发与独立备份恢复；各案例页只声明实际覆盖范围。2026-10-20 的五库矩阵属于历史证据，不自动计为本轮候选复验。独立 Windows VM 实测已于 2026-10-27 完成，范围和证据边界见下节。

本轮主工程基础回归 1663 次执行，1661 通过、真实消息源 2 项因条件未启用而跳过；Agent 30 项全部通过。另在本轮专属 Kafka/RabbitMQ 上显式运行真实消息源测试，两项均通过、零跳过；默认回归报告中的跳过记录保留。前端升级前的完整验证包括单元 429、普通浏览器 548、生产构建浏览器 147，以及七套真实后端配置共 9 项，全部通过；安全依赖升级后的复验单独留证，不以旧结果代替。

依赖扫描发现 Vite、Vitest mocker、Happy DOM 的九条开发依赖告警，已分别更新到 Vite 8.3.3、Vitest 5.0.3、Happy DOM 21.0.4，锁文件同步；升级后 pnpm audit 零已知告警。产品版本仍为 1.0.0。后端/Agent 的 OSV 原始报告仍命中 Spring Framework 6.2.19 两条条件性告警：[SSE 视图片段](https://spring.io/security/cve-2026-47890/)与 [XsltView](https://spring.io/security/cve-2026-47884/)。当前 36 个控制器均使用 RestController，主源码没有对应视图/SSE API 或视图解析器配置，按官方触发条件判定当前实现不适用；保留发现，新增相关功能时须重新评估，不宣称后端零漏洞。官方列出的 6.2.20 修复需企业支持，本轮未直接跨主版本替换框架。

新增事件、审批、S3 传输的六张双语图片来自本轮真实 API，已核对实例身份、实例树和详情一致性。原始图片、来源实例与 SHA-256 存放环境证据目录；手册图片仅用于说明已验证的路径。公开再分发所需的许可签收及镜像 OS 漏洞处置仍遵循上文门禁。

## 2026-10-27 验收汇总

| 范围 | 已有证据 | 边界 |
| --- | --- | --- |
| Linux / Docker | 独立 VM 的服务生命周期、恢复、插件、复合流程、备份恢复和 Full 复验；Native07 / Docker05 绑定基线 cae0b7a | 原始证据保留，不改写为 Windows 修复后重新执行 |
| Windows Server 2022 Standard Evaluation Core | Native09 的双 SQLite 实例、虚拟服务账号和文件隔离、停止及子进程清理、异常恢复、restart、卸载重装、两次真实重启的自启切换；独立 PostgreSQL 18.6 SQL 任务及正式 V1 | 未实测 Windows MQ、MySQL、SQL Server、Oracle；数据库服务依赖由该环境显式登记 |
| 程序回退 | Windows Native09 → Native07 → Native09 保留配置、秘密和历史 | 编译业务代码与 V1 相同，不代表数据库版本迁移；旧包保留已有 ACL，并由 SCM 等待停止 |
| 本地软件缓存 | 199 个文件/资料条目、7 个固定上游镜像；含 Windows ISO 与 PostgreSQL 安装包，约 8.45 GB | 软件和依赖可共享；账号、密钥、数据库、配置和数据目录仍独立创建；缓存数量为本次快照 |

Windows 实测修复了初始化文件禁用 ACL 继承后服务账号缺少访问权，以及 WinSW 停止请求异步返回导致的过早状态判断。修复后的分发测试 32 项、前端单元测试 429 项通过；该候选构建跳过后端测试，未重跑浏览器完整套件。失败尝试及脚本兼容问题的接续记录均保留，不能把原失败报告改为通过。[服务说明](../zh-CN/installation/services.md)及其英文版描述实际行为。

原始报告、候选哈希、源码差异和环境状态由维护工作区的统一验收索引串联，不入源码库。Native09 实测后的源码差异仅为验收文档与测试文件原有换行恢复；部署脚本保持字节一致。各次 CI 必须另按提交号和 run ID 记录，历史 CI 不替代新提交的结果。已有交付包不会随提交自动更新；最终统一重建候选属于后续阶段。

Linux 与 Windows VM 均已关机，自有服务停止、临时验收任务及转发通道关闭；数据、账号、备份及失败证据保留。后续新 VM 先核验本地缓存，缺少的固定版本再下载，不克隆已有验收数据环境。
