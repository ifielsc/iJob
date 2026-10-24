# 多表 ETL 与目标命名

[English](../../en/examples/batch-etl.md) · [手册首页](../README.md)

**验证状态：配置与操作示例；本轮未完成该场景外部依赖的端到端验收。**

相关字段：[批量 ETL](../user-guide/plugins/batch-etl.md)。

## 准备、配置与执行

准备两个独立数据库，源表 orders/order_items 各 3 行，两表都具有适用逻辑键 id；目标建好兼容结构。选择明确表列表、FULL、UPSERT、clearBeforeImport=false；可设 targetNaming.prefix=copy_，但目标表名需对应。

先核对选表预览与列映射，运行后逐表检查行数、主键集合和金额汇总。重复运行验证幂等，目标故意缺列的失败变体用另一份库，记录已提交表和剩余表。清空/增量/非事务 TRUNCATE 需额外确认和备份，不能因勾选风险确认就忽略部分提交。

## 结束检查

保留输入、定义版本、触发/实例 ID、脱敏日志和目标核对结果。只停止本案例拥有的进程或调度；其他环境和历史证据不作为清理对象。
