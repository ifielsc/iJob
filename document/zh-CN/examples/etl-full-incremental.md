# 同步订单增量，并处理相同水位

[English](../../en/examples/etl-full-incremental.md) · [手册首页](../README.md)

先同步三行订单，再同步相同时间水位上的新订单，重复运行保持结果不变。

## 适用场景与读者

适合需要持续同步变化数据的用户，重点是时间水位、同值排序和幂等重跑。

## 开始前准备

先完成[教程公共准备](../examples.md)，再准备下表中的资源。

| 资源 | 要求 |
| --- | --- |
| 源与目标 | 两个独立PostgreSQL业务数据库。样例初始化身份在专属库具备CREATE/INSERT权限；iJob来源连接只需SELECT，目标连接需对应写入权限。 |
| 业务数据 | 本教程使用id主键、整数amount与TIMESTAMP类型updated_at；不要复用TEXT水位列。 |
| 执行环境 | 可用Worker；任务自动重试先设0，避免掩盖手动核对过程。 |

## 配置一览

| 界面字段 / 配置项 | 本例填写值 |
| --- | --- |
| 来源/目标类型 | DATABASE / DATABASE |
| 目标表/写入方式 | orders / UPSERT |
| 逻辑键 | id |
| 字段映射 | id→id；amount→amount；updated_at→updated_at |
| 初始增量开关 | 关闭 |
| 增量检查点键 | 本教程专用新键，例如docs-orders-incremental-01 |

## 操作步骤

### 1. 创建来源和目标数据

准备两个新的专属业务数据库。以下初始化由具备CREATE/INSERT权限的数据库身份执行；任务读取来源可使用另一个只读账号。若表已存在，选择新的专属库或表名，不覆盖原表。

在**源业务库**执行建表和样例插入：

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
```

在**目标业务库**仅创建空表，不插入来源样例：

```sql
CREATE TABLE orders (
 id INTEGER PRIMARY KEY,
 amount INTEGER NOT NULL,
 updated_at TIMESTAMP NOT NULL
);
```

分别为iJob的来源账号授予该源表SELECT权限，为目标账号授予该目标表所需的读取、插入和更新权限。在“资源→数据库资源”创建两个连接并测试；初始化身份不必用于任务日常执行。

### 2. 配置并执行全量任务

任务→新增→ETL，按配置表选择资源和映射。来源SQL填写：
```sql
SELECT id,amount,updated_at FROM orders ORDER BY id;
```
保存发布后手动执行，在运行中心等待终态，再连接目标库查询：
```sql
SELECT COUNT(*) AS rows, SUM(amount) AS amount FROM orders;
```
预期3行、金额600。再次执行同一全量任务，结果仍为3行、600。

### 3. 配置增量和稳定排序

编辑任务，启用增量；标记字段updated_at，标记类型TIMESTAMP，初始标记2025-12-31T00:00:00；同值排序字段id，排序初始值0。使用新的检查点键。上界查询为SELECT MAX(updated_at) FROM orders。来源查询改成：
```sql
SELECT id,amount,updated_at FROM orders
WHERE (updated_at > CAST(:etlLastWatermark AS timestamp)
 OR (updated_at = CAST(:etlLastWatermark AS timestamp) AND id > :etlLastTie1))
 AND updated_at <= CAST(:etlUpperWatermark AS timestamp)
ORDER BY updated_at,id;
```
查询的过滤和排序必须与水位、同值排序字段一致。

### 4. 建立首次增量检查点

保存发布后手动执行。新检查点会从初始标记读取已有三行；UPSERT后目标仍为3行、600。查看该任务的增量进度，确认检查点对应本教程专用键。

### 5. 追加相同水位的新订单

在源库执行：
```sql
INSERT INTO orders VALUES (4,400,TIMESTAMP '2026-01-03 00:00:00');
```
再次执行增量任务，随后在目标库查询行数、金额及id=4的记录。预期4行、1000，id=4仅一行。

### 6. 无新数据再运行

保持源库不变再次执行。目标仍为4行、1000。不要只看SUCCESS；同时核对目标记录、检查点和任务使用的版本。

## 预期结果

| 检查项 | 预期值 |
| --- | --- |
| 首次全量及重复全量 | 3行，金额600 |
| 首次新检查点增量 | 3行，金额600 |
| 同水位追加id=4 | 本次提取并插入1行；目标4行，金额1000 |
| 空跑 | 本次提取0行；目标仍4行、金额1000 |
| 主键 | id=1、2、3、4各一行 |

## 常见失败

| 现象 | 处理方法 |
| --- | --- |
| 时间类型不匹配 | 源列使用TIMESTAMP，并保留查询中的CAST；不要将JSON时间字符串直接与数据库时间类型混用。 |
| 同水位漏行 | 检查同值排序字段、初始值和ORDER BY是否一致。 |
| 目标写入失败 | 核对已提交数据和检查点；修复后利用UPSERT重执行。检查点与目标写入并非一个事务。 |

## 安全与清理

停用本教程调度（若创建）；确认无活动实例后再移除本教程任务及专属源/目标样例。不要随意重置复用中的检查点。

## 适用版本与限制

适用1.0.0、PostgreSQL来源与目标，涵盖全量、TIMESTAMP水位加id排序、相同水位追加和空跑。每个独立练习使用新的检查点键。文件增量、其他数据库方言和SCD2是独立扩展。

## 界面参考

以下为该功能的真实界面参考；示例名称和实例 ID 可与本次运行不同。扩展步骤以正文配置和结果为准。

![界面参考：ETL 数据库来源](../images/manual/etl-editor.png)

![界面参考：目标表与字段映射](../images/manual/etl-target.png)

![界面参考：ETL 运行详情](../images/manual/etl-run.png)

## 相关页面

- [ETL：源、转换、目标与增量](../user-guide/plugins/etl.md)

- [SQL 与数据质量门禁](sql-and-validation.md)

- [业务消息与运行结果通知](push-and-notifications.md)
