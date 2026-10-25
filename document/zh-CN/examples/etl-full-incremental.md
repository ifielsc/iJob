# 全量、增量与 ETL 检查点

[English](../../en/examples/etl-full-incremental.md) · [手册首页](../README.md)

**验证状态：基础路径已在独立环境实测；下述扩展不自动继承通过结论。**

相关字段：[ETL：源、转换、目标与增量](../user-guide/plugins/etl.md)。

## 准备、配置与执行

复用本案例独立业务源的三行 orders，另建空目标库和同结构表。ETL source=DATABASE，SQL 为 `SELECT id,amount,updated_at FROM orders ORDER BY id`；target=DATABASE、table=orders、mode=UPSERT、logicalKeys=[id]，三列同名映射。保持增量关闭，执行后用独立连接核对：

```sql
SELECT COUNT(*), SUM(amount) FROM orders;
-- 3, 600
```

本轮已在独立 PostgreSQL 验证全量 UPSERT 重复执行、增量检查点恢复和相同水位追加行。增量使用独立 checkpointKey、watermarkField=updated_at、watermarkType=TIMESTAMP、initialWatermark=2025-12-31T00:00:00、tieBreakerFields=[id]、initialTieBreakerValues=[0]，上界查询为 `SELECT MAX(updated_at) FROM orders`。读取 SQL 明确转换 JSON 字符串参数的类型：

```sql
SELECT id, amount, updated_at FROM orders
WHERE (updated_at > CAST(:etlLastWatermark AS timestamp)
   OR (updated_at = CAST(:etlLastWatermark AS timestamp) AND id > :etlLastTie1))
  AND updated_at <= CAST(:etlUpperWatermark AS timestamp)
ORDER BY updated_at, id;
```

首次导入三行后，在最后水位追加 id=4、amount=400；再次执行目标为 4 行、金额 1000，随后空跑保持不变。实测发现并修复了 JDBC 时间水位保存后带偏移量、再次读取解析失败的问题；新包保留原检查点后恢复成功，新保存值保留本地时间和纳秒精度。检查点与目标写入仍是不同提交边界，需要幂等目标。另以 PostgreSQL 独立表实测 SCD2 首次写入、未变化重复、业务属性变更关旧开新、重复运行、倒退生效时间拒绝及修复后恢复，独立 SQL 核对连续历史区间和唯一当前标记。文件增量及其他数据库的 SCD2 方言另验；消息源的实际消费和失败恢复见[消息案例](push-and-notifications.md)。旧截图展示全量路径。

## 结束检查

保留输入、定义版本、触发/实例 ID、脱敏日志和目标核对结果。只停止本案例拥有的进程或调度；其他环境和历史证据不作为清理对象。

## 真实页面示例

全量 UPSERT 配置。

![全量 UPSERT 配置。](../images/manual/etl-target.png)

真实成功实例；目标行数和金额还通过独立 SQL 核对。

![真实成功实例；目标行数和金额还通过独立 SQL 核对。](../images/manual/etl-run.png)
