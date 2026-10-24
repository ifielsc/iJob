# 文档目录与实施范围（已确认）

[English](../en/documentation-plan.md) · [完整配置清单](configuration-catalog.md) · [当前手册](README.md)

本页记录已确认的 1.0.0 文档目录、分级和维护原则。正文按此结构组织，检查器覆盖 54 项；完整配置索引保留 249 项。截图使用新登记的独立环境，GitHub 保持私密，现有演示环境不作部署或清理。

## 1. 阅读路线

| 读者目标 | 推荐路线 | 完成标准 |
| --- | --- | --- |
| 第一次安装 | 安装方式选择 → 对应平台安装 → 快速入门 | 独立完成初始化、启动登录、停止和再次启动 |
| 日常业务配置 | 快速入门 → 系统操作手册 → 对应案例 | 能配置、发布、执行、观察结果并处理常见失败 |
| 配置部署节点 | 配置参考 → 分组/角色/白名单 → 容量与运维 | 知道在哪里改、是否重启、如何检查作用范围 |
| 管理账号和部署模式 | 运维账号篇 / 单机集群篇 | 按具体流程完成变更和验证，不混淆两类模式 |
| 修改源码或准备发布 | 开发与发布验证 | 能分清构建成功、测试通过与运行验收通过 |

## 2. 已确认目录

中文 `document/zh-CN/` 与英文 `document/en/` 保持相同相对路径。下面只展示一棵树；文件名和代码示例不翻译，页面标题、说明和截图语言对应翻译。

```text
README.md                         文档导航、角色阅读路线、术语
installation.md                   安装入口：完整包 / 源码构建
installation/
  source-build.md                  工具准备、native/docker 构建、输出与失败定位
  windows.md                      Windows 首次与日常启停
  linux.md                        Linux 首次与日常启停
  docker.md                       Linux Docker、离线镜像、重建与查看状态
quickstart.md                     第一个任务、作业、运行结果、调度预览
configuration.md                  配置入口：分级、位置、覆盖、生效与检查
configuration/
  common.md                       网络、HTTP、语言、日志等常用项
  routing.md                      节点名称、角色、分组与任务/作业范围
  plugins.md                      11 种插件开关及 Local JAR 部署参数
  scheduling-retention.md          调度、事件、通知与保留策略
  capacity.md                     线程池、在途运行、连接池、关闭时限
  deployment-agent.md              启动变量、Compose、独立 Agent
  controlled.md                    账号/密钥/数据库/模式的受控项及运维链接
  internals.md                     内部协调与框架参数附录
user-guide/
  README.md                       操作手册入口和页面地图
  workspace.md                    登录、语言/主题/时区、工作台与节点
  tasks.md                        任务公共配置、版本、启停与执行
  plugins/README.md               插件选型与共同约定
  plugins/script.md               本地/SSH 脚本
  plugins/sql.md                  SQL
  plugins/api-call.md             接口调用
  plugins/event.md                事件
  plugins/data-check.md           数据校验
  plugins/file-transfer.md        文件传输
  plugins/push.md                 推送
  plugins/etl.md                  ETL
  plugins/batch-etl.md            批量 ETL
  plugins/server-monitor.md       服务器监控
  plugins/local-jar.md            Local JAR
  jobs.md                         作业公共配置、发布及版本引用
  job-nodes.md                    节点、边、条件、失败策略、网关与子作业
  parameters.md                   参数模板、输入与变量引用
  schedules.md                    调度配置、预览、启停和补发
  calendars.md                    日历规则与例外
  runs.md                         触发、实例、状态、日志、拓扑、停止与重试
  resources.md                    资源索引、秘密字段共同规则
  resources-database.md           数据库资源
  resources-ssh.md                SSH 资源与主机指纹
  resources-service.md            服务资源与认证
  resources-file.md               LOCAL/FTP/SFTP/S3 文件资源
  resources-messaging.md          Kafka/RabbitMQ 资源
  resources-monitor.md            监控服务器与 Agent 连接
  events.md                       事件审批与处理记录
  notifications.md                邮件通道、通知规则、模板与发送记录
  concurrency.md                  业务并发控制
  diagnostics.md                  运行诊断与健康信息
  users-audits.md                 用户、权限和审计
  import-export.md                导出、映射、差异、导入与恢复处理
examples.md                       案例索引：目标、依赖、难度、验证状态
examples/
  script-lifecycle.md             成功、失败、超时、停止和重试
  remote-script.md                SSH 远程执行与执行节点路径
  api-and-parameters.md           HTTP、认证、结果提取和参数传递
  sql-and-validation.md           SQL 查询/写入与 DATA_CHECK
  file-transfer.md                本地、SFTP、S3 与覆盖策略
  etl-full-incremental.md         全量/增量 ETL、检查点和结果核对
  batch-etl.md                    多表选择与映射
  dag-control-flow.md             串行、并行、汇合、条件分支及失败处理
  subjobs-and-versions.md         子作业及发布版本引用
  schedules-and-calendars.md      时区、工作日、错过触发与补发
  events-and-approval.md          事件等待、审批及恢复
  push-and-notifications.md       消息推送与运行结果通知
  monitoring.md                   监控采集与异常结果
  local-jar.md                    可信插件部署、调用与结果
  routing-and-concurrency.md      节点分组、白名单与业务并发
  environment-transfer.md         两个隔离环境间配置迁移
operations.md                     运维入口与变更分类
operations/
  daily.md                        日常检查、日志、维护窗口
  accounts.md                     STATIC/DATABASE、账号日常管理
  account-mode-change.md          引导首个账号、切换、改密与异常处理
  standalone-cluster.md           运行模式、数据库要求、节点分工
  runtime-mode-change.md          单机/集群变更与回退条件
  backup-restore.md               SQLite/服务数据库备份和独立恢复
  upgrade-rollback.md             包、配置、数据与版本一致性
  production.md                   TLS、服务托管、监控、容量和入口要求
  troubleshooting.md              按现象查原因、证据与恢复
development.md                    开发与发布验证入口
development/
  quality-gates.md                构建/测试/运行验收分别证明什么
  documentation.md                双语、配置清单、截图和链接维护
  release-checklist.md            制品来源、检查结果和私密发布边界
images/                           对应语言的真实截图，按章节分类
```

`configuration-catalog.md` 与本页是范围索引。配置内容按上面的主题拆分，保留完整索引和可追踪条目 ID，避免 249 条挤在日常配置首页。共享机器清单 `document/reference/configuration-review.json` 保存双语定义，避免分别维护两套配置事实。

## 3. 各类页面的层次与篇幅规则

| 页面类型 | 固定层次 | 截图/示意的用途 |
| --- | --- | --- |
| 平台安装 | 前置条件 → 解压与目录 → 首次初始化 → 启动访问 → 状态 → 日志 → 停止 → 改配置重启 → 再次启动 → 常见问题 | 命令保持可复制；只在初始交互难理解时补终端示例 |
| 快速入门 | 前提 → 创建 → 发布 → 执行 → 查看结果 → 两节点作业 → 调度预览 → 完成检查 | 约 4–5 张关键真实界面，不堆满步骤截图 |
| 页面操作手册 | 页面用途/权限 → 入口 → 常用路径 → 字段分组 → 动作影响 → 结果解释 → 常见问题 → 关联案例 | 复杂表单与结果页；字段表补充默认、必填、条件显示和作用范围 |
| 每种插件 | 适用场景 → 外部依赖/执行位置 → 字段 → 最小配置 → 输出/变量 → 错误与重试 → 案例 | 关键配置与结果各按需要配图，不靠截图承载可复制配置 |
| 场景案例 | 目标 → 前置资源 → 样例数据 → 配置 → 运行 → 核对业务结果 → 常见错误 → 结束处理 | 简单案例可无图；复杂案例约 1–3 张，突出配置和结果差异 |
| 受控运维流程 | 适用情形 → 检查/备份 → 操作顺序 → 成功判据 → 异常处理 → 恢复/回退 | 拓扑或流程关系可用简图；实际页面使用真实截图 |
| 配置参考 | 键/类型 → 默认值来源 → 调整建议 → 示例 → 联动条件 → 生效 → 验证 | 配置文本优先；分组与作用关系使用小表或简图 |

不反复复制相同知识：插件表单字段在操作手册解释，案例链接到它并提供具体样例；运维说明模式切换，配置参考链接到对应流程。术语、命令、版本、枚举与路径相互一致。

## 4. 需要同步修正的已确认问题

| 编号 | 当前问题 | 正文阶段的修改目标 |
| --- | --- | --- |
| F01 | build.py 与 pnpm check/-DskipTests 的关系不清 | 明确后两者是脚本内部步骤；安装者无需再构建一次；完整质量检查面向开发/发布 |
| F02 | Windows 命令块容易让人以为每次必须 initialize 后 start | 说明 start 会调用初始化；initialize 是只初始化不启动；首次和日常分别展示 |
| F03 | 配置页 Docker 重建命令与镜像锁入口不一致 | 统一使用对应交付包 start-docker.sh --recreate，并解释状态/日志/停止 |
| F04 | 快速入门/案例称脚本 stdout 不记录，与实现和演练证据不符 | 按真实输出路径、限制和页面位置修正，并以真实运行截图证明 |
| F05 | 21 项配置缺少路由、插件、保留周期、会话及 Agent 等 | 按完整清单分级展示；检查器扩展范围单独标明，避免文档超前宣称支持 |
| F06 | 多账号、集群和生产入口混在一节 | 认证模式与运行模式拆开，分别给前提、步骤、验证与失败处理 |
| F07 | 案例只有操作概述，缺少字段取值和可核验结果 | 按统一模板给出样例输入、操作、输出与业务核对步骤 |

## 5. 页面字段与场景覆盖要求

正文阶段为每页建立“字段/动作 → 说明 → 示例 → 验证证据”对照。任务公共字段、全部 11 个插件、作业及节点详细配置是首批重点。每种插件除成功路径外，还要说明不适用的执行位置、必要资源、超时/重试和输出限制。

作业必须解释发布时绑定的版本、参数来源、执行条件、失败策略、网关默认分支/优先级、子作业和运行拓扑。运行中心必须区分触发受理与实际执行，说明保存、发布、启用、停止平台、停止实例各自的效果；不能仅凭页面按钮名称推断语义。

案例中的外部数据库、SSH、对象存储、消息队列及 Agent 要逐个登记并在独立环境准备。标记“已实测 / 仅配置示例 / 待验证”，不把前一轮 SQLite 演练当作所有外部插件已验证。案例数量按上面 16 组组织，各组可包含多个变体，不承诺用一张截图证明所有变体。

## 6. 双语与截图计划

| 截图组 | 计划画面 | 数据与验收要求 |
| --- | --- | --- |
| Q01–Q05 快速入门 | SCRIPT 配置、成功实例/日志、两节点编辑图、运行拓扑、调度预览 | 对应同一组真实示例；保留关键执行与日志证据 |
| U 页面操作 | 复杂插件表单、作业节点/网关、参数、调度、资源与账号 | 每张图只解释一个重点，标明操作位置与预期效果 |
| E 场景案例 | ETL 映射/结果、分支命中、导入差异、审批或通知预览等 | 与真实案例步骤一致；不能把预览写成已发送、把模拟响应写成真实服务结果 |

- 先登记新的截图环境、账号、数据目录、端口与外部服务，再配置和运行所需案例。既有演示环境不用于采集或测试。
- 中文与英文截图分别切换真实界面语言，使用同一份合成样例和同一版本；主截图统一浅色与尺寸，深色仅在说明主题功能时展示。
- 先完成明确需要验证的样例运行，再截图展示结果；不为取得漂亮画面额外发送通知、重复执行任务或操作真实业务。
- 不展示口令、令牌、完整私有配置或真实业务数据。截图不伪造成功状态；必要的编号/区域说明放在图注中，界面文字保留真实内容。
- 每张截图维护语言、页面、步骤、源码版本和证据引用。命令/脚本/SQL/配置始终另外提供可复制文本与替代文字说明。
- 中文完成一个可审阅单元即同步英文；核对目录、配置键、默认值、枚举、样例数据、图片和链接，不等全部中文写完才开始翻译。

## 7. 已确认决策与验收

目录采用 Markdown，暂不引入文档站；A/B 为常用与高级，C 走受控流程，D 保留附录。先安装/配置/运维，再页面/插件/案例，最后核对双语、真实截图和完整包。

后续外部协议和扩展案例验证范围见[案例索引](examples.md)，交付检查及后续优先级见[发布清单](development/release-checklist.md)。截图不能替代每种协议的端到端证据。
