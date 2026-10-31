# 术语表

[English](../en/glossary.md) · [手册首页](README.md)

本表适用于 1.0.0。先阅读[核心概念](concepts.md)了解关系，再用本表快速查词。

| 中文术语 | 英文 / 技术名称 | 含义与易混点 |
| --- | --- | --- |
| 任务 | Task | 由一种插件执行的动作；不是整个工作流 |
| 作业 / 工作流 | Job / Workflow | 节点与依赖组成的流程；本文两种称呼指同一编排对象 |
| 子作业 | Subjob | 被父作业引用的作业版本；不是第十二种任务插件 |
| 节点 | Job node | 编排图中的任务、子作业或网关；不是服务器节点 |
| 运行节点 | Runtime node | 提供 API、调度、执行或维护职责的进程 |
| 网关 | Gateway | 决定依赖/条件分支如何推进 |
| 定义版本 | Definition version | 发布时固定的配置快照，不等于产品 1.0.0 |
| 发布 | Publish | 形成定义版本；任务保存会发布，不自动执行 |
| 触发 | Trigger | 执行请求记录，不等于实例已成功 |
| 实例 | Instance | 一次执行的身份和状态 |
| 根实例 | Root instance | 用于关联整个流程的根执行记录 |
| 调度 | Schedule | 产生计划触发的规则，涉及时区、日历和错过处理 |
| 业务日期 | Business date | 该次触发的业务语义日期，不一定等于机器当前日期 |
| 参数模板 | Parameter template | 输入类型、默认值和校验定义；不是参数值 |
| 输入快照 | Input snapshot | 本次触发/运行保存的输入 |
| 输出变量 | Output variable | 插件产生并可供后续节点使用的值 |
| 系统变量 | Built-in variable | 平台提供的 ijob.* 上下文；用户不能覆盖该前缀 |
| 资源 | Resource | 业务系统连接与访问配置 |
| 平台数据库 | Platform database | 保存定义、历史、配置及协调状态的库 |
| 业务数据库 | Business database | SQL、ETL 等插件访问的目标或来源库 |
| 执行分组 | Execution group | 任务与 Worker 的路由匹配条件，不是账号角色 |
| 并发策略 | Concurrency policy | 限制相关执行同时占用的能力，不改变定义版本 |
| 检查点 / 水位 | Checkpoint / watermark | ETL 增量读取进度；不能代替目标端幂等 |
| 幂等 | Idempotency | 同一业务操作重复执行不会产生重复业务效果 |
| 重试 | Retry | 再次执行；不是对前一次外部副作用的自动回滚 |
| 审批 | Approval | EVENT 等待动作，由允许的账号决定是否继续 |
| 节点就绪 | Readiness | 节点可接受相应工作，不代表所有业务资源均健康 |

## 相关参考

[变量与表达式](reference/variables.md) · [节点与网关](user-guide/job-nodes.md) · [调度](user-guide/schedules.md) · [账号权限](user-guide/users-audits.md)
