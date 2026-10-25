# 受控配置的变更入口

[English](../../en/configuration/controlled.md) · [手册首页](../README.md)

这些值允许管理员按流程维护，但不作为随意调参项。它们涉及账号来源、密钥、数据库身份或对外暴露范围。先备份配置和数据，记录修改原因、节点和回退条件，再执行对应流程。

| 变更 | 流程 |
| --- | --- |
| STATIC / DATABASE、首个管理员、改密 | [账号与认证](../operations/accounts.md)、[账号模式变更](../operations/account-mode-change.md) |
| STANDALONE / CLUSTER、节点角色 | [运行模式](../operations/standalone-cluster.md)、[运行模式变更](../operations/runtime-mode-change.md) |
| 数据库地址/账号/秘密、恢复目标 | [备份与恢复](../operations/backup-restore.md) |
| 入口、健康详情、管理端点暴露 | [生产部署](../operations/production.md) |

AES key 与 key-id 必须和已加密资源匹配；丢失原密钥无法靠重新初始化解密。已有集群还校验认证与安全指纹，不能单独改一个节点的 BCrypt/AES/Token 参数。没有一键密钥轮换、跨库迁移或集群指纹重登记流程；需要另行验证迁移方案，不能清协调表绕过保护。

## 本主题配置索引

下列为源码/实现默认值；初始化覆盖、来源和完整说明见[完整清单](../configuration-catalog.md)。

| ID / 配置键 | 级别 / 类型 | 默认值来源 | 说明 |
| --- | --- | --- | --- |
| C025 / `app.platform-database.vendor` | C / enum | ${IJOB_PLATFORM_DB:mysql} | 数据库产品标识；与驱动、迁移目录、安装元数据及启动环境一致。 |
| C035 / `app.runtime.mode` | C / enum | ${IJOB_RUNTIME_MODE:STANDALONE} | STANDALONE/CLUSTER 切换走运维流程；DEVELOPMENT 不用于正式部署。 |
| C071 / `app.auth.mode` | C / enum | ${IJOB_AUTH_MODE:STATIC} | STATIC/DATABASE 账号来源；NONE 不用于正式安装。 |
| C072 / `app.auth.username` | C / text | ${IJOB_AUTH_USERNAME:} | STATIC 或首次 DATABASE 引导账号；数据库模式日常账号通过用户页面管理。 |
| C073 / `app.auth.password-hash` | C / secret | ${IJOB_AUTH_PASSWORD_HASH:} | BCrypt 密码哈希，不是明文密码；按改密流程生成。 |
| C074 / `app.auth.bootstrap-enabled` | C / boolean | ${IJOB_AUTH_BOOTSTRAP_ENABLED:false} | 仅在数据库账号表为空时受控引导首个管理员，完成后关闭。 |
| C075 / `app.security.aes.key` | C / secret | ${IJOB_CONFIG_AES_KEY:} | 加密已有资源凭据；备份必须保留原值，不能直接换随机值。 |
| C076 / `app.security.aes.key-id` | C / text | ${IJOB_CONFIG_AES_KEY_ID:local} | 与 AES 密钥对应的标识；随完整轮换方案维护。 |
| C142 / `sa-token.token-name` | C / text | ${IJOB_AUTH_TOKEN_NAME:ijob-token} | 认证请求头名称；须与客户端、代理及集群节点保持一致。 |
| C156 / `management.endpoints.web.exposure.include` | C / text-list | health,info,metrics | 管理端点暴露列表；新增端点需检查访问控制，不能公开配置秘密。 |
| C159 / `management.endpoint.health.show-details` | C / enum | never | 健康细节展示；按访问权限设计，不直接对外开放。 |
| C167 / `spring.flyway.default-schema` | C / text | ${IJOB_PLATFORM_DB_SCHEMA:${IJOB_DB_USERNAME:ijob}}; ${IJOB_PLATFORM_DB_SCHEMA:public}; ${IJOB_PLATFORM_DB_SCHEMA:dbo} | 迁移 schema；已有安装变更需制定迁移方案。 |
| C168 / `app.platform-database.schema` | C / text | ${IJOB_PLATFORM_DB_SCHEMA:${IJOB_DB_USERNAME:ijob}}; ${IJOB_PLATFORM_DB_SCHEMA:public}; ${IJOB_PLATFORM_DB_SCHEMA:dbo} | 平台 schema；按数据库产品配置，与 JDBC 和 Flyway 保持一致。 |
| C170 / `spring.datasource.hikari.schema` | C / text | ${IJOB_PLATFORM_DB_SCHEMA:public}; ${IJOB_PLATFORM_DB_SCHEMA:dbo} | 默认 schema；与平台 schema、迁移 schema 和账号权限共同调整。 |
| C177 / `agent.token` | C / secret | ${IJOB_AGENT_TOKEN:} | Agent 认证令牌；部署双方一致，镜像从私有 token 文件导入。 |
| C183 / `spring.datasource.url` | C / text | generated from database selection | 平台数据库连接；换数据库产品属于迁移，不能只改 URL。 |
| C184 / `spring.datasource.username` | C / text | user input; empty for SQLite | 数据库连接账号；与权限和数据库口令一起维护。 |
| C185 / `spring.datasource.password` | C / secret | private input; empty for SQLite | 数据库口令；在受限文件中轮换并验证连接。 |
| C206 / `IJOB_CONFIG_PATH` | C / path | required; no default | 高级 Compose 私有 YAML 路径；首次安装包使用 IJOB_HOME/application.properties。 |
| C207 / `IJOB_AGENT_TOKEN_PATH` | C / path | required; no default | Agent 私有令牌文件路径，不是令牌值。 |
| C209 / `IJOB_PLATFORM_DB` | C / enum | setup selects vendor; repository YAML fallback mysql | 原生启动器从安装元数据传入；Docker 需在 backend environment 中一致配置。 |
| C210 / `IJOB_PLATFORM_DB_SCHEMA` | C / text | PostgreSQL public; SQL Server dbo; Oracle IJOB_DB_USERNAME or ijob | 数据库专用配置的 schema 占位符；对齐 JDBC、平台及迁移 schema。 |
| C211 / `IJOB_DB_USERNAME` | C / text | ijob for Oracle schema fallback only | 在 Oracle 模板中仅作为 schema 回退，不会替代 spring.datasource.username。 |
| C225 / `name` | C / compose-field | setup-generated project; advanced ijob-v1-candidate | 项目身份；恢复或多实例必须使用独立项目名。 |
| C228 / `services.<service>.volumes` | C / compose-field | setup config/runtime binds; advanced named data volumes | 私有配置、数据和插件挂载；恢复时逐项检查源目录及权限。 |
| C229 / `services.<service>.environment` | C / compose-field | template-specific | 容器环境变量；不是宿主 shell 的自动继承，保持与配置文件一致。 |
| C243 / `secrets.<secret>.file` | C / compose-field | advanced private config/token paths | 高级 Compose 秘密文件源；限制权限，不进仓库。 |
| C244 / `volumes.<volume>` | C / compose-field | advanced backend-data / backend-b-data | 高级模板持久卷身份；勿指向旧环境或删除以重置。 |

## 数据库配置的适用范围

spring.datasource 同时用于单实例和集群节点的平台连接，不是 SQL/ETL 业务资源。具体配置边界、schema 与连接池说明见[平台数据库](platform-database.md)。
