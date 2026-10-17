# Docker 交付准备

2026-10-17 的完整交付包首次启动器使用 `ijob-backend:1.0.0-setup.3`、`ijob-web:1.0.0-ui.1`。默认 STATIC，bootstrap=false 显式生成；定时、ETL/批量 ETL 开启，本地 JAR、事件接入 API 关闭。初始化器保留既有配置、密码哈希和 AES 密钥。步骤与切换 DATABASE/多实例见[启动说明](../first-run/README.md)；验收原件保存在维护工作区，不随源码仓库分发。本目录高级构建/Compose 模板及历史三镜像证据有自己的标签范围，不能将旧镜像当作当前简单启动器的默认镜像；已有数据不变。

1.0.0 的 Linux amd64 三个镜像已在新的 Ubuntu 24.04 LTS 虚拟机实际从源码构建。基础镜像 digest 固定于 `images.lock.json` / `images.env`；最终应用镜像 ID、制品哈希、测试和剩余门禁记录在维护工作区的独立验收材料中。本轮仅在新虚拟机与隔离数据库验收，现有 QA/cluster 未切换，未推送镜像。

## 文件与构建

- 后端：[Dockerfile](../../api/ijob/Dockerfile)，构建上下文 `develop/api/ijob`；JDK 21、Maven 3.9.11，多阶段构建，运行 UID/GID 10001。
- 前端：[Dockerfile](../../web/ijob/Dockerfile)，上下文 `develop/web/ijob`；Node 24.14.1、pnpm 11.19.0、frozen lockfile 和 pnpm 补丁；运行 nginx 非 root UID/GID 101，端口 8080。
- Agent：[Dockerfile](../../api/ijob-monitor-agent/Dockerfile)，上下文 `develop/api`，先构建本地 common/contract，再独立构建 Agent；运行 UID/GID 10001。
- 三个上下文均使用 `.dockerignore` 白名单；不允许把工作区根目录、backup、outputs、local-secrets、数据库、构建缓存或真实配置作为构建上下文。运行镜像只复制 JAR/静态文件和健康检查类，测试源码不进入运行镜像。

基础镜像以 digest 固定。Maven 构建镜像的 JDK 实际补丁号、Linux 包/SBOM/漏洞与依赖制品摘要须在构建主机验收并入清单；固定基础镜像不代表全部依赖可离线重现或通过发布审计。构建使用 `-DskipTests`，不能代替五库与统一候选测试。

先把 `examples/application.<vendor>.yaml` 复制到源码树外的受限目录，填写新目标连接、BCrypt 管理员哈希和 Base64 编码的 32 字节 AES 密钥。没有默认密码。Compose 直接将完整配置文件作为 secret 挂载，并由 Spring 的配置导入读取；没有虚构 `_FILE` 支持。不要把秘密写入 `images.env`、Dockerfile 或镜像 build args。文件型 Compose secret 的宿主权限需使容器运行 UID 可读，不依赖本地 Compose 自动替你 chown。

以下命令供构建环境使用。prepare.ps1 默认只验证配置，`-Build` 只构建，不 up、push、清理或部署：

```powershell
./prepare.ps1 -Database mysql -ConfigPath C:/private/ijob/application.yaml
./prepare.ps1 -Database postgresql -ConfigPath C:/private/ijob/application.yaml -Cluster -Build
```

Linux 可使用相同 Compose 文件与 `--env-file images.env`，设置 `IJOB_CONFIG_PATH`、`IJOB_PLATFORM_DB`，执行 `docker compose ... config --quiet` / `build`。Linux 对应路径必须是 Linux 主机可访问的私有路径；当前 Windows JDBC/文件路径不能直接复制进容器。模板不含数据库服务，不把现有数据库生命周期绑定到 Compose。

## 运行契约

`compose.yaml` 是单机应用＋外部数据库＋静态前端；`compose.cluster.yaml` 为四种服务器数据库提供第二节点、独立工作卷、唯一实例名与双上游代理。SQLite 显式拒绝 Cluster，单个文件只允许一个应用写入者。Compose 单宿主双进程不是物理 HA。首次启动由 A 完成 V1 初始化并达到 healthy 后再启动 B，避免 B 将未完成的 DDL 误当成可用库；数据库边界守卫保持拒绝不完整历史。

容器使用 `container` profile，避免启用开发默认 `local`/无认证配置。五库模板开启 Flyway、持久化协调器和调度，以符合当前运行守卫；只能指向确认过的空新库或已批准的正式 V1。历史源库由边界守卫拒绝，转换目标 REVIEW_REQUIRED 也会拒启；**不能用本模板绕过转换审核**。现有 QA/cluster 数据、密钥、配置和资源端点不得用于 Docker 演练。

MySQL 示例要求服务器证书验证，PostgreSQL 为 verify-full，SQL Server 为有效证书验证；实际证书/信任库单独只读挂载。Oracle 示例为外部服务基本 URL，生产环境的 TCPS/钱包、Oracle 驱动许可和服务名必须另行核定。所有外部主库都需完成运行权限和版本核验；模板不负责自动建用户或授予权限。

后端只写 `/var/lib/ijob` 持久卷和 `/tmp` 临时卷，根文件系统只读。SQLite/JNA 从临时目录加载本地库，因此后端及 Agent 的 tmpfs 使用 `exec,mode=0700,uid=10001,gid=10001`，仅运行用户可读写；不能改成默认 noexec 后仍宣称 SQLite 可用。SQLite 数据库位于 `/var/lib/ijob/platform.db`，备份需使用一致备份机制或停写后连同 WAL 状态核验，不能只复制正在写入的主文件。已验证容器重建保留历史，以及 SQLite 停写备份恢复到新卷后历史、AES 密文资源与本地文件可用；磁盘满和真实生产备份调度仍需目标环境验收。默认容器内没有 Python、PowerShell、宿主命令或本地 JAR；SCRIPT 解释器、FILE/LOCAL_JAR 目录与 SPI 2 依赖应做专用派生镜像/明确挂载，再验收。不得挂 docker.sock 或使用 privileged 补齐能力。

后端健康检查访问 `/actuator/health/readiness`；前端 `/healthz` 只表示静态代理存活。Compose `service_healthy` 只控制启动顺序，Docker unhealthy 不会自动重启进程，也不会自动从 nginx 摘流。双上游 nginx 提供被动失败处理，未开启非幂等请求重试；发布前还需接入和验证按 readiness 摘流的外部负载均衡，以及 SSE、上传、退出排空与超时。不将基础模板宣称为完整生产负载均衡方案。

配置提供 120 秒后端停止宽限、内存/CPU 上限、只读根目录、cap_drop、no-new-privileges 和日志轮转；已执行普通停止、强制停止单节点、幸存节点继续运行、重加入、容器重建保留数据等隔离验收；不等于任意在途业务均已验证。JRE 使用 UTC，调度业务时区独立，默认沿用应用配置。

## Agent

`compose.agent.yaml` 需与主 Compose 一起使用；`IJOB_AGENT_TOKEN_PATH` 指向源码树外的 token 文件（无首尾空白/换行），通过 Spring configtree 映射 `agent.token`。健康检查读取该文件并用 Bearer 头访问 ping，不将 token 放进命令行。Agent 不映射宿主端口，应用资源端点使用 `http://agent:8081`。

容器 Agent 仅代表容器可见的进程/网络/文件系统范围，不等同宿主机 Agent；没有 host PID、privileged 或宿主根目录挂载。宿主指标继续使用独立宿主 Agent。本轮 Agent 30 项回归及 Linux 容器鉴权/采集/健康检查通过；现有 Windows/Linux 宿主 Agent 未部署新包。

## 隔离环境与交付边界

隔离验收主机、账户、SSH 密钥和临时隧道属于维护环境，未收入源码仓库。请在自己的构建主机配置数据库网络和代理，不复制验收环境的配置或凭据。

默认标签为 `1.0.0`；可显式设置 `IJOB_IMAGE_TAG`。运行配置仍必须放在源码树外。交付包包含脱敏模板与检查清单，不包含数据库、私钥、真实密码或本机代理。首发认证范围为 Linux amd64，未测试 ARM64。平台需 JDK 21；容器内解释器/插件和外部数据库不是自动附带的基础设施。

未完成的目标环境项目包括生产 TLS 与 readiness 负载均衡、SSE/上传/插件专用镜像、磁盘/主库故障、正式告警接收与责任签收、最新数据切换及服务数据库恢复。已执行项目以报告对应的结果与制品哈希为准。

参考：[Docker Compose services](https://docs.docker.com/reference/compose-file/services/)、[Compose secrets](https://docs.docker.com/reference/compose-file/secrets/)、[nginx 非 root 镜像](https://github.com/nginx/docker-nginx-unprivileged/blob/main/README.md)。


## 1.0.0 验收配置与容量参考

最终镜像：后端/Agent 为 JRE 21.0.12.1，前端 nginx 1.30.6、Alpine 3.24.2、zlib 1.3.2-r1。基础镜像 digest 见锁文件。最终后端的五库单机/四库双节点 9/9 通过；前端静态文件 512 项生产浏览器检查通过，代理 SSE/上传/GET 切换/POST 不重复在最终运行层另行通过。镜像 scan 和精确制品绑定见报告。

MySQL 短时容量基线使用后端 4 CPU/2GiB、Hikari 48、`app.runtime.work-queue.max-inflight-roots=64`，trigger/job/task 的 core-size/max-size 各 64、queue-capacity 各 1000。20 个提交客户端下验证 50 同时任务及约 20 触发/秒；新库低数据量 API 20 并发 p95 194.804ms/p99 244.493ms。这个配置是明确记录的单次基线，不自动替换默认上限，也不是 SQLite 或所有业务的容量保证。生产需依据实际任务时长、历史数据量和数据库可承受连接数配置。

正式 HTTPS 入口的剪贴板功能需浏览器安全上下文；测试使用自签证书并仅在测试浏览器忽略证书错误，不向生产提供这一设置。SQLite 恢复测得 50.578 秒仅代表停写隔离演练；生产的 RPO≤15min/RTO≤30min 仍需备份调度和目标主库实演。
