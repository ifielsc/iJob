# iJob 1.0.0 启动说明

当前新用户入口：[中文完整手册](../../../document/zh-CN/README.md) / [English manual](../../../document/en/README.md)。以下保留数据库、认证和集群的详细操作契约。新安装推荐先配置[统一实例入口](../../../document/zh-CN/installation/instance-launcher.md)（[English](../../../document/en/installation/instance-launcher.md)）：每个实例只保存一次程序目录、数据目录和语言，随后通过该控制目录中的 ijob.cmd / ijob.sh 执行 start、stop、restart、status、logs。下文直接调用旧脚本的方式保留兼容，使用时仍须确保每次 IJOB_HOME 一致。

本交付保持默认 `STATIC` 认证、`STANDALONE` 单实例。数据库向导默认 SQLite，支持选择 MySQL、PostgreSQL、SQL Server、Oracle。认证模式、数据库产品和实例模式独立配置。

本次更新显式生成 `app.auth.bootstrap-enabled=false`，为常用开关添加注释，并在输入服务器数据库 JDBC 地址前显示对应格式示例。已有安装只复用，不补写注释或覆盖配置。请使用完整交付目录。

## 1. Windows

准备 Java 21，双击 `legacy/start.cmd`。首次输入管理员账号、密码并确认，数据库直接回车选择 SQLite。启动后打开 http://127.0.0.1:18080。`legacy/initialize.cmd` 只生成配置，不启动应用、不连接数据库。

密码至少 12 个 Java UTF-16 字符单位，UTF-8 最多 72 字节，输入两遍且不回显。BCrypt 哈希、随机 AES 密钥及私有文件权限自动生成。网页由后端读取 `web-dist` 提供，无需额外安装 Node.js 或 Nginx。

默认数据目录为本包同级 `ijob-data`。自定义路径时，先确保父目录存在，再在同一 PowerShell 窗口运行：

```powershell
$env:IJOB_HOME = 'F:\iJobData\installation-a'
.\start.cmd
```

这只设置当前窗口环境；以后启动必须继续使用相同 `IJOB_HOME`。路径必须在交付目录外，不使用符号链接或目录联接。改路径不会自动迁移已有数据。停止使用 `legacy/stop.cmd` 并等待退出；`legacy/logs.cmd` 中 Ctrl+C 只结束日志查看。

## 2. Linux 原生

准备 Java 21、Bash、util-linux 的 `flock`，进入完整交付目录：

```sh
chmod +x legacy/start.sh legacy/stop.sh legacy/status.sh legacy/logs.sh
./start.sh
./status.sh
./logs.sh
./stop.sh
```

首次交互初始化，之后后台运行。`legacy/logs.sh` 中 Ctrl+C 只结束日志查看。默认数据目录为包同级 `ijob-data-linux`；可在当前终端 `export IJOB_HOME=/home/ijob/installation-a`，所有启动、停止、状态和日志命令使用同一路径。使用规范绝对路径，父目录需存在。默认监听 `127.0.0.1:18080`，远程访问可通过 SSH 转发。

## 3. Docker（Linux amd64）

准备 Docker Engine 和 Compose 插件，使用 tools/distribution/build.py --target docker 生成完整交付目录（包含 docker-images.lock 和 docker-images.compose.yaml），然后进入该目录：

```sh
chmod +x legacy/start-docker.sh legacy/stop-docker.sh
sudo ./start-docker.sh
sudo ./stop-docker.sh
```

缺少镜像时自动加载离线归档。后端镜像为 `ijob-backend:1.0.0`，前端为 `ijob-web:1.0.0`。默认数据目录为包同级 `ijob-data-docker`，主机入口为 `127.0.0.1:18080`。

```sh
sudo env IJOB_HOME=/home/ijob/installation-a IJOB_HTTP_PORT=18080 ./start-docker.sh
sudo env IJOB_HOME=/home/ijob/installation-a ./stop-docker.sh
sudo docker compose -f /home/ijob/installation-a/compose.yaml logs --tail=200 -f
```

已有 `compose.yaml` 不会自动覆盖或换镜像。修改其内容、端口或镜像后需要 `docker compose up -d` 重新协调容器；仅 `restart` 不会应用 Compose 配置变化。不要删除数据卷或目录。

## 4. 选择 MySQL 等平台数据库

先准备服务器、专用空库/schema、数据库账号及必要权限。向导中选择 `mysql`、`postgresql`、`sqlserver` 或 `oracle`，会显示对应示例：

| 选择值 | 格式示例 |
| --- | --- |
| mysql | `jdbc:mysql://db-host:3306/ijob?serverTimezone=UTC` |
| postgresql | `jdbc:postgresql://db-host:5432/ijob` |
| sqlserver | `jdbc:sqlserver://db-host:1433;databaseName=ijob;encrypt=true` |
| oracle | `jdbc:oracle:thin:@//db-host:1521/service_name` |

替换主机、端口、库名或服务名，按环境配置 TLS/证书，不把密码拼进 URL；数据库账号、密码在后续单独输入。Docker 中数据库主机必须从容器可达，容器的 `localhost` 不是宿主机。初始化阶段只检查数据库类型前缀，不测试连接；应用启动时才连接并执行 Flyway 校验/建表。

PostgreSQL 默认 schema 为 `public`；使用其他 schema 时，在应用进程环境设置 `IJOB_PLATFORM_DB_SCHEMA`，同步 JDBC 默认 schema；Docker 需将该变量写入 backend 的 Compose environment，不能只设宿主 shell。SQL Server 使用 `dbo`，要求 RCSI OFF。Oracle 使用专用账号/schema，启动前将 `app.platform-database.schema` 和 `spring.flyway.default-schema` 设为该账号对应的 schema（通常为大写），单实例额外需要 `SYS.DBMS_LOCK` 执行权限。SQLite 只支持本地文件单实例。初始化向导不负责创建数据库服务器或数据库用户。

已有安装不会再次询问数据库。需要全新安装时，使用新的空 `IJOB_HOME`。需要保留已有数据而更换数据库产品时，应单独制定迁移方案：当前首发转换工具不支持当前版本 SQLite 到 MySQL 等跨产品一键迁移。不能只改 JDBC URL；驱动、vendor、迁移位置及 `installation.properties` 中的 vendor 必须一致，Docker 还涉及 Compose 环境变量。不要删除旧目录或用初始化向导覆盖历史库。

## 5. 配置文件与常用开关

编辑数据目录中的 `application.properties`，不是 JAR 内部文件。修改前停机并备份配置、数据库及原 AES 密钥；编辑已有同名键，避免重复。全局开关需重启后生效。启动参数或环境中的同名有效覆盖项也应核对，避免多个来源冲突。

新配置在相关键上方附有英文注释，中文说明如下。已有安装不会自动得到这些注释；缺少的键可以手动添加一次。

| 配置项 | 新安装值 | 作用 |
| --- | --- | --- |
| `app.auth.mode` | `STATIC` | 单配置账号；DATABASE 才支持多账号管理和个人改密 |
| `app.auth.bootstrap-enabled` | `false` | 仅用于 DATABASE 空用户表首次建管理员 |
| `app.schedule.enabled` | `true` | 全局定时扫描；还需在页面启用具体调度并有 SCHEDULER 节点 |
| `app.notification.outbox.enabled` | `true` | 持久通知投递；集群必须开启 |
| `app.event.recovery.enabled` | `true` | 持久事件恢复，与接收新事件的入口开关不同 |
| `app.runtime.maintenance.enabled` | `true` | 运行恢复和协调；集群必须开启，不是历史数据清理 |
| `app.plugin.etl.enabled` | `true` | ETL 任务插件 |
| `app.plugin.batch-etl.enabled` | `true` | 批量 ETL 任务插件 |
| `app.plugin.local-jar.enabled` | `false` | 本地 JAR 插件执行能力 |
| `app.event.ingress.enabled` | `false` | 事件接入 API；开启后仍需登录及 EVENT_PUBLISH 权限 |
| `app.retention.enabled` | `false` | 业务历史清理，需要先配置并核对保留规则 |
| `app.retention.dry-run` | `true` | 清理只演练、不删除；与应用文件日志滚动独立 |

例如定时全局开关是 `app.schedule.enabled=true`，当前向导已默认开启。页面里的调度还需启用且满足 Cron、时区、有效期和目标条件。具体任务、资源、调度保存在数据库，通常不需要重启；仅开全局开关不会自动创建任务或调度。

### 5.1 文件里没列出的配置怎么改

生成的 application.properties 是常用项集合，不是完整配置白名单。只要当前版本已经读取/绑定这个配置项，就可以在同一个外部文件补写完整键名；没有该键时沿用内置默认值。不要修改 JAR 内文件，也不用重新运行初始化向导。

例如，想改变内置 HTTP 连接/读取超时，可在 `IJOB_HOME/application.properties` 中添加以下配置；如果同名键已经存在，就修改原行，不重复追加：

```properties
app.http.connect-timeout=10s
app.http.read-timeout=60s
```

这两个键在当前源码中存在，内置值分别为 5s 和 30s。示例是按需覆盖，不改变当前交付的默认配置。它们作用于使用平台统一 HTTP 客户端配置的调用，不代表所有插件或外部系统的全部超时。

确认支持的键、类型与单位时，查阅对应发布版本的 [application.yml](../../api/ijob/ijob-starter/src/main/resources/application.yml)、其导入的 database 配置及 [AppProperties](../../api/ijob/ijob-common/src/main/java/com/ifielsc/ijob/common/config/AppProperties.java)。其他配置类、Spring Boot/组件配置、日志 XML 也可能提供配置入口，不能只按生成文件判断是否支持。完整二进制交付中可查随包源码归档；不要用未来分支新增的键配置旧 JAR。任意自造键不会自动增加功能，拼写错误可能被忽略。

操作步骤：确认当前安装的 IJOB_HOME → 停机并保留配置/数据/原密钥的备份 → 添加或修改已支持的键 → 按原方式启动 → 核对日志、实际功能与预期值。普通外部文件修改不支持自动热加载；不要为检查配置把包含秘密的完整配置或 env 端点公开。

### 5.2 生效来源与边界

Windows/Linux 原生启动器通过 `spring.config.additional-location` 加载安装数据目录的文件；Docker 首次启动模板把同一个宿主配置挂载为 `/run/secrets/application.properties` 并加载。外部文件可覆盖包内默认值，命令行、Java 系统属性、直接绑定的环境变量或 SPRING_APPLICATION_JSON 仍可能覆盖文件。`IJOB_*` 中也有仅供 YAML 占位符或启动器使用的变量，不应把所有名称一律当成更高优先级的配置。框架加载规则见 [Spring Boot 3.5 外部配置说明](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)。

原生启动器显式设置 profile、Web 目录和日志配置位置等参数；Linux legacy/start.sh 不接受任意额外参数。若这些固定参数与文件冲突，应按启动器设计处理，不能假设在文件里改同名项一定有效，也不能假设启动器会读取 JAVA_OPTS。

| 想改的内容 | 正确位置 |
| --- | --- |
| 已支持的 app.*、server.*、spring.* 应用配置 | 安装数据目录的 application.properties；检查类型、单位和更高优先级来源，重启生效 |
| 原生日志文件大小/保留期 | 同一文件可加 `ijob.logging.max-file-size=20MB`、`ijob.logging.max-history=30`、`ijob.logging.total-size-cap=500MB`；只适用于随包原生日志 XML |
| Docker 日志轮转、端口映射、容器资源/环境 | 宿主 compose.yaml；修改后使用同一项目执行 compose up -d 重新协调，需要时重建容器，不能只改 application.properties |
| IJOB_HOME、JDK 路径、JVM 堆大小 | 启动环境或明确支持这些参数的服务/启动器；这些不是普通 Spring 应用配置 |
| 具体任务、资源、调度参数 | 管理页面和数据库；与全局开关分开 |
| 当前程序完全没有读取或支持的新功能参数 | 先开发配置绑定/行为及验证，再发布新版；仅增加配置行不会实现功能 |

Docker 修改已挂载文件后需要重启应用；若编辑器原子替换文件使容器仍绑定旧文件，需使用同一项目重新创建 backend 容器。路径/环境/Compose 配置变更也需重新协调，不删除数据目录或卷。集群的共享认证、密钥和兼容配置需按集群步骤统一调整；切换数据库产品仍按第 4 节处理，不能仅加一个 vendor/JDBC 键。

## 6. 从 STATIC 切换到 DATABASE（单实例）

适用于当前 1.0.0、没有既有集群兼容记录的单实例安装；不包含旧版本数据库升级。先停机、备份，并核对平台账号表是否为空或已有启用管理员。不要重建业务库或更换 AES 密钥。

**用户表为空时：**

1. 在 `application.properties` 将已有认证模式改为 `DATABASE`，bootstrap 改为 `true`；保留原 `app.auth.username` 和 `app.auth.password-hash`。

   ```properties
   app.auth.mode=DATABASE
   app.auth.bootstrap-enabled=true
   ```

2. 按原方式启动。系统只在用户表为空时，用配置中的账号及 BCrypt 哈希创建管理员。用原账号密码登录，完成首次强制改密，再进入用户管理。
3. 停机，将 `app.auth.bootstrap-enabled=false`，再启动，使用数据库中的新密码确认登录。此后不要再开启 bootstrap 作为日常配置。

**已有启用管理员时：** 保持 `bootstrap-enabled=false`，切换 `DATABASE` 后使用已有数据库管理员登录。STATIC 配置账号不会覆盖已有用户。用户表非空但没有启用管理员时拒启，应按账号恢复流程处理，bootstrap 不会绕过该保护。

DATABASE 且 bootstrap 关闭后，配置里的 `app.auth.username` / `app.auth.password-hash` 不参与数据库登录，也不会重置数据库密码；可以保留。单机删除这两项只是可选的凭据清理，不能称为必须步骤。STATIC 模式必须保留；已有 CLUSTER 中它们参与兼容指纹，不能随意删除或逐节点改动。

尚未建立集群且打算移除 bootstrap 凭据时，应在单机阶段完成，验证重启后再组集群。当前不提供一键认证迁移、账号恢复或集群指纹修改命令；不要通过删除数据库记录绕过启动检查。

## 7. 多实例协作（CLUSTER）

以下用于新组建集群，或从尚未建立集群兼容记录的单机扩展。SQLite 不支持 CLUSTER，先选择 MySQL、PostgreSQL、SQL Server 或 Oracle。STATIC 和 DATABASE 都能用于集群，不要求切换认证模式。

1. 在节点 A 先用 `STANDALONE` 完成空库初始化、登录以及需要的 DATABASE 首账号设置，确认日常配置后停机。已有单机切换时停止所有旧写节点，不能让 STANDALONE 与 CLUSTER 同库混跑。
2. 为每个节点准备独立的本地 `IJOB_HOME`。在同一操作系统/运行方式下，可在停机后复制 A 的安装配置和标记文件：`application.properties`、`installation.properties`、`setup.complete`、`.setup-lock`，为 B 单独建立 `runtime` 目录，并恢复相同私有权限。不复制 PID、控制锁、日志或整套运行目录。跨 Windows/Linux/Docker 的安装元数据不同，应分别准备后再统一共享配置，不能直接复制 mode。
3. 所有节点使用相同版本、同一个平台库/schema、相同认证模式、AES 密钥/key-id 和 Token 配置。STATIC 的用户名和 BCrypt 哈希须完全相同；不要每台机器各自运行向导生成新密钥/哈希后直接组集群。
4. 在各节点的 `application.properties` 设置 CLUSTER 和唯一实例名。同机原生双实例示例：

   节点 A：
   ```properties
   app.runtime.mode=CLUSTER
   app.runtime.instance-name=ijob-node-a
   server.port=18080
   ```

   节点 B：
   ```properties
   app.runtime.mode=CLUSTER
   app.runtime.instance-name=ijob-node-b
   server.port=18081
   ```

5. 普通双节点保留默认 `API,SCHEDULER,WORKER,MAINTENANCE` 全部角色。保持 Flyway、通知 Outbox 和运行维护开启。组名和执行白名单根据任务路由配置；默认向导的 `default` 组可在各节点一致保留。节点上的任务依赖文件、脚本和资源访问也需要准备，不能假设数据库会复制本地文件。
6. 用各自的 `IJOB_HOME` 先启动 A，确认就绪后启动 B，检查节点登记、跨节点登录、任务执行和停止后接管。不同机器可使用相同端口；同机端口必须不同。原生默认监听 `127.0.0.1`，跨机入口需显式设置受限内网监听地址并配置访问控制；统一访问地址可由反向代理或负载均衡提供。

Windows 可分别在两个 PowerShell 窗口设置各自 `$env:IJOB_HOME` 后运行 `legacy/start.cmd`；Linux 分别用 `IJOB_HOME=/path/to/node-a ./start.sh` 和 `IJOB_HOME=/path/to/node-b ./start.sh`，停止时同样指定对应目录。各节点日志和进程控制文件独立。

Docker 每节点还需独立 Compose 项目名和绑定目录。可用向导先为 B 生成自己的安装和 Compose（只初始化、不启动），再停机同步上述共享配置；或者审阅复制后的 Compose，逐项修改项目名及两个 volume source，不能让 B 仍挂载 A 的配置/runtime。保留容器内 `server.port=8080`，同机通过不同的 `IJOB_HTTP_PORT` 映射 Web 端口。配置文件为 0600、runtime 为 0700，容器访问的文件及 runtime 归 UID/GID 10001；安装目录及 Compose 保持宿主用户权限。

Docker 只初始化示例（先确保本包镜像已加载，父目录存在；HOME_PATH 使用规范绝对路径）：

```sh
HOME_PATH=/home/ijob/node-b
sudo mkdir -m 700 "$HOME_PATH"
sudo docker run --rm -it --user 0:0 --network none \
  --cap-drop ALL --cap-add CHOWN --cap-add DAC_OVERRIDE --cap-add FOWNER \
  --security-opt no-new-privileges \
  --mount "type=bind,source=$HOME_PATH,target=$HOME_PATH" \
  --entrypoint java ijob-backend:1.0.0 \
  -jar /opt/ijob/setup.jar init --mode docker --bundle /opt/ijob \
  --home "$HOME_PATH" --host-uid "$(id -u)" --host-gid "$(id -g)"
```

初始化 B 时选择与 A 相同的数据库产品。在启动 B 之前统一账号、哈希、AES、Token 等共享配置，保留 B 的独立 Compose 路径和项目名。例如 A/B 分别用 `sudo env IJOB_HOME=/home/ijob/node-a IJOB_HTTP_PORT=18080 ./start-docker.sh` 和 `sudo env IJOB_HOME=/home/ijob/node-b IJOB_HTTP_PORT=18081 ./start-docker.sh` 启动。

已有集群的认证、哈希或 AES/Token 配置变化可能触发兼容指纹拒启，应统一停机并使用单独验证的迁移流程；本说明不要求清空指纹。任务对外部系统的副作用仍需业务幂等，多节点接管不代表外部操作天然只执行一次。

如果只是运行两套独立系统，各自使用独立数据库、数据目录和端口，保持 `STANDALONE` 即可。

## 8. 日志与交付内容

Windows 和 Linux 原生均后台运行，通过 logs 命令查看文件日志。原生日志位于 `IJOB_HOME/logs/ijob.log`，按 UTC 日期/10 MB 滚动并压缩，同一天归档序号 `.0`、`.1`、`.2` 递增。归档最多保留 14 天、200 MB，容量限制可能提前移除旧归档；当前文件另计，清理异步。可通过 `ijob.logging.max-file-size`、`ijob.logging.max-history`、`ijob.logging.total-size-cap` 调整并重启。Docker 每容器沿用 10 MB × 5 份日志。

`support/logging-native.xml`、完整 legacy/support 目录与新入口、两个 JAR 和 `web-dist` 应完整复制。升级保留整个数据目录和 AES 密钥；原安装配置不会自动覆盖。源码归档、`source-manifest.json`、`MANIFEST.json`、`SHA256SUMS.txt` 和隔离验证证据随交付包提供。当前功能默认及使用流程以本说明为准，旧报告保留其历史验证范围。
