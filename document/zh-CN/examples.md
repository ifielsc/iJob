# 经典案例与验证范围

[English](../en/examples.md) · [手册首页](README.md)

案例分 16 组。先运行无外部副作用样例，再扩展读写、自动触发和恢复。截图来自独立真实实例；配置表单截图不代表外部投递、消费或所有协议都已验证。

| 案例 | 本轮状态 |
| --- | --- |
| [脚本成功、失败、停止与重试](examples/script-lifecycle.md) | 基础路径实测；扩展另验 |
| [SSH 远程脚本](examples/remote-script.md) | 隔离实测；详见各案例范围与待验项 |
| [HTTP、参数与结果提取](examples/api-and-parameters.md) | 隔离实测；详见各案例范围与待验项 |
| [SQL 与数据质量门禁](examples/sql-and-validation.md) | 基础路径实测；扩展另验 |
| [本地与远端文件传输](examples/file-transfer.md) | 隔离实测；详见各案例范围与待验项 |
| [全量、增量与 ETL 检查点](examples/etl-full-incremental.md) | 隔离实测；详见各案例范围与待验项 |
| [多表 ETL 与目标命名](examples/batch-etl.md) | 隔离实测；详见各案例范围与待验项 |
| [串行、并行、汇合与条件分支](examples/dag-control-flow.md) | 隔离实测；详见各案例范围与待验项 |
| [子作业与发布引用](examples/subjobs-and-versions.md) | 隔离实测；详见各案例范围与待验项 |
| [时区、工作日与错过补发](examples/schedules-and-calendars.md) | 隔离实测；详见各案例范围与待验项 |
| [事件等待、审批与恢复](examples/events-and-approval.md) | 隔离实测；详见各案例范围与待验项 |
| [业务消息与运行结果通知](examples/push-and-notifications.md) | 隔离实测；详见各案例范围与待验项 |
| [监控采集、阈值与恢复](examples/monitoring.md) | 隔离实测；详见各案例范围与待验项 |
| [可信扩展的部署与调用](examples/local-jar.md) | 隔离实测；详见各案例范围与待验项 |
| [节点路由与共享并发](examples/routing-and-concurrency.md) | 隔离实测；详见各案例范围与待验项 |
| [两个环境间配置迁移](examples/environment-transfer.md) | 隔离实测；详见各案例范围与待验项 |

本轮基础数据：orders 三行，金额 100/200/300；数据库样例使用独立 PostgreSQL，平台自身使用独立 SQLite。文件样例使用独立 LOCAL 目录。失败/停止是预期演练结果，不能统计为任务业务成功。
