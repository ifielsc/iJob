# 插件开关与 Local JAR 部署

[English](../../en/configuration/plugins.md) · [手册首页](../README.md)

11 种插件均列于下表，默认除 Local JAR 外开启。开关在节点启动时生效，不是某条任务的启用按钮。关闭前检查已发布任务、作业引用、排队和运行中实例；所有目标 Worker 必须具备相应插件和依赖。

```properties
app.plugin.script.enabled=true
app.plugin.local-jar.enabled=false
```

Local JAR 仅运行可信代码。启用前准备执行节点插件目录，Docker 显式挂载；使用 Maven SPI 1.0.0、新命名空间 `com.ifielsc.ijob` 和协议 1。上传/选择制品后核对 SHA-256、入口类与协议。不能因协议同为 1 就直接复用旧命名空间 JAR。业务表单、最小配置和输出详见[插件手册](../user-guide/plugins/README.md)。

## 本主题配置索引

下列为源码/实现默认值；初始化覆盖、来源和完整说明见[完整清单](../configuration-catalog.md)。

| ID / 配置键 | 级别 / 类型 | 默认值来源 | 说明 |
| --- | --- | --- | --- |
| C125 / `app.plugin.event.enabled` | A / boolean | ${IJOB_PLUGIN_EVENT_ENABLED:true} | event 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| C126 / `app.plugin.script.enabled` | A / boolean | ${IJOB_PLUGIN_SCRIPT_ENABLED:true} | script 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| C127 / `app.plugin.sql.enabled` | A / boolean | ${IJOB_PLUGIN_SQL_ENABLED:true} | sql 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| C128 / `app.plugin.file-transfer.enabled` | A / boolean | ${IJOB_PLUGIN_FILE_TRANSFER_ENABLED:true} | file-transfer 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| C129 / `app.plugin.api-call.enabled` | A / boolean | ${IJOB_PLUGIN_API_CALL_ENABLED:true} | api-call 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| C130 / `app.plugin.push.enabled` | A / boolean | ${IJOB_PLUGIN_PUSH_ENABLED:true} | push 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| C131 / `app.plugin.data-check.enabled` | A / boolean | ${IJOB_PLUGIN_DATA_CHECK_ENABLED:true} | data-check 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| C132 / `app.plugin.server-monitor.enabled` | A / boolean | ${IJOB_PLUGIN_SERVER_MONITOR_ENABLED:true} | server-monitor 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| C133 / `app.plugin.etl.enabled` | A / boolean | ${IJOB_PLUGIN_ETL_ENABLED:true} | etl 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| C134 / `app.plugin.batch-etl.enabled` | A / boolean | ${IJOB_PLUGIN_BATCH_ETL_ENABLED:true} | batch-etl 插件节点级开关；关闭前检查已发布任务和运行中工作。 |
| C135 / `app.plugin.local-jar.enabled` | A / boolean | ${IJOB_PLUGIN_LOCAL_JAR_ENABLED:false} | local-jar 插件节点级开关；关闭前检查已发布任务和运行中工作。 另需可信 JAR、依赖及目录。 |
| C136 / `app.plugin.local-jar.root-directory` | A / path | ${IJOB_PLUGIN_LOCAL_JAR_ROOT:} | 可信 Local JAR 所在绝对目录；容器需单独挂载。 |
| C137 / `app.plugin.local-jar.max-jar-bytes` | B / bytes | ${IJOB_PLUGIN_LOCAL_JAR_MAX_BYTES:67108864} | JAR 文件字节上限；评估内存与归档安全限制后调整。 |
| C138 / `app.plugin.local-jar.max-jar-entries` | B / integer | ${IJOB_PLUGIN_LOCAL_JAR_MAX_ENTRIES:10000} | JAR 条目数上限；评估内存与归档安全限制后调整。 |
| C139 / `app.plugin.local-jar.max-expanded-bytes` | B / bytes | ${IJOB_PLUGIN_LOCAL_JAR_MAX_EXPANDED_BYTES:268435456} | 解压内容字节上限；评估内存与归档安全限制后调整。 |
| C140 / `app.plugin.local-jar.max-compression-ratio` | B / integer | ${IJOB_PLUGIN_LOCAL_JAR_MAX_COMPRESSION_RATIO:100} | 最大压缩比；评估内存与归档安全限制后调整。 |
| C141 / `app.plugin.local-jar.max-cached-loaders` | B / integer | ${IJOB_PLUGIN_LOCAL_JAR_MAX_CACHED_LOADERS:32} | 缓存类加载器数量；评估内存与归档安全限制后调整。 |
