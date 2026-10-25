# 批量 ETL

[English](../../../en/user-guide/plugins/batch-etl.md) · [手册首页](../../README.md)

## 入口与前提

任务 → 新增/编辑 → 插件 `BATCH_ETL`。先准备字段表要求的资源、权限及执行节点依赖。任务公共超时、重试和发布规则见[任务](../tasks.md)。

## 字段与作用

| 页面字段/动作 | 用途与填写规则 |
| --- | --- |
| 来源数据库、来源 Schema、目标数据库、目标 Schema、同步表 | 两个数据库资源、可选 schema 和明确选表列表；先一两张小表。 |
| 逻辑键、提取方式、增量规则 | 默认 FULL；INCREMENTAL 指定 watermarkColumn/type/initialWatermark/initialLogicalKeyValues，核对每张表都有所需列。 |
| 写入方式、空值策略、目标表前后缀 | 默认 UPSERT / OVERWRITE；目标表名前后缀默认空，不改变源表。 |
| 导入前清空、清空方式 | 默认不清理，清理模式 DELETE；TRUNCATE 可能不支持事务。 |
| 隐式提交、部分提交、非幂等重试确认 | 显式风险确认，不是让不安全场景自动安全；默认 false。 |
| 批次与执行限制 | 默认 fetchSize 2000、writeBatchSize 1000、超时 300 秒、最多 500 表、每表 1000 万行、总计 5000 万行。 |

## 最小样例与核对

先准备上表的独立测试资源，按页面逐项填写；保存后手动执行，再按下文核对实例、输出与目标系统。保存本身不会执行。

| 配置项 | 填写内容 |
| --- | --- |
| 来源数据库 / 目标数据库 | 分别选择独立测试资源 |
| 同步表 | `orders`、`order_items` |
| 逻辑键 | `id`（两张表均须有此键） |
| 提取方式 | 全量提取（FULL） |
| 写入方式 | 插入或更新（UPSERT） |
| 导入前清空 | 关闭 |

确保两表均有适用逻辑键和目标结构，先预览选表、目标名与映射，执行后逐表核对行数、键和金额汇总。某表成功不能证明整批成功；失败重试需核对已提交表。清空导入先在独立副本演练并确认可恢复备份。

示例中的资源、路径及对象名称需替换为本环境实际值；实际外部协议验收状态见[案例索引](../../examples.md)。

## 技术字段对照（按需查阅）

以下是保存配置中的键名，供开发或排障定位；不是需要粘贴的任务 JSON。自动核验的元数据不作为页面手工输入。

| 页面字段/分组 | 技术键名 |
| --- | --- |
| 来源数据库、来源 Schema、目标数据库、目标 Schema、同步表 | `sourceResourceId / sourceSchema / targetResourceId / targetSchema / tables` |
| 逻辑键、提取方式、增量规则 | `logicalKeys / extractMode / incremental` |
| 写入方式、空值策略、目标表前后缀 | `loadMode / nullPolicy / targetNaming` |
| 导入前清空、清空方式 | `clearBeforeImport / clearMode` |
| 隐式提交、部分提交、非幂等重试确认 | `acknowledgeNonTransactionalTruncate / acknowledgePartialCommit / allowNonIdempotentRetry` |
| 批次与执行限制 | `execution` |
