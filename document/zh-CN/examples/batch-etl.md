# 同步两张表，并控制目标命名

[English](../../en/examples/batch-etl.md) · [手册首页](../README.md)

批量同步orders与order_items，逐表核对映射和目标数据。

## 适用场景与读者

适合需要同步多张表的用户，重点是目标命名、逐表结果及部分提交后的恢复。

## 开始前准备

先完成[教程公共准备](../examples.md)，再准备下表中的资源。

| 资源 | 要求 |
| --- | --- |
| 数据库 | 两个新的独立PostgreSQL业务库。初始化身份需CREATE/INSERT权限；iJob来源账号读取源表，目标账号具备目标表读取、插入与更新权限。失败变体另用新的空目标库。 |

## 配置一览

| 界面字段 / 配置项 | 本例填写值 |
| --- | --- |
| 同步表 / 逻辑键 | orders、order_items / id |
| 提取 / 写入 | FULL / UPSERT |
| 目标前缀 / 清空 | copy_ / 关闭 |

## 操作步骤

### 1. 准备两张源表

使用新的专属业务库，避免复用已经追加第四行的ETL练习数据。由初始化身份在**源业务库**执行：

```sql
CREATE TABLE orders (
 id INTEGER PRIMARY KEY,
 amount INTEGER NOT NULL,
 updated_at TIMESTAMP NOT NULL
);
INSERT INTO orders VALUES
 (1,100,TIMESTAMP '2026-01-01 00:00:00'),
 (2,200,TIMESTAMP '2026-01-02 00:00:00'),
 (3,300,TIMESTAMP '2026-01-03 00:00:00');
CREATE TABLE order_items (
 id INTEGER PRIMARY KEY,
 order_id INTEGER NOT NULL,
 qty INTEGER NOT NULL
);
INSERT INTO order_items VALUES (1,1,1),(2,2,2),(3,3,3);
```

在**目标业务库**创建两个空表，表名带copy_前缀：

```sql
CREATE TABLE copy_orders (
 id INTEGER PRIMARY KEY,
 amount INTEGER NOT NULL,
 updated_at TIMESTAMP NOT NULL
);
CREATE TABLE copy_order_items (
 id INTEGER PRIMARY KEY,
 order_id INTEGER NOT NULL,
 qty INTEGER NOT NULL
);
```

给iJob来源账号授予两张源表SELECT权限，给目标账号授予两张目标表所需的读取、插入和更新权限，分别创建并测试两个数据库资源。

### 2. 选择源、目标和表清单

新建BATCH_ETL任务，选择源与目标资源，显式选择orders、order_items，填id逻辑键和copy_前缀。保持FULL、UPSERT、不清空。

### 3. 预览映射后发布

检查每张表的最终目标名、可写列及键映射。目标缺少非键列不一定使任务失败，因此需要主动核对列清单。保存发布后手动执行。

### 4. 逐表核对并重跑

在目标库执行以下只读查询：

```sql
SELECT COUNT(*) AS row_count, SUM(amount) AS amount_total FROM copy_orders;
SELECT COUNT(*) AS row_count, SUM(qty) AS quantity_total FROM copy_order_items;
SELECT id FROM copy_orders ORDER BY id;
SELECT id FROM copy_order_items ORDER BY id;
```

第一条应为3行、金额600，第二条应为3行、数量合计6；后两条主键集合都为1、2、3。再次执行同一任务后重新查询，结果保持不变。

### 5. 理解部分提交

故障演练使用**另一个新的空目标业务库**，保留前面的成功目标不动。由该库的初始化身份执行：

```sql
CREATE TABLE copy_orders (
 id INTEGER PRIMARY KEY,
 amount INTEGER NOT NULL,
 updated_at TIMESTAMP NOT NULL
);
CREATE TABLE copy_order_items (
 id INTEGER PRIMARY KEY,
 order_id INTEGER NOT NULL,
 qty INTEGER NOT NULL,
 CONSTRAINT docs_reject_qty CHECK (qty < 0)
);
```

该约束在空表创建时就存在，样例qty为正数，写入时会被拒绝。为这个失败目标创建独立资源，授予运行所需权限。新增同配置BATCH_ETL任务，只把目标换成该资源，保存执行。查看FAILED、逐表执行记录和实际目标：拒绝表应为0行，其他表是否已提交取决于实际处理顺序。不要把失败当作全部表已经回滚。

只在这个失败演练库，由具备DDL权限的身份移除专用约束：

```sql
ALTER TABLE copy_order_items DROP CONSTRAINT docs_reject_qty;
```

重执行后，用步骤4的查询核对两张表分别达到3/600和3/6，主键无重复。

## 预期结果

| 检查项 | 预期值 |
| --- | --- |
| 订单目标 | 3行，金额600 |
| 明细目标 | 3行，数量合计6 |
| 重复运行 | 两表主键无重复，汇总不变 |
| 明细约束拒绝 | 任务FAILED；本例订单表已提交3行，明细表0行 |
| 移除专用约束后重跑 | 订单3行/金额600，明细3行/数量6，已有订单不重复插入 |

## 常见失败

| 现象 | 处理方法 |
| --- | --- |
| 部分表写入后失败 | 检查逐表结果和已提交目标，修复后幂等重跑；不能假设失败自动回滚全部表。 |

## 安全与清理

停止相关调度，确认无运行实例后移除本教程表、映射及失败演练副本。

## 适用版本与限制

适用1.0.0、PostgreSQL两表FULL/UPSERT，包含重复运行、明细约束失败及恢复。失败副本使用独立目标库；跨表可能部分提交。清空导入、TRUNCATE和批量增量不在本教程主路径内。

## 相关页面

- [批量 ETL](../user-guide/plugins/batch-etl.md)

- [查询订单，并设置数据质量门禁](sql-and-validation.md)
