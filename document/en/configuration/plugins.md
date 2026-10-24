# Plugin switches and Local JAR deployment

[简体中文](../../zh-CN/configuration/plugins.md) · [Manual home](../README.md)

All 11 plugins are listed below; all except Local JAR are enabled by default. Switches apply at node startup and are separate from an individual task's enabled state. Review published tasks, job references, queued and active instances before disabling. Every eligible Worker needs the plugin and its dependencies.

```properties
app.plugin.script.enabled=true
app.plugin.local-jar.enabled=false
```

Local JAR runs trusted code. Prepare the executing node's plugin directory and explicit Docker mounts before enabling. Use Maven SPI 1.0.0, namespace `com.ifielsc.ijob` and protocol 1. Verify SHA-256, entry class and protocol when selecting/uploading an artifact. Protocol number 1 does not make old-namespace binaries compatible. See [Plugin reference](../user-guide/plugins/README.md) for forms, minimal examples and outputs.

## Settings in this topic

These are source/implementation defaults; see the [complete catalog](../configuration-catalog.md) for setup overrides and provenance.

| ID / key | Level / type | Source defaults | Explanation |
| --- | --- | --- | --- |
| C125 / `app.plugin.event.enabled` | A / boolean | ${IJOB_PLUGIN_EVENT_ENABLED:true} | event plugin switch on this node; review published tasks and active work before disabling. |
| C126 / `app.plugin.script.enabled` | A / boolean | ${IJOB_PLUGIN_SCRIPT_ENABLED:true} | script plugin switch on this node; review published tasks and active work before disabling. |
| C127 / `app.plugin.sql.enabled` | A / boolean | ${IJOB_PLUGIN_SQL_ENABLED:true} | sql plugin switch on this node; review published tasks and active work before disabling. |
| C128 / `app.plugin.file-transfer.enabled` | A / boolean | ${IJOB_PLUGIN_FILE_TRANSFER_ENABLED:true} | file-transfer plugin switch on this node; review published tasks and active work before disabling. |
| C129 / `app.plugin.api-call.enabled` | A / boolean | ${IJOB_PLUGIN_API_CALL_ENABLED:true} | api-call plugin switch on this node; review published tasks and active work before disabling. |
| C130 / `app.plugin.push.enabled` | A / boolean | ${IJOB_PLUGIN_PUSH_ENABLED:true} | push plugin switch on this node; review published tasks and active work before disabling. |
| C131 / `app.plugin.data-check.enabled` | A / boolean | ${IJOB_PLUGIN_DATA_CHECK_ENABLED:true} | data-check plugin switch on this node; review published tasks and active work before disabling. |
| C132 / `app.plugin.server-monitor.enabled` | A / boolean | ${IJOB_PLUGIN_SERVER_MONITOR_ENABLED:true} | server-monitor plugin switch on this node; review published tasks and active work before disabling. |
| C133 / `app.plugin.etl.enabled` | A / boolean | ${IJOB_PLUGIN_ETL_ENABLED:true} | etl plugin switch on this node; review published tasks and active work before disabling. |
| C134 / `app.plugin.batch-etl.enabled` | A / boolean | ${IJOB_PLUGIN_BATCH_ETL_ENABLED:true} | batch-etl plugin switch on this node; review published tasks and active work before disabling. |
| C135 / `app.plugin.local-jar.enabled` | A / boolean | ${IJOB_PLUGIN_LOCAL_JAR_ENABLED:false} | local-jar plugin switch on this node; review published tasks and active work before disabling. Also provide trusted JARs, dependencies and directory. |
| C136 / `app.plugin.local-jar.root-directory` | A / path | ${IJOB_PLUGIN_LOCAL_JAR_ROOT:} | Absolute trusted Local JAR directory; mount it explicitly in containers. |
| C137 / `app.plugin.local-jar.max-jar-bytes` | B / bytes | ${IJOB_PLUGIN_LOCAL_JAR_MAX_BYTES:67108864} | Maximum JAR file bytes; assess memory and archive safety before tuning. |
| C138 / `app.plugin.local-jar.max-jar-entries` | B / integer | ${IJOB_PLUGIN_LOCAL_JAR_MAX_ENTRIES:10000} | Maximum JAR entries; assess memory and archive safety before tuning. |
| C139 / `app.plugin.local-jar.max-expanded-bytes` | B / bytes | ${IJOB_PLUGIN_LOCAL_JAR_MAX_EXPANDED_BYTES:268435456} | Maximum expanded bytes; assess memory and archive safety before tuning. |
| C140 / `app.plugin.local-jar.max-compression-ratio` | B / integer | ${IJOB_PLUGIN_LOCAL_JAR_MAX_COMPRESSION_RATIO:100} | Maximum compression ratio; assess memory and archive safety before tuning. |
| C141 / `app.plugin.local-jar.max-cached-loaders` | B / integer | ${IJOB_PLUGIN_LOCAL_JAR_MAX_CACHED_LOADERS:32} | Cached classloader limit; assess memory and archive safety before tuning. |
