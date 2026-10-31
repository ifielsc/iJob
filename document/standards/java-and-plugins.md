# Java 与插件规范

Java 根包为 `com.ifielsc.ijob`，Maven groupId 为 `com.ifielsc`。主工程、SPI、setup 工具和 Agent 均保持 1.0.0。使用 JDK 21、Maven 3.9.11（构建工具的 Plexus Utils 3.6.1 与 Guava 33.7.2-jre 安全补丁见 tools/ci/install-maven.ps1）；Java 格式由 google-java-format 1.24.0 AOSP 与现有换行规则统一执行，禁止通配符 import。业务源码中名为 `target` 的包不得被构建产物过滤规则误删。

保持现有模块依赖方向；API 负责边界校验与安全投影，业务规则进入相应业务模块，不把数据库访问、远程调用与权限判断散落在控制器。错误使用稳定错误码和国际化消息；日志与响应不得包含凭据、连接秘密或完整密钥。取消、超时和关闭路径必须释放本轮拥有的资源。

使用构造器注入和 final 依赖字段，不新增字段级注入；多实现通过明确的 Qualifier 选择。Controller 返回 contract DTO，不返回 Entity；Mapper 只负责持久化，core 依赖 SPI，starter 负责装配。common/contract 不直接引入认证、分页或 MyBatis 引擎，分页转换在既有适配位置完成。Service 优先 RequiredArgsConstructor，Entity/DTO 按需 Getter/Setter，不默认使用 Data；敏感对象不得生成泄密的 toString。

普通管理 CRUD 使用 queryPage、getDetail、create、update、delete、deleteBatch；创建接口返回 201 与 Location。Mapper 使用明确的 select/insert/update/delete 语义，条件状态变更单独命名。运行态 publication、admission、trigger、retry 不强套 CRUD，不引入泛型 BaseController/BaseService 来消除少量重复。

JSON 语义按边界区分：管理 DTO 的 ID/版本定向字符串化，业务数值保持数值且 ID 拒绝小数；持久化插件配置和变量快照保留显式 null 与精确数值；外发业务 payload 遵循自身协议。不得给所有 ObjectMapper 全局添加管理 ID 或 NON_NULL 策略。错误 code/key 稳定且双语占位符一致，原始 message 与 displayMessage 分离，历史未知元数据安全降级。

日志采用参数化 SLF4J，保留可定位但脱敏的 cause、traceId 与运行引用，不能只记异常 message。系统和实例技术日志使用英文，用户数据保持原文。必要代码注释用简洁英文说明线程、事务、取消、资源归属、null、时间单位和幂等契约；不机械增加注释，不修改第三方声明和工具指令，不为翻译改写历史 SQL。

状态变更集中到状态机服务；多实例以数据库为最终事实，JVM 缓存不能替代一致性。线程池必须有界且有名称、拒绝策略、监控与关闭方式；中断恢复标记，取消与超时分开处理，远程调用不占用长事务锁。

Local JAR 使用 Maven SPI 1.0.0 与 `IJob-Plugin-Api-Version: 1`。插件编译依赖新的 SPI，宿主提供接口；禁止把平台类打入插件。校验同时禁止 `com.ifielsc.ijob`、`com.ifelio.ijob` 和 `com.ifeel.ijob` 平台包。旧包名二进制不能直接复用，应修改 import、重新编译和上传，并重新登记入口类与制品哈希。新协议号 1 不表示兼容旧命名空间曾经使用的协议号 1。

变更通过统一格式、quality 编译与测试入口验证。不能以跳过失败用例、缩小选集或删除历史迁移测试来实现通过；对预期真实消息源跳过项单独记录。版本字段分别遵守其语义，不因包名变化重置数据库逻辑能力或运行时协议计数。
