# 查询订单，并设置数据质量门禁

[English](../../en/examples/sql-and-validation.md) · [手册首页](../README.md)

查询三行订单，用标量检查发现不满足预期的记录数。

## 适用场景与读者

适合需要查询业务数量、设置质量门禁的配置人员，重点是标量结果与不合格数据。

## 开始前准备

先完成[教程公共准备](../examples.md)，再准备下表中的资源。

| 资源 | 要求 |
| --- | --- |
| 业务库 | 独立PostgreSQL业务库；样例初始化身份需CREATE/INSERT权限，iJob查询与检查账号只需读取教程表。不能连接平台库练习。 |

## 配置一览

| 界面字段 / 配置项 | 本例填写值 |
| --- | --- |
| SQL任务 | SELECT COUNT(*) AS total FROM orders;；结果变量ordersResult；查询超时30秒 |
| 校验任务 | SELECT COUNT(*) FROM orders；NORMAL / EQ / 数值3 / ERROR |

## 操作步骤

### 1. 创建三行订单

使用初始化身份在空业务库执行以下SQL；不要通过只读任务账号执行建表：
```sql
CREATE TABLE orders(id INTEGER PRIMARY KEY, amount INTEGER NOT NULL, updated_at TIMESTAMP NOT NULL);
INSERT INTO orders VALUES
(1,100,TIMESTAMP '2026-01-01 00:00:00'),
(2,200,TIMESTAMP '2026-01-02 00:00:00'),
(3,300,TIMESTAMP '2026-01-03 00:00:00');
```
为iJob查询账号授予orders的SELECT权限，再创建数据库资源并测试连接。

### 2. 保存并运行查询任务

新建SQL任务，选择资源，按表填写只读查询。保存后执行，查看输出变量 `ordersResult`，其值应为数值 `3`。该查询只有一列，结果变量直接保存标量，列别名 `total` 不会产生对象包装。

### 3. 配置值比较检查

新建DATA_CHECK任务，选择同一资源。新增检查项“订单条数”，类型选值比较NORMAL，比较EQ，期望值类型为数值、值3，级别ERROR。保存执行，核对实际值3及SUCCESS。

### 4. 验证不合格结果

用初始化身份在本教程库另建失败变体表，并为iJob查询账号授予该表SELECT权限：
```sql
CREATE TABLE orders_short AS SELECT * FROM orders WHERE id <= 2;
```
新增一个不同名称的检查任务，按原检查配置填写，仅将查询改为 `SELECT COUNT(*) FROM orders_short`。运行后应FAILED、实际值2；原三行orders不变。

### 5. 可选：连接下游

在作业画布中把检查任务连到无副作用SCRIPT，依赖条件ALL_SUCCESS、失败策略STOP。分别引用正常/失败检查运行，核对失败路径下游没有执行；不要把单任务通过等同于新作业链路已经通过。

## 预期结果

| 检查项 | 预期值 |
| --- | --- |
| 正常查询与检查 | ordersResult=3（数值），检查实际值3，实例SUCCESS |
| 两行变体 | 实际值2，校验FAILED，原表仍3行 |

## 常见失败

| 现象 | 处理方法 |
| --- | --- |
| COUNT类型选错 | SQL COUNT(*)返回一个标量，本例用NORMAL，不用把结果集行数当业务行数的COUNT模式。 |

## 安全与清理

确认无人使用后删除本教程orders_short及练习任务；保留或移除orders由本教程数据归属决定。

## 适用版本与限制

适用1.0.0、PostgreSQL标量查询与NORMAL值比较。两行失败副本不修改原三行订单；连接下游和JDBC查询超时属于独立扩展，不能将超时状态推广到所有驱动。

## 界面参考

以下为该功能的真实界面参考；示例名称和实例 ID 可与本次运行不同。扩展步骤以正文配置和结果为准。

![界面参考：sql-editor](../images/manual/sql-editor.png)

![界面参考：check-editor](../images/manual/check-editor.png)

## 相关页面

- [SQL 执行](../user-guide/plugins/sql.md)

- [数据校验](../user-guide/plugins/data-check.md)

- [串行、并行、汇合与条件分支](dag-control-flow.md)
