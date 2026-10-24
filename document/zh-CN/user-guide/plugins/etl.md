# ETL：源、转换、目标与增量

[English](../../../en/user-guide/plugins/etl.md) · [手册首页](../../README.md)

## 入口与前提

任务 → 新增/编辑 → 插件 `ETL`。先准备字段表要求的资源、权限及执行节点依赖。任务公共超时、重试和发布规则见[任务](../tasks.md)。

## 字段与作用

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `source.type / resourceId` | DATABASE/FILE/KAFKA/RABBITMQ；资源类型必须匹配。 |
| `source.sql / parameters / beforeSql / fetchSize` | 数据库读取 SQL、绑定参数、前置 SQL 与抓取批次（2000）。前置 SQL 可能写入。 |
| `source.path / format / readMode` | FILE 支持 CSV/TXT/EXCEL/REGEX/SQLITE/MDB；DIRECT 与 SQL 读取方式有不同字段。SQLite 文件源不是 DB 资源。 |
| `source.charset / delimiter / quote / escape / header / skipLines` | 文件解码与分隔；默认 UTF-8、逗号、双引号、有表头、跳过 0 行。 |
| `source.sheetName / sheetIndex / sheets / headerRow / startRow` | Excel 单/多工作表及行定位；headerRow 默认 0，按页面索引提示填写。 |
| `source.regexMode / pattern / blockStart / blockEnd / regexFields` | 正则逐行或分块读取；字段指定捕获组和默认值，限制块行数/字符数。 |
| `source.stagingTable / stagingColumns` | 文件 SQL 模式临时表和列名；不等于平台业务表。 |
| `transforms / validations` | 按顺序转换字段，再按规则校验；先预览样本并核对类型、空值和丢弃行为。 |
| `target.type / resourceId / schema / table / path` | DATABASE 或 FILE 目标，按分支填表或文件路径。 |
| `target.mappingMode / columns / excludedFields / logicalKeys` | 手工或自动映射；显式核对源到目标列。UPSERT 等需要有效逻辑键。 |
| `target.mode / transactionScope / nullPolicy / columnNullPolicies` | APPEND/UPSERT/INSERT_ONLY/UPDATE_ONLY/SCD2；默认 APPEND、BATCH、KEEP_TARGET。逐列空值策略可覆盖总策略。 |
| `target.beforeSql / afterSql / protection / scd2` | 前后 SQL、受保护记录和历史拉链配置会影响业务写入；SCD2 配业务键、比较列、生效时间、历史范围/当前标记列。 |
| `target.existingFilePolicy / emptyFilePolicy` | 默认 FAIL / CREATE_WITH_HEADER；覆盖、跳过和空文件输出需明确选择。 |
| `incremental.enabled / checkpointKey / watermarkField / watermarkType` | 启用增量并明确检查点归属、水位列和类型；默认关闭，类型 STRING。 |
| `incremental.tieBreakerFields / initialTieBreakerValues / upperBoundSql / initialWatermark` | 相同水位用稳定唯一排序打破平局；初值和上界 SQL 必须与查询条件/排序一致。 |
| `incremental.allowCheckpointPastInvalidRecords / file` | 默认允许越过无效记录；决定是否可接受跳过。文件增量还需标记位置/模板或正则/时区/回看/稳定期/变更策略。 |
| `execution / message / allowNonIdempotentRetry` | 读取/写入批次、查询超时、记录/文件/文本/Excel 上限；消息源另限摄入时间、空闲时间、批次/缓冲/字节和 JSON 深度。非幂等重试默认不放行。 |

## 最小样例与核对

```sql
SELECT id, amount, updated_at FROM orders ORDER BY id;
```

入门先做全量三行 DATABASE→DATABASE，目标 orders，映射 id/amount/updated_at，逻辑键 id，模式 UPSERT；核对目标 3 行、金额和 600。再单独演练增量：相同水位两行、重试、失败回退、无效记录与检查点；不要仅靠最大时间戳认定不漏数。BATCH 事务可能部分提交，重试前核对目标和检查点。消息源提交/确认与目标写入是不同边界。

样例代码是配置片段，资源 ID/路径需替换；实际外部协议验收状态见[案例索引](../../examples.md)。

## 真实页面示例

数据库源和只读提取 SQL。

![数据库源和只读提取 SQL。](../../images/manual/etl-editor.png)

目标表、UPSERT、逻辑键与同名字段映射。

![目标表、UPSERT、逻辑键与同名字段映射。](../../images/manual/etl-target.png)
