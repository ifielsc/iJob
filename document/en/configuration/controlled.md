# Controlled configuration workflows

[简体中文](../../zh-CN/configuration/controlled.md) · [Manual home](../README.md)

Administrators may maintain these values through controlled workflows. They affect account sources, keys, database identity or exposed endpoints. Back up config/data, record the reason and affected nodes, and establish rollback conditions before acting.

| Change | Workflow |
| --- | --- |
| STATIC / DATABASE, first administrator, password changes | [Accounts](../operations/accounts.md), [account-mode changes](../operations/account-mode-change.md) |
| STANDALONE / CLUSTER and node roles | [Runtime modes](../operations/standalone-cluster.md), [runtime-mode changes](../operations/runtime-mode-change.md) |
| Database address/account/secrets and restore targets | [Backup/restore](../operations/backup-restore.md) |
| Ingress, health details and management endpoint exposure | [Production](../operations/production.md) |

AES key and key-id must match encrypted resources. Reinitialization cannot recover data encrypted with a lost key. Existing clusters also validate authentication/security fingerprints; do not independently change a node's BCrypt/AES/Token values. There is no one-click key rotation, cross-database migration or cluster-fingerprint re-registration workflow. Validate a migration separately rather than clearing coordination tables.

## Settings in this topic

These are source/implementation defaults; see the [complete catalog](../configuration-catalog.md) for setup overrides and provenance.

| ID / key | Level / type | Source defaults | Explanation |
| --- | --- | --- | --- |
| C025 / `app.platform-database.vendor` | C / enum | ${IJOB_PLATFORM_DB:mysql} | Database vendor; align driver, migration location, installation metadata and startup environment. |
| C035 / `app.runtime.mode` | C / enum | ${IJOB_RUNTIME_MODE:STANDALONE} | Use the operations procedure for STANDALONE/CLUSTER changes; DEVELOPMENT is not a deployment mode. |
| C071 / `app.auth.mode` | C / enum | ${IJOB_AUTH_MODE:STATIC} | STATIC/DATABASE account source; NONE is not for deployed installations. |
| C072 / `app.auth.username` | C / text | ${IJOB_AUTH_USERNAME:} | STATIC or initial DATABASE bootstrap account; manage normal database users in the UI. |
| C073 / `app.auth.password-hash` | C / secret | ${IJOB_AUTH_PASSWORD_HASH:} | BCrypt password hash, not plaintext; generate through the password-change procedure. |
| C074 / `app.auth.bootstrap-enabled` | C / boolean | ${IJOB_AUTH_BOOTSTRAP_ENABLED:false} | Controlled first-admin bootstrap only for an empty user table; disable after completion. |
| C075 / `app.security.aes.key` | C / secret | ${IJOB_CONFIG_AES_KEY:} | Encrypts stored resource credentials; preserve in backups and never replace with an arbitrary key. |
| C076 / `app.security.aes.key-id` | C / text | ${IJOB_CONFIG_AES_KEY_ID:local} | AES key identifier; manage as part of a complete rotation procedure. |
| C142 / `sa-token.token-name` | C / text | ${IJOB_AUTH_TOKEN_NAME:ijob-token} | Authentication header name; align clients, proxies and cluster nodes. |
| C156 / `management.endpoints.web.exposure.include` | C / text-list | health,info,metrics | Management endpoint exposure; review access controls before adding endpoints. |
| C159 / `management.endpoint.health.show-details` | C / enum | never | Health detail visibility; design access controls before exposing details. |
| C167 / `spring.flyway.default-schema` | C / text | ${IJOB_PLATFORM_DB_SCHEMA:${spring.datasource.username:${IJOB_DB_USERNAME:ijob}}}; ${IJOB_PLATFORM_DB_SCHEMA:public}; ${IJOB_PLATFORM_DB_SCHEMA:dbo} | Migration schema; plan migration before changing an existing installation. |
| C168 / `app.platform-database.schema` | C / text | ${IJOB_PLATFORM_DB_SCHEMA:${spring.datasource.username:${IJOB_DB_USERNAME:ijob}}}; ${IJOB_PLATFORM_DB_SCHEMA:public}; ${IJOB_PLATFORM_DB_SCHEMA:dbo} | Platform schema; configure per vendor and align JDBC and Flyway. |
| C170 / `spring.datasource.hikari.schema` | C / text | ${IJOB_PLATFORM_DB_SCHEMA:public}; ${IJOB_PLATFORM_DB_SCHEMA:dbo} | Default schema; align platform/migration schemas and account permissions. |
| C177 / `agent.token` | C / secret | ${IJOB_AGENT_TOKEN:} | Agent authentication token; align both ends, image imports a private token file. |
| C183 / `spring.datasource.url` | C / text | generated from database selection | Platform database connection; changing database products requires migration, not only a URL edit. |
| C184 / `spring.datasource.username` | C / text | user input; empty for SQLite | Database account; maintain together with grants and password. |
| C185 / `spring.datasource.password` | C / secret | private input; empty for SQLite | Database password; rotate in a restricted file and verify connectivity. |
| C206 / `IJOB_CONFIG_PATH` | C / path | required; no default | Private YAML path for advanced Compose; first-run bundles use IJOB_HOME/application.properties. |
| C207 / `IJOB_AGENT_TOKEN_PATH` | C / path | required; no default | Path to private Agent token file, not the token value. |
| C209 / `IJOB_PLATFORM_DB` | C / enum | setup selects vendor; repository YAML fallback mysql | Native launcher derives vendor from installation metadata; align backend Docker environment. |
| C210 / `IJOB_PLATFORM_DB_SCHEMA` | C / text | PostgreSQL public; SQL Server dbo; Oracle IJOB_DB_USERNAME or ijob | Schema placeholder in database configurations; align JDBC, platform and migration schemas. |
| C211 / `IJOB_DB_USERNAME` | C / text | ijob for Oracle schema fallback only | Only an Oracle schema fallback in the template; not a replacement for spring.datasource.username. |
| C225 / `name` | C / compose-field | setup-generated project; advanced ijob-v1-candidate | Project identity; use an independent name for restore or multiple installations. |
| C228 / `services.<service>.volumes` | C / compose-field | setup config/runtime binds; advanced named data volumes | Private config/data/plugin mounts; check source paths and permissions on restore. |
| C229 / `services.<service>.environment` | C / compose-field | template-specific | Container environment; not automatic host-shell inheritance. Align with configuration files. |
| C243 / `secrets.<secret>.file` | C / compose-field | advanced private config/token paths | Advanced Compose secret-file source; restrict permissions and keep outside Git. |
| C244 / `volumes.<volume>` | C / compose-field | advanced backend-data / backend-b-data | Advanced template persistent-volume identity; do not reuse old environments or delete to reset. |

## Database configuration scope

spring.datasource controls the platform connection for both standalone and cluster nodes, not SQL/ETL business resources. See [Platform database](platform-database.md) for scope, schema and pool sizing.
