# Database resources

[简体中文](../../zh-CN/user-guide/resources-database.md) · [Manual home](../README.md)

## Entry and workflow

Resources → Database resources. Creating, changing, deleting or testing resources and reading sensitive details require ADMIN. All three roles can read permitted summaries. A disabled resource or one unreachable from the execution node remains unusable by the task. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

See [common resource fields](resources.md#common-resource-fields) for Name, Resource code, Enable and Description, and [common resource actions](resources.md#common-resource-actions) for saving, deletion and secret preservation. Initial values below apply to new resources; editing loads saved non-secret values and keeps existing secrets by default.

### Connection settings

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Database type (`dbType`) | Required | Not selected | Always | MySQL, PostgreSQL, Oracle or SQL Server, represented by MYSQL, POSTGRESQL, ORACLE and SQLSERVER. Match the business database and JDBC URL. |
| JDBC URL (`jdbcUrl`) | Required | Empty | Always | JDBC address for the selected type, up to 2048 characters. Do not include password, passwd or pwd parameters in the URL. |
| Username (`username`) | Required | Empty | Always | Up to 128 characters. Use a dedicated business account with only the required read/write privileges. |
| Password (`password`) | Form allows empty; target authentication still applies | Empty on creation; keep existing on edit | Always | Up to 4096 characters; saved passwords are hidden. Use Clear to remove a value, not an empty replacement. |
| Schema (`schema`) | Optional | Empty | Advanced settings expanded | Up to 128 characters; use the schema required by the target database. |

### Database resource actions

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Change database type | ADMIN | Select another type in the editor and confirm | Enter the connection URL, username and password on the first switch to that type; a previously edited type restores its draft. Cancelling keeps current input. |
| Test connection | ADMIN | Resource saved and enabled; target reachable from API node | Check the saved database connection; this does not verify task SQL or all business privileges. |

## Use and verify

Example PostgreSQL URL: `jdbc:postgresql://127.0.0.1:15432/docs_source`; replace the port. Test connection then a read-only SELECT. Platform SQLite is not in this resource list; ETL SQLite file reading belongs to a FILE source.
