# 节点角色、分组与允许范围

[English](../../en/configuration/routing.md) · [手册首页](../README.md)

## 调整位置与匹配顺序

平台节点的值写入 application.properties 并重启；任务/作业的分组在定义中维护并按发布语义生效。节点名用于追踪，各节点应不同。角色 API/SCHEDULER/WORKER/MAINTENANCE 是职责分工；领取执行需要 WORKER，拆分部署仍须有人承担其他必要职责。

```properties
app.runtime.instance-name=worker-finance-a
app.runtime.groups=finance,nightly
app.runtime.allowed-task-ids=101,102
app.runtime.allowed-job-ids=201
```

以上 ID 仅示意，改为本环境实际 ID。分组统一去首尾空格、转小写、去重；最多 16 个，每个 1–64 字符，匹配 `[a-z0-9][a-z0-9._-]{0,63}`。ID 为正整数，每个列表最多 100 个去重值。

| 条件 | 匹配结果 |
| --- | --- |
| 定义没有分组 | 不限制节点分组 |
| 定义有分组 | 与节点分组至少一个交集 |
| 节点没有分组 | 只能匹配没有分组限制的定义 |
| 两个 ID 白名单都空 | 不限制根触发目标 ID |
| 仅任务列表非空 | 只接受所列独立任务根；不接受作业根 |
| 仅作业列表非空 | 只接受所列作业根；不接受独立任务根 |
| 两个列表都非空 | 按根目标类型匹配对应列表，再同时满足分组 |

这是领取范围，不是登录权限或脚本安全沙箱，也不是对作业内每个后代任务逐一授权。验证时分别创建一个匹配和一个不匹配的无副作用任务，观察触发/排队与执行节点；不能只看保存成功。修改前确保原有排队工作仍有可接手 Worker。

## 本主题配置索引

下列为源码/实现默认值；初始化覆盖、来源和完整说明见[完整清单](../configuration-catalog.md)。

| ID / 配置键 | 级别 / 类型 | 默认值来源 | 说明 |
| --- | --- | --- | --- |
| C036 / `app.runtime.instance-name` | A / text | ${IJOB_RUNTIME_INSTANCE_NAME:} | 节点标识；集群各节点取不同名称，便于追踪运行。 |
| C037 / `app.runtime.roles` | B / enum-list | ${IJOB_RUNTIME_ROLES:API,SCHEDULER,WORKER,MAINTENANCE} | API/SCHEDULER/WORKER/MAINTENANCE 职责；部署前确保所需角色仍有人承担。 |
| C038 / `app.runtime.groups` | A / text-list | ${IJOB_RUNTIME_GROUPS:} | 节点执行分组；与发布定义的分组匹配，空节点分组并不表示能接所有分组。 |
| C039 / `app.runtime.allowed-task-ids` | A / id-list | ${IJOB_RUNTIME_ALLOWED_TASK_IDS:} | 允许领取的根触发目标任务 ID；与作业白名单共同限制范围，见组合规则。 |
| C040 / `app.runtime.allowed-job-ids` | A / id-list | ${IJOB_RUNTIME_ALLOWED_JOB_IDS:} | 允许领取的根触发目标作业 ID；不等价于对子作业内每个任务逐一授权。 |
