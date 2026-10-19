# 基础 CI

工作流为 [ci.yml](../../.github/workflows/ci.yml)，覆盖 main 推送、指向 main 的 PR 和手动运行。正式开发使用连接 origin 的独立 Git 工作目录，机器路径见维护工作区入口。CI 不连接维护电脑、原 develop、运行数据或 QA/cluster；不推送镜像、不启动容器、不部署。

## 工具链与任务

| 任务 | 环境与工具 | 检查范围 |
| --- | --- | --- |
| Repository checks | Ubuntu 24.04、Python 3.12 | Git 实际文件与逐项白名单相等；敏感信息扫描及已有审查记录 |
| Backend and Agent | Windows 2025、JDK 21、Maven 3.9.11、Python 3.12/Paramiko 4.0.0 | google-java-format 1.24.0 AOSP/既有 CRLF、quality 编译、单测、指定 SQLite/安全 IT、打包；先安装主工程，再测试 Agent |
| Frontend | Ubuntu 24.04、Node 24.14.1、pnpm 11.19.0、Playwright Chromium | runtime/typecheck/lint/boundaries/format/unit/build/bundle；普通 E2E 和 production 配置 |
| Docker validation and build | Ubuntu 24.04、Docker/Compose、Python 3.12 | 五库单机/四库双节点 Compose 模板语法；后端、前端、Agent 三镜像 Linux amd64 构建和镜像 ID |

Node 版本由 `.node-version` 定义并精确检查；pnpm 版本取 package.json。后端、Agent wrapper 和 Maven 构建镜像统一为 3.9.11。CI 下载 Maven 后校验发布方 SHA-512；Java 主版本为 21，不声称宿主 JDK 与 Docker 内补丁号完全一致。quality profile 打开编译警告，不将所有警告当成错误。

Action 固定完整提交 SHA；GITHUB_TOKEN 仅 contents:read，checkout 不持久化认证。相同分支/PR 的新运行取消旧运行。基础检查不需要仓库 Secrets、镜像仓库凭据或外部数据库账户。

## 本地入口

从仓库根目录运行。RunId 每次唯一，脚本拒绝覆盖旧证据。

```powershell
# JAVA_HOME/PATH 使用 JDK 21，PATH 中 Maven 必须为 3.9.11。
# 建议使用独立 Python 虚拟环境并将其 Scripts/bin 放在 PATH 前部。
python -m pip install -r tools/ci/requirements.txt
pwsh -File develop/api/ijob/tools/verify-backend.ps1 -RunId local-backend-001

# 先在 develop/web/ijob 安装 pnpm 锁定依赖和 Chromium。
# pnpm install --frozen-lockfile
# pnpm exec playwright install chromium
pwsh -File test/tools/verify-frontend.ps1 -RunId local-web-001 -Suite Basic

# Linux Docker 主机上执行；只校验和构建，无 up/push。
python tools/ci/verify-docker.py --run-id local-docker-001
```

后端首次缺少格式化 JAR 时从 Maven Central 获取；`-Offline` 仅使用已经准备好的缓存。`-ScanDependencies` 显式运行 CycloneDX/OSV，不能与 Offline 组合，当前基础工作流不启用它。依赖扫描失败或网络异常不能记为通过。

FTP/SFTP 单元测试使用已审查的 `tools/ci/fixtures` 回环内存服务，主机密钥每次生成，不读取业务文件。初次干净构建发现这两个 fixture 未随初始源码入库，本次补齐并去掉 Java 测试的机器专用 Python 路径；外层历史原件不改动。

基础检查同时发现 Windows 脚本取消后的临时文件句柄短暂占用问题：清理现对文件系统异常最多重试 1 秒，并保留线程中断状态。现有重复 20 次的取消回归保持断言不变；冻结交付包不随这处源码修复更新。云端还发现发送窗口 flush 与计数回调之间的竞态，现等待本次发送的计数回调完成再返回，并用受控线程回归验证。脚本编码测试显式设置 BAT UTF-8 代码页，以及 PowerShell UTF-16 脚本/UTF-8 输出，继续断言中文结果，避免依赖操作系统默认语言。

配置回归已对齐既定交付：ETL/批量 ETL 默认开启，STATIC 默认认证不变。私有 application-local.yml 原本就不入库，旧测试对其内容的依赖改为检查它未混入打包资源；CI 不生成或复制个人配置来满足测试。

前端默认 Suite 为 Basic。CI 将普通 E2E 和生产构建浏览器测试分成 4 片，每片在独立 runner 使用 1 个 worker；每片仍执行完整静态检查、单测和构建，Docker 等待四片全部通过。这样避免小规格私有仓库 runner 上四个浏览器争用资源；不放宽用例超时、不跳过测试。报告 selection.json 标明分片，统计单测时不能把四片重复执行误算为新增用例。本地默认不分片；可用 `-Shard 1/4 -BrowserWorkers 1` 单独复现某片。`-Suite Full` 在 Basic 后顺序运行 security、batch-etl、diagnostics、etl、localjar、monitor、workbench 七套真实后端集成，不能在同一主机同时运行这些固定端口配置。parameters 专项配置是默认 E2E 已包含的 parameters.spec.ts 的独立入口，不在基础流程中重复执行。

Full 尚未接入本次基础工作流。它需要 Windows（包含 BAT/PowerShell 场景）、同提交后端和 SPI JAR、JDK 21、Python/Paramiko、Git Shell、PostgreSQL initdb/pg_ctl/psql。使用 `IJOB_TEST_PYTHON`、`IJOB_TEST_SH`、`IJOB_TEST_PG_BIN` 显式指定工具；其他 JAR/目录覆盖见[前端说明](../../develop/web/ijob/README.md)。所有 fixture 必须在新的临时目录运行，不使用用户数据。工作台与诊断 fixture 已改用可配置 Python；未以本次 Basic 通过宣称 Full 已通过。

浏览器基线复验同时修复通知候选翻页后下拉框焦点丢失，并将工作台、生产通知记录空状态测试从过期 CSS 类改为可访问状态角色断言，同时对齐通知投递接口；没有放宽等待断言或跳过失败用例。云端 Windows 测试按真实路径比较 SQLite 目录，兼容 runner 的短路径别名；Linux 英文认证页在较矮视口的溢出通过收紧表单间距修复，仍保留小视口滚动和所有操作入口。

本地扩展集成入口、P2 隔离环境方案和 P3 清点方法见[隔离集成与发布准备](isolated-integration.md)。保持本工作流完整，不以专项结果替代 Basic。

## 后端 IT 分组与后续覆盖

基础入口明确选择 `*Test,RealSqliteMigrationIT,SqliteOfflineMigrationIT,SecurityHttpIT`，检查两个工程实际有测试执行，并要求三套 IT 有非跳过的成功用例。JUnit 汇总保留 failures/errors/skipped，跳过不会记为执行成功。Maven 的 `surefire.failIfNoSpecifiedTests=false` 仅容许不包含这些测试的子模块为空。

基础环境不提供 Kafka/RabbitMQ，因此 RealMessageSourcesTest 中两项由 IJOB_MQ_IT 控制的真实消息源检查允许跳过，并明确记录；其他 suite 出现跳过会使入口失败。消息源真实集成需后续独立接入，不能算在基础通过范围内。

以下 IT 不在基础任务内，按专用入口编排，不能直接把所有 `*IT` 接到基础任务：

- 本地 HTTP/SQLite 专项（使用 `tools/ci/verify-local-integration.ps1` 精确选择并要求零跳过）：ConcurrencyHttpIT、DataCheckHttpIT、DiagnosticsHttpIT、EtlFileIncrementalIT、FilesHttpIT、InstanceLogSnapshotIT、PushHttpIT、SecurityCompletionIT、Stage9cLifecycleHttpIT、TableIdMigrationIT、TriggerBusinessTimeMigrationIT、UnifiedExperienceHttpIT、WorkbenchHttpIT。
- 四种服务数据库：Mysql/Postgresql/SqlServer/OracleDatabaseProvisionerIT、RealMysql/RealPostgresql/RealSqlServer/RealOracleMigrationIT、MysqlV19UpgradeIT、OracleIdSegmentConcurrencyIT。
- 按 vendor/目标环境及历史数据编排：ParameterTemplateMigrationIT、TableIdServerMigrationIT、RuntimeGovernanceV26IT、SecurityFoundationV27IT、RealCrossDatabaseMigrationIT。

Provisioner 会重建符合命名约束的专用目标。某些专项会读取 IJOB_* 变量或因缺变量跳过；后续任务必须显式创建每次独立的库/schema、核对目标和测试结果，再做限定清理。mysql-it profile 选择所有 IT，不等于“只运行 MySQL”。Oracle 固定测试 schema 约束等仍需在五库阶段适配。不得配置现有 QA/cluster 或生产地址。

## Docker 与报告边界

Docker 等待后端/Agent 和前端通过后执行。校验 images.env 与 images.lock.json 一致，使用独立 Compose 项目名和 CI 镜像标签；临时空文件仅满足 Compose secret 路径检查，没有真实认证材料。模板校验不验证数据库连接，构建也不验证健康探针、实际 nginx 代理或 Agent 鉴权。上述运行验收及五库集成属于后续阶段。

CI runner 的证据位于 `test/reports/<RunId>`，包括命令日志、JUnit 和前端报告/哈希、Docker 构建日志与镜像 ID。本地后端、专项和前端入口使用 `-ReportRoot`、`-TemporaryRoot` 指向仓库外 `runtime/<env-id>/runs/<run-id>/`，详见[工作区规范](../operations/workspace-and-environments.md)。后端在 Maven 失败时仍收集已生成的 JUnit；工作流 always 上传已存在报告，保留 14 天。工具安装失败发生在检查入口之前时，以 Actions 步骤日志为准。报告不包含 .m2、node_modules、临时数据库、配置目录、密码文件或整个工作区。取消/runner 丢失时报告可能不完整，不能据此签收。

后续依次接入真实后端浏览器集成、容器运行冒烟、五库和双节点、SBOM/漏洞扫描及发布基线。本次基础 CI 不替代这些验收，也不改变冻结交付包。
