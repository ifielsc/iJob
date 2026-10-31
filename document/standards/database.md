# 数据库与迁移规范

正式新安装只使用 `db/release/<vendor>/V1__*.sql`，五库 V1 保持冻结。逻辑数据库能力版本与物理 Flyway 版本不同；包名更改不重置逻辑能力 38 或运行时协议 10。SQLite 限单实例，服务数据库的 CLUSTER 仍需共享库、一致密钥及独立节点资源。

平台表统一小写 `ifj_` 前缀与 snake_case，Flyway 表为 `ifj_schema_history`；外部 SQL/ETL 目标表不受此前缀约束。主键、唯一键、普通索引、外键使用 pk/uk/idx/fk 命名，五库同一逻辑对象尽量同名，长度限制产生的映射必须记录。保持 baseline-on-migrate=false、validate-on-migrate=true、clean-disabled=true；只对明确目标开启迁移。结构演进追加后续版本，不手工 ALTER 绕过迁移记录。

Mapper 外部值使用参数绑定，禁止直接拼接用户输入；列表使用明确安全字段和稳定排序，不为列表读取密文配置。管理 ID 使用既有逐表发号契约，不重新引入自增或一个无归属的全局计数器。多态引用、图归属/无环及业务状态边仍需应用验证，不能把外键或 CHECK 的值域约束等同于完整业务规则。状态写入带预期状态条件，避免晚到完成覆盖停止结果；审计失败不触发业务重做或通知重发。

117 份历史 SQL 及 1 份配套 Flyway 配置已退出当前应用源码树，按原始字节归档。仓库 `.repository/legacy-db.json` 固定历史提交、路径、长度与 SHA-256；`tools/ci/prepare-legacy-db.py --destination <仓库外全新目录>` 从该提交提取并逐一验证。完整 Git 历史或保留该提交的本地 bundle 是恢复来源，不依赖旧数据库。脚本拒绝现成目录与仓库内部目录。

历史专项仍使用 `-Dijob.legacy.resources=<上述目录>` 将已验证旧 SQL 加入测试 classpath，保留历史兼容与拒绝旧库直接启动的覆盖。当前产品从正式 V1 开始，已移除旧 V27 离线迁移及开发 V35/V38 转 V1 的独立工具；新空库由应用 Flyway 初始化。历史工具源码和原始证据仅作归档，不属于当前产品模块或分发资产。新应用启动不能指向历史库，不能改写历史 SQL、伪造 release 状态或随意 baseline。

数据库配置和资源身份先登记于 `runtime/<env-id>/resources.md`、`environment.yaml`，真实连接秘密留在该环境受限的 `secrets/`。每套测试使用新库/账号/数据目录，重建测试只允许作用于本轮资源。DROP 类专项必须核验独立实例身份，名称正则不能代替隔离。字段、索引、UTC 时间与 ID 规则沿用冻结 V1 和当前 mapper 契约，五库差异由对应迁移与集成验证覆盖。
