# 核心概念与执行生命周期

[English](../en/concepts.md) · [手册首页](README.md)

先区分“定义要做什么”和“这一次实际做了什么”。iJob 1.0.0 的任务保存会发布版本，作业使用“保存并发布”；这两个动作都不会自动执行。

## 从配置到结果

<div class="lifecycle-model" role="group" aria-label="概念模型与执行生命周期"><div class="life-heading"><span class="life-tag">定义准备</span><strong>先定义要做什么</strong><small>保存 / 发布 ≠ 执行</small></div><div class="definition-lane"><div class="definition-card"><span class="life-index">01</span><h3>资源与参数</h3><small>连接配置 · 输入约定</small><p>为任务提供访问能力和输入。任务也可不引用外部资源。</p></div><div class="definition-card"><span class="life-index">02</span><h3>任务定义与版本</h3><small>配置插件 → 保存并发布</small><p>任务保存形成已发布版本，但不会因此自动执行。</p></div><div class="definition-card"><span class="life-index">03</span><h3>作业编排与发布</h3><small>依赖 · 网关 · 子作业</small><p>发布时固定所引用的任务和子作业版本。</p></div></div><div class="life-bridge"><span>↓</span>已发布定义 + 本次输入</div><div class="life-heading"><span class="life-tag">一次执行</span><strong>再追踪这一次做了什么</strong><small>点击阶段查看说明</small></div><div class="execution-lane"><button type="button" class="life-node" data-life-step="0" aria-pressed="true" data-title="记录一次执行请求" data-detail="触发记录保留来源、时间与输入。调度启用只是允许后续产生触发；触发已受理不代表已开始执行。"><span>01</span><strong>触发</strong><small>调度 / 手动 / 事件绑定</small></button><button type="button" class="life-node" data-life-step="1" aria-pressed="false" data-title="从定义，进入这一次运行" data-detail="满足执行条件后创建或推进实例。根实例关联任务节点和子实例；并发、路由及资源条件可能使它等待。"><span>02</span><strong>实例</strong><small>本次执行的身份与状态</small></button><button type="button" class="life-node" data-life-step="2" aria-pressed="false" data-title="按关系推进，而非一律顺序执行" data-detail="上游结果与网关条件决定后续路径。事件或审批可让已有实例等待，再继续原流程；失败策略决定下游处理。"><span>03</span><strong>节点执行</strong><small>依赖推进 / 分支 / 等待</small></button><button type="button" class="life-node" data-life-step="3" aria-pressed="false" data-title="把状态对应到具体动作" data-detail="日志、输入快照和节点输出贯穿运行过程，并非完成后才生成。可从实例拓扑定位，再检查节点日志与变量。"><span>04</span><strong>日志与变量</strong><small>贯穿整个执行过程</small></button><button type="button" class="life-node" data-life-step="4" aria-pressed="false" data-title="SUCCESS之后，仍需核对目标结果" data-detail="实例成功是平台按插件与流程规则判定的状态。还要确认目标行数、金额、文件或外部回执；停止和失败不会自动撤销外部写入。"><span>05</span><strong>业务核对</strong><small>文件 / 数据 / 外部回执</small></button></div><div class="life-detail" role="status"><strong>记录一次执行请求</strong><p>触发记录保留来源、时间与输入。调度启用只是允许后续产生触发；触发已受理不代表已开始执行。</p></div><div class="life-foot">资源按需引用 · 版本固定定义 · 日志贯穿运行 · 结果回到业务</div></div>

资源按需由任务引用；任务也可以没有外部资源。作业发布时固定引用的任务、子作业版本。一次触发记录来源、时间与输入；满足执行条件后创建或推进相应实例。调度、并发、路由、资源和实例状态都会影响实际执行。

## 概念之间的关系

| 概念 | 在 iJob 中的含义 | 例子 |
| --- | --- | --- |
| 资源 | 任务使用的连接及访问配置，不是任务本身 | 业务 PostgreSQL、SSH 主机、HTTP 服务 |
| 任务 | 选择一种插件并配置一次执行动作 | 查询订单、调用接口、执行脚本 |
| 作业 | 用节点、依赖、网关组织任务和子作业的流程 | 提取订单 → 检查数据 → 下游处理 |
| 子作业 | 被另一个作业节点引用的已发布作业 | 复用统一的数据检查流程 |
| 版本 | 发布后的定义快照；历史版本只读 | 父作业仍引用任务旧版本 |
| 发布 | 形成可执行或可被引用的定义版本 | 保存任务、保存并发布作业 |
| 启用 | 对象是否允许相应操作的开关；具体作用按对象说明 | 启用调度允许后续按计划产生触发 |
| 触发 | 一次执行请求及其来源、时间、输入记录 | 手动点击执行、计划时间到达 |
| 实例 | 一次执行的身份与状态记录 | 根实例及其节点/子实例 |
| 运行 | 实例从等待、执行到终态的过程 | 查看运行拓扑、停止或重试 |
| 参数模板 | 输入项的名称、类型、校验与默认值 | 必填 batchDate、整数 limit |
| 变量 | 输入、系统上下文及节点输出 | 本次 limit、实例 ID、上游 rows |

业务账号 ADMIN/OPERATOR/VIEWER 与节点角色 API/SCHEDULER/WORKER/MAINTENANCE 属于不同维度，参阅[权限](user-guide/users-audits.md)与[部署拓扑](operations/standalone-cluster.md)。

## 保存、发布、启用、触发、成功各代表什么

| 动作或状态 | 可以据此确认 | 还需要确认 |
| --- | --- | --- |
| 任务保存成功 | 新定义版本已发布 | 没有因此自动运行 |
| 作业保存并发布成功 | 发布事务选取并固定子对象当前版本 | 预检时看到的版本不等于已锁定版本 |
| 对象启用 | 对应开关已打开 | 调度到期、路由及其他条件是否满足 |
| 触发已受理 | 平台收到执行请求 | 是否生成实例、是否开始执行 |
| 实例 SUCCESS | 平台按插件/流程规则判定完成 | 目标系统数据和业务条件是否符合预期 |
| 停止或超时 | 平台已处理控制请求或期限 | 远端进程、外部写入是否仍在继续 |

## 一个版本引用例子

1. 保存任务 A，得到它的一个已发布版本。
2. 将 A 加入作业 B，保存并发布 B。
3. 修改 A 并保存，得到 A 的新版本。
4. B 的旧版本仍然引用原来的 A；它不会被 A 的更新改写。
5. 要让 B 使用新的 A，重新校验并发布 B，再检查发布引用。

产品版本 **1.0.0**、任务/作业定义版本、Local JAR 协议和数据库迁移编号不是同一套编号。

## 事件有两种进入流程的方式

- **事件绑定触发**：匹配事件后产生新的执行请求。
- **等待事件或审批**：已有实例进入等待，匹配事件或符合条件的审批后继续原流程。等待本身不是失败。

审批需要账号权限、允许操作人和可处理状态同时满足。停止、超时、重试也不自动撤销目标系统已经完成的写入。

## 接下来

[完成第一个任务](quickstart.md) · [参数与变量](user-guide/parameters.md) · [术语表](glossary.md) · [子作业与版本教程](examples/subjobs-and-versions.md)
