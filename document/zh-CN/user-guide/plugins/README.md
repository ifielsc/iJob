# 插件选型与共同约定

[English](../../../en/user-guide/plugins/README.md) · [手册首页](../../README.md)

先按业务动作选插件，再准备资源。插件开关、定义启用、手动执行和自动调度是不同层；保存任务发布版本，不自动执行。所有文件/解释器位置属于执行 Worker 或明确的远端资源。

| 插件 | 详细说明 |
| --- | --- |
| SCRIPT | [脚本](script.md) |
| SQL | [SQL 执行](sql.md) |
| API_CALL | [接口调用](api-call.md) |
| EVENT | [事件发布、等待与审批](event.md) |
| DATA_CHECK | [数据校验](data-check.md) |
| FILE_TRANSFER | [文件传输与归档](file-transfer.md) |
| PUSH | [数据库结果推送到消息队列](push.md) |
| ETL | [ETL：源、转换、目标与增量](etl.md) |
| BATCH_ETL | [批量 ETL](batch-etl.md) |
| SERVER_MONITOR | [服务器监控](server-monitor.md) |
| LOCAL_JAR | [可信 Local JAR 插件](local-jar.md) |

超时/停止/重试不会回滚外部副作用；写入型插件先规划幂等和业务核对。实例日志、变量、行数或消费确认分别证明不同事实。
