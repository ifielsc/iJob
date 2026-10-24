# 数据库资源

[English](../../en/user-guide/resources-database.md) · [手册首页](../README.md)

## 入口与操作路径

资源 → 数据库资源。页面动作随当前账号权限提供；先确认操作对象及环境。

## 配置和动作说明

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `dbType` | MYSQL/POSTGRESQL/SQLSERVER/ORACLE，驱动与 URL 必须匹配。 |
| `jdbcUrl / schema` | 数据库地址与可选 schema，URL 不含明文口令。 |
| `username / password` | 使用独立业务账号，仅授予任务需要的读写权限。 |

## 操作与结果核对

PostgreSQL 示例 URL：`jdbc:postgresql://127.0.0.1:15432/docs_source`，替换本环境端口。先测试连接，再执行只读 SELECT。平台 SQLite 不在这个资源列表；ETL 的 SQLite 文件读取在 FILE 源配置。
