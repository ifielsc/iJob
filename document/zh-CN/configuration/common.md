# 基础网络、语言与日志

[English](../../en/configuration/common.md) · [手册首页](../README.md)

原生入门保持 `server.address=127.0.0.1`、`server.port=18080`。确需远端访问时配置受控入口；Docker 容器内保持 0.0.0.0:8080，宿主映射另改。端口更换后检查防火墙、反向代理和浏览器地址。

浏览器语言/主题/显示时区在用户偏好中修改；`spring.web.locale` 是服务端缺省语言，`app.notification.locale=en/zh` 是通知缺省语言。两者不替代具体调度时区。

原生日志大小/天数/总量共同生效：总量上限可能提前删除未满天数的归档。HTTP 公用超时不代表所有插件自己的超时。资源浏览根目录只限制相应 LOCAL 浏览入口，不构成所有脚本的文件系统沙箱。

会话 timeout 为总有效期、active-timeout 为空闲有效期，单位秒。`-1` 是框架支持的特殊值，检查器接受其格式，但普通部署建议保留有限时长；调整需遵守本环境会话策略。

## 本主题配置索引

下列为源码/实现默认值；初始化覆盖、来源和完整说明见[完整清单](../configuration-catalog.md)。

| ID / 配置键 | 级别 / 类型 | 默认值来源 | 说明 |
| --- | --- | --- | --- |
| C008 / `spring.web.locale` | A / locale | ${IJOB_DEFAULT_LOCALE:en} | 服务端缺省语言；Accept-Language 和前端偏好另有作用。 |
| C018 / `server.port` | A / port | ${IJOB_SERVER_PORT:8080} | 原生服务端口；Docker 后端内部保持 8080，另改宿主映射。 |
| C022 / `app.resource-browser.local-roots` | A / path-list | [] | LOCAL 资源草稿允许浏览的绝对根目录；不等价于所有脚本的文件系统沙箱。 |
| C102 / `app.http.connect-timeout` | A / duration | ${IJOB_HTTP_CONNECT_TIMEOUT:5s} | 共用 HTTP 客户端连接超时；不覆盖所有插件自身的超时。 |
| C103 / `app.http.read-timeout` | A / duration | ${IJOB_HTTP_READ_TIMEOUT:30s} | 共用 HTTP 客户端读取超时；与任务总超时区分。 |
| C105 / `app.execution.trigger.core-size` | B / integer | ${IJOB_TRIGGER_POOL_CORE_SIZE:4} | trigger 执行池核心线程数；需联合评估，不等于业务并发组限制。 |
| C106 / `app.execution.trigger.max-size` | B / integer | ${IJOB_TRIGGER_POOL_MAX_SIZE:20} | trigger 执行池最大线程数；需联合评估，不等于业务并发组限制。 |
| C108 / `app.execution.trigger.keep-alive` | B / duration | ${IJOB_TRIGGER_POOL_KEEP_ALIVE:60s} | trigger 执行池非核心空闲线程保留时间；需联合评估，不等于业务并发组限制。 |
| C109 / `app.execution.job.core-size` | B / integer | ${IJOB_JOB_POOL_CORE_SIZE:4} | job 执行池核心线程数；需联合评估，不等于业务并发组限制。 |
| C110 / `app.execution.job.max-size` | B / integer | ${IJOB_JOB_POOL_MAX_SIZE:20} | job 执行池最大线程数；需联合评估，不等于业务并发组限制。 |
| C112 / `app.execution.job.keep-alive` | B / duration | ${IJOB_JOB_POOL_KEEP_ALIVE:60s} | job 执行池非核心空闲线程保留时间；需联合评估，不等于业务并发组限制。 |
| C113 / `app.execution.dag.core-size` | B / integer | ${IJOB_DAG_POOL_CORE_SIZE:8} | dag 执行池核心线程数；需联合评估，不等于业务并发组限制。 |
| C114 / `app.execution.dag.max-size` | B / integer | ${IJOB_DAG_POOL_MAX_SIZE:32} | dag 执行池最大线程数；需联合评估，不等于业务并发组限制。 |
| C116 / `app.execution.dag.keep-alive` | B / duration | ${IJOB_DAG_POOL_KEEP_ALIVE:60s} | dag 执行池非核心空闲线程保留时间；需联合评估，不等于业务并发组限制。 |
| C117 / `app.execution.task.core-size` | B / integer | ${IJOB_TASK_POOL_CORE_SIZE:8} | task 执行池核心线程数；需联合评估，不等于业务并发组限制。 |
| C118 / `app.execution.task.max-size` | B / integer | ${IJOB_TASK_POOL_MAX_SIZE:32} | task 执行池最大线程数；需联合评估，不等于业务并发组限制。 |
| C120 / `app.execution.task.keep-alive` | B / duration | ${IJOB_TASK_POOL_KEEP_ALIVE:60s} | task 执行池非核心空闲线程保留时间；需联合评估，不等于业务并发组限制。 |
| C121 / `app.execution.script.core-size` | B / integer | ${IJOB_SCRIPT_POOL_CORE_SIZE:4} | script 执行池核心线程数；需联合评估，不等于业务并发组限制。 |
| C122 / `app.execution.script.max-size` | B / integer | ${IJOB_SCRIPT_POOL_MAX_SIZE:16} | script 执行池最大线程数；需联合评估，不等于业务并发组限制。 |
| C124 / `app.execution.script.keep-alive` | B / duration | ${IJOB_SCRIPT_POOL_KEEP_ALIVE:60s} | script 执行池非核心空闲线程保留时间；需联合评估，不等于业务并发组限制。 |
| C143 / `sa-token.timeout` | B / seconds | ${IJOB_AUTH_TIMEOUT_SECONDS:1800} | 登录令牌总有效期，单位秒；按会话策略调整。 |
| C144 / `sa-token.active-timeout` | B / seconds | ${IJOB_AUTH_ACTIVE_TIMEOUT_SECONDS:300} | 空闲会话超时，单位秒；区别于总有效期。 |
| C153 / `logging.level.root` | B / enum | INFO | 全局日志级别；排障临时调整后恢复，评估日志量。 |
| C154 / `logging.level.com.ifielsc.ijob` | B / enum | INFO | iJob 日志级别；不要输出含凭据的配置来排障。 |
| C172 / `spring.datasource.hikari.data-source-properties.busy_timeout` | B / milliseconds | ${IJOB_SQLITE_BUSY_TIMEOUT_MS:5000} | 锁繁忙等待毫秒数；仅 SQLite，评估并发和持久性影响。 |
| C182 / `server.address` | A / text | generated: native=127.0.0.1; docker=0.0.0.0 | 原生监听地址；Docker 内部必须与健康检查和代理保持一致。 |
| C194 / `ijob.logging.max-file-size` | A / size | 10MB | 原生日志单文件滚动大小。 |
| C195 / `ijob.logging.max-history` | A / days | 14 | 原生日志归档保留天数。 |
| C196 / `ijob.logging.total-size-cap` | A / size | 200MB | 原生日志归档总容量，可能先于天数触发清理。 |
