# 批量 ETL

[English](../../../en/user-guide/plugins/batch-etl.md) · [手册首页](../../README.md)

## 入口与前提

任务 → 新增/编辑 → 插件 `BATCH_ETL`。先准备字段表要求的资源、权限及执行节点依赖。任务公共超时、重试和发布规则见[任务](../tasks.md)。

## 字段与作用

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `sourceResourceId / sourceSchema / targetResourceId / targetSchema / tables` | 两个数据库资源、可选 schema 和明确选表列表；先一两张小表。 |
| `logicalKeys / extractMode / incremental` | 默认 FULL；INCREMENTAL 指定 watermarkColumn/type/initialWatermark/initialLogicalKeyValues，核对每张表都有所需列。 |
| `loadMode / nullPolicy / targetNaming` | 默认 UPSERT / OVERWRITE；目标表名前后缀默认空，不改变源表。 |
| `clearBeforeImport / clearMode` | 默认不清理，清理模式 DELETE；TRUNCATE 可能不支持事务。 |
| `acknowledgeNonTransactionalTruncate / acknowledgePartialCommit / allowNonIdempotentRetry` | 显式风险确认，不是让不安全场景自动安全；默认 false。 |
| `execution` | 默认 fetchSize 2000、writeBatchSize 1000、超时 300 秒、最多 500 表、每表 1000 万行、总计 5000 万行。 |

## 最小样例与核对

```text
Tables: orders, order_items
Extract: FULL
Load: UPSERT
Clear before import: false
```

确保两表均有适用逻辑键和目标结构，先预览选表、目标名与映射，执行后逐表核对行数、键和金额汇总。某表成功不能证明整批成功；失败重试需核对已提交表。清空导入先在独立副本演练并确认可恢复备份。

样例代码是配置片段，资源 ID/路径需替换；实际外部协议验收状态见[案例索引](../../examples.md)。
