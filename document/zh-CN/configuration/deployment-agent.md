# 启动变量、Compose 与独立 Agent

[English](../../en/configuration/deployment-agent.md) · [手册首页](../README.md)

## 部署入口

`IJOB_HOME` 选择私有安装目录；`IJOB_LANG=en/zh` 控制初始化提示，不改变浏览器语言；JAVA_HOME 选择 Java 21。原生 IJOB_START_TIMEOUT/IJOB_STOP_TIMEOUT 与应用执行器超时不同。首次生成后修改配置文件，不期望初始化变量自动重写已有文件。

高级 Compose 模板与完整包生成的 Compose 分开管理：前者可使用 IJOB_HTTP_BIND 等模板变量，后者以生成的实际映射、挂载、资源上限为准。修改 Compose 后从对应完整包执行 `start-docker.sh --recreate`。不要复制其他环境的项目名、目录或 secret 文件。

## Agent

Agent 是独立进程/可选镜像，不是调度 Worker。为它登记独立地址、端口、token、配置与日志；只允许需要的网络来源。原生通过独立外部配置指定 `server.port` 和 `agent.*`，令牌保存在受限文件中；镜像配置遵循其 configtree secret 挂载。平台“监控服务器资源”填写 Agent 地址和匹配 token。

`agent.hostname` 未设时自动探测；collect-timeout 单位毫秒，默认 10000，collect-concurrency 默认 2，collect-queue-capacity 默认 4。先测试连接和一次采集，核验指标、采集时间和错误状态，再用于监控任务。不要把连接成功当作规则已触发通知。

## 本主题配置索引

下列为源码/实现默认值；初始化覆盖、来源和完整说明见[完整清单](../configuration-catalog.md)。

| ID / 配置键 | 级别 / 类型 | 默认值来源 | 说明 |
| --- | --- | --- | --- |
| C178 / `agent.collect-timeout` | B / milliseconds | ${IJOB_AGENT_COLLECT_TIMEOUT:10000} | 单次采集超时，毫秒；按被监控主机负载评估。 |
| C179 / `agent.collect-concurrency` | B / integer | ${IJOB_AGENT_COLLECT_CONCURRENCY:2} | 并行采集数；结合目标主机资源评估。 |
| C180 / `agent.collect-queue-capacity` | B / integer | ${IJOB_AGENT_COLLECT_QUEUE_CAPACITY:4} | 采集等待队列容量；过大可能延迟返回。 |
| C181 / `agent.hostname` | A / text | unset: detected automatically | 上报的主机名称；不配置时自动探测，可用于明确监控对象身份。 |
| C197 / `server.port` | B / port | 8081 in image; not explicitly set in Agent YAML | Agent 镜像内部 8081；修改需同步探针和平台资源连接。 |
| C199 / `IJOB_HOME` | A / path | Windows: sibling ijob-data; Linux: sibling ijob-data-linux; Docker: sibling ijob-data-docker | 安装数据根目录；所有管理命令一致，更改路径不会自动搬迁数据。 |
| C200 / `IJOB_LANG` | A / enum | en; zh selects Chinese | 初始化/管理命令语言，不是界面偏好或通知语言。 |
| C201 / `JAVA_HOME` | A / path | unset: resolve Java from PATH | 原生启动的 Java 21 安装目录。 |
| C202 / `IJOB_START_TIMEOUT` | B / seconds | 120 | 等待启动就绪的秒数，范围 1–600；仅 Linux 启动脚本使用，Windows 当前固定等待。 |
| C203 / `IJOB_STOP_TIMEOUT` | B / seconds | 120 | 等待优雅停止的秒数，范围 1–600；仅 Linux 启动脚本使用，Windows 当前固定等待。 |
| C204 / `IJOB_HTTP_PORT` | A / port | 18080 | Docker 宿主映射端口，改变后用同一安装包 --recreate。 |
| C205 / `IJOB_HTTP_BIND` | B / text | 127.0.0.1 | 仅仓库高级 Compose 模板支持；初始化器生成的 Compose 固定回环绑定，不能只设置此变量。 |
| C216 / `JAVA_TOOL_OPTIONS` | B / text | images: -XX:MaxRAMPercentage=70 -Duser.timezone=UTC | JVM 启动参数；容器内保留 UTC 约定，结合内存限制调整。build.py 拒绝继承此变量。 |
| C217 / `IJOB_DEV_API_TARGET` | B / uri | unset: no proxy | 前端开发服务代理目标；不写入正式包的运行配置，不能误连演示环境。 |
| C227 / `services.web.ports` | B / compose-field | 127.0.0.1:<IJOB_HTTP_PORT>:8080 | 宿主绑定地址与映射端口；对外访问需配套入口和权限。 |
| C230 / `services.<service>.mem_limit` | B / compose-field | backend 1g; web 128m; agent 256m | 内存上限；与 JVM 比例和工作负载共同调整。 |
| C231 / `services.<service>.cpus` | B / compose-field | backend 2.0; advanced web/agent 1.0; setup web unset | CPU 配额；初始化 Web 未显式设置，高级模板与初始化模板需区分。 |
| C232 / `services.<service>.restart` | B / compose-field | unless-stopped | 进程重启策略；不等于任务恢复或幂等保证。 |
| C233 / `services.<service>.stop_grace_period` | B / compose-field | backend 120s; advanced web/agent 30s; setup web unset | 停止宽限；初始化 Web 未显式设置，后端应覆盖排空与关闭所需时间。 |
| C234 / `services.<service>.logging.driver` | B / compose-field | json-file | 容器日志驱动；更换时验证采集和查看方式。 |
| C235 / `services.<service>.logging.options.max-size` | A / compose-field | 10m | 每份容器日志滚动大小。 |
| C236 / `services.<service>.logging.options.max-file` | A / compose-field | backend/web 5; agent 3 | 保留容器日志文件数量。 |
| C237 / `services.<service>.tmpfs` | B / compose-field | backend 256m; web 128m; agent 64m; template permissions | 临时空间大小、权限及 exec 标志；与插件运行需要一起审核。 |
| C245 / `services.<service>.healthcheck.interval` | B / duration | backend/web 15s; Agent 30s | 健康检查周期；镜像默认值来自各 Dockerfile，覆盖后重新验证就绪判定。 |
| C246 / `services.<service>.healthcheck.timeout` | B / duration | backend/Agent 6s; web 5s | 单次健康检查超时；镜像默认值来自各 Dockerfile，覆盖后重新验证就绪判定。 |
| C247 / `services.<service>.healthcheck.start_period` | B / duration | backend 90s; Agent 30s; web not explicitly set | 启动探测宽限；镜像默认值来自各 Dockerfile，覆盖后重新验证就绪判定。 |
| C248 / `services.<service>.healthcheck.retries` | B / integer | 3 | 失败次数阈值；镜像默认值来自各 Dockerfile，覆盖后重新验证就绪判定。 |

[实例持久入口的 5 项设置（C250–C254）](../installation/instance-launcher.md)由 configure 自动生成。推荐通过该入口管理实例，已保存的目录和语言优先于终端环境变量。
