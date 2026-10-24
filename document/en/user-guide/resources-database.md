# Database resources

[简体中文](../../zh-CN/user-guide/resources-database.md) · [Manual home](../README.md)

## Entry and workflow

Resources → Database resources. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `dbType` | MYSQL/POSTGRESQL/SQLSERVER/ORACLE; match the driver and URL. |
| `jdbcUrl / schema` | Database URL and optional schema; keep passwords out of URLs. |
| `username / password` | Use a dedicated business account with necessary read/write privileges only. |

## Use and verify

Example PostgreSQL URL: `jdbc:postgresql://127.0.0.1:15432/docs_source`; replace the port. Test connection then a read-only SELECT. Platform SQLite is not in this resource list; ETL SQLite file reading belongs to a FILE source.
