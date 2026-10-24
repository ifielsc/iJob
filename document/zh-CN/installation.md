# 安装、构建与启停

[English](../en/installation.md) · [手册首页](README.md)

## 从 GitHub 源码构建

在有仓库访问权限的账号下克隆，或下载该提交的源码 ZIP 并解压。进入包含根 `README.md` 和 `tools/` 的目录。不要在源码目录内保存部署配置、数据库或输出包。

原生构建需要 Python 3.11+、JDK 21、Maven 3.9.11、Node 24.14.1、pnpm 11.19.0，工具应在 PATH 中；JAVA_HOME 指向 JDK 21。首次构建需要访问 Maven/npm 依赖源。锁文件必须保留。

```sh
python tools/distribution/build.py --target native --output ../ijob-build-001
```

输出目录必须全新，父目录应已存在。入口先按源码清单复制一份隔离构建快照，然后生成 `ijob-1.0.0/`、ZIP 和 tar.gz。`reports/` 保留构建日志，`build-source/` 保留构建现场。无需手工拷贝多个 target 或 dist，也不依赖完整 Git 历史。普通安装不需要历史数据库迁移资源。

原生构建执行前端完整 `pnpm check`；后端打包使用 `-DskipTests`，因此打包成功不等于后端或浏览器测试通过。完整门禁见[开发与验证](development.md)。构建目录中的日志可能含机器路径，发布时只选择交付包，不上传整个构建目录。

## Windows 原生

运行交付包只需要 Java 21 和系统 PowerShell，无需 Node、Maven、Python 或 Nginx。解压完整包，在 PowerShell 中进入包目录。数据目录父目录必须已存在，数据目录本身应是新目录：

```powershell
$env:JAVA_HOME = 'C:\Tools\jdk-21'
$env:IJOB_HOME = 'C:\iJobData\installation-a'
$env:IJOB_LANG = 'zh'
.\initialize.cmd
.\start.cmd
.\status.cmd
.\logs.cmd
.\stop.cmd
# 修改配置后：
.\restart.cmd
```

替换示例路径。每次管理同一安装都应使用同一个 IJOB_HOME；可将上述非秘密环境设置保存到安装目录外自己的启动快捷方式。首次输入管理员账号和密码，数据库直接回车选择 SQLite。密码不回显，至少 12 个字符、最多 72 个 UTF-8 字节；没有通用默认密码。

`start.cmd` 后台启动并等待就绪，默认访问 `http://127.0.0.1:18080`。`stop.cmd` 验证进程身份后发送 Ctrl+C，等待优雅关闭，不强杀。`status.cmd` 返回 0 表示进程存在，3 表示停止；就绪情况还需检查日志。日志查看时 Ctrl+C 仅退出查看。若系统因下载标记阻止 PowerShell，先审阅并核验包的 SHA256SUMS，再按本机策略解除这些已核验脚本的阻止，不关闭全局安全策略。

## Linux 原生

需要 Java 21、Bash、util-linux 的 flock。使用普通专用系统用户；解压 tar.gz 可保留脚本执行权限：

```sh
export IJOB_HOME=/srv/ijob-data/installation-a
export IJOB_LANG=zh
./start.sh
./status.sh
./logs.sh
./stop.sh
# 修改配置后：
./stop.sh && ./start.sh
```

父目录由管理员预先创建并授权给运行用户。首次 start 会运行初始化向导；以后保留已有配置。后台进程、PID、日志和数据属于该 IJOB_HOME。默认仅监听回环地址。需要远程访问时配置受限监听和 HTTPS 反向代理，再按实际目标环境核验，勿直接复制演示环境地址。

## Docker（Linux amd64）

源码构建只要求 Python 3.11+ 和提供 Linux amd64 构建能力的 Docker；Java、Maven、Node 在镜像内执行。需要联网拉取锁定基础镜像和依赖：

```sh
python tools/distribution/build.py --target docker --output ../ijob-docker-build-001
```

该入口构建 backend/web/agent 三镜像，导出离线镜像归档和完整包。镜像构建跳过部分测试，独立 CI/运行验收仍是发布前要求。镜像标签为 1.0.0，精确镜像 ID 记录在 MANIFEST.json；未推送任何镜像仓库。

在 Linux Docker 主机解压 Docker 包，确保数据父目录存在：

```sh
export IJOB_HOME=/srv/ijob-data/container-a
export IJOB_HTTP_PORT=18080
export IJOB_LANG=zh
./start-docker.sh
docker compose -f "$IJOB_HOME/compose.yaml" ps
docker compose -f "$IJOB_HOME/compose.yaml" logs --tail=100 -f
./stop-docker.sh
```

根据主机权限使用 sudo，并用 `sudo env IJOB_HOME=... IJOB_HTTP_PORT=... IJOB_LANG=zh ./start-docker.sh` 显式传递环境。首次自动加载缺失镜像并初始化；现有安装不覆盖。默认 SQLite 数据在本安装 runtime 目录，不创建外部数据库服务器。宿主访问 `http://127.0.0.1:18080`，容器内后端保持 8080。修改端口后用同一个 IJOB_HTTP_PORT 执行 `docker compose ... up -d`，不能仅 restart。

Docker Desktop 的 Linux 容器模式可以作为构建端；本手册运行脚本面向 Linux 主机。ARM64、Windows 容器和 Docker Desktop 文件权限行为不属于 Linux amd64 验收结论。Agent 是可选组件，基础 start 不启动 Agent。

## 验收与包内容

先核对 SHA256SUMS.txt，再初始化；确认日志出现 READY、浏览器可以登录，然后执行[快速入门](quickstart.md)。数据目录独立于程序目录，升级时保留整个数据目录和原 AES 密钥。不要把旧完整目录覆盖到新安装上。

包内包含 JAR、web-dist、启停脚本、双语 docs、LICENSE、source.zip、source-manifest.json、MANIFEST.json、SHA256SUMS.txt；Docker 包另含镜像归档。MANIFEST 明确记录实际执行/未执行的检查，以及公开发布尚未批准的状态。
