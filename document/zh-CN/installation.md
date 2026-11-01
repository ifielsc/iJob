# 安装与启停

[English](../en/installation.md) · [手册首页](README.md)

## 程序包获取状态

从 [v1.0.0 Release](https://github.com/ifielsc/iJob/releases/tag/v1.0.0) 下载完整 Native 或 Docker 包及校验文件；核对 SHA-256 后再解压。请先阅读[版本说明](releases.md)中的验证范围与已知风险，再选择安装方式。

## 先选择安装方式

拿到完整程序包后，按操作系统和运行方式选择指南。安装机器无需再次编译程序；脚本任务使用的解释器和业务依赖按需另行准备。

| 安装方式 | 指南 | 前置条件 |
| --- | --- | --- |
| Windows 原生 | [Windows](installation/windows.md) | Java 21 与任务解释器 |
| Linux 原生 | [Linux](installation/linux.md) | Java 21、Bash、flock 与任务解释器 |
| Linux Docker | [Docker](installation/docker.md) | Linux Docker Engine、提供 `docker compose` 命令的 Compose 插件、Bash |

Native 与 Docker 成品均不包含应用源码归档。安装和启动不需要源码包；开发或审阅源码时，另取同一版本的 `ijob-1.0.0-source.zip`，具体见[源码构建](installation/source-build.md)。

下载后先用 Release 的 `SHA256SUMS.txt` 校验成品归档；解压后再核对包内 `MANIFEST.json` 和 `SHA256SUMS.txt`。包内 `source-manifest.json` 仅记录源码文件哈希，独立源码包的名称和哈希见 `MANIFEST.json` 的 `source_archive`。版本号和文件名不能证明两个程序包的内容相同；升级时保留原包，并将新包解压到新的程序目录。

## 程序和数据分开

```text
programs/ijob-1.0.0/     解压后的完整程序；升级用另一个目录
data/ijob-a/             IJOB_HOME：配置、密钥、数据库、日志、安装标记
```

数据目录在源码和程序目录之外。首次启动交互创建管理员和随机密钥，默认 SQLite、STATIC 认证、STANDALONE 运行。保存管理员密码，限制数据目录访问权限。已有安装再次启动会复用配置，不覆盖它。

## 安装完成的检查

1. 启动器报告就绪；浏览器访问所配置地址并登录。
2. 按[快速入门](quickstart.md)运行一个无外部副作用的任务，查看真实实例和日志。
3. 按平台章节停止、查询状态、再次启动，确认任务与历史仍在。
4. 修改配置前阅读[配置入口](configuration.md)；部署模式或账号模式变更进入[运维流程](operations.md)。

## 推荐的日常入口

先按平台章节配置一次[实例控制入口](installation/instance-launcher.md)，以后所有启停、状态与日志命令共用保存的目录和语言。旧脚本的默认目录和变量规则也在该页说明。

需要 Windows 服务、systemd 或开机自启时，完成首次运行后进入[服务托管](installation/services.md)。交互设置与服务托管分别负责实例路径和操作系统进程管理，不代替平台参数配置。

## JDBC 驱动

SQLite、PostgreSQL JDBC 和 Microsoft SQL Server JDBC 继续随包提供。MySQL Connector/J、Oracle JDBC/NLS **不包含在应用、迁移工具或 Docker 镜像中**。数据库适配能力保留；请按厂商条款从官方渠道取得需要的驱动。数据库服务器授权与 JDBC 驱动许可分别处理。

1. 配置实例并首次启动。选择 MySQL 或 Oracle 时，setup 会保留已经生成的配置，并提示缺失驱动的目录。
2. 将可信 MySQL Connector/J JAR 或 Oracle JDBC JAR 放入 `<IJOB_HOME>/drivers/`；字符集需要时另加匹配的 Oracle `orai18n` JAR。每种数据库只保留一个驱动版本，核验上游校验和并保留许可文件。不要放进产品或源码目录，也不要作为 iJob 资产转发。
3. 再次启动。Native 启动器和服务加载此目录；自动生成的 Docker Compose 将它只读挂载到 `/opt/ijob/drivers`。容器 UID 10001 必须能读取 JAR 并访问目录，只有安装管理员应能替换驱动。

即使平台数据库使用 SQLite、PostgreSQL 或 SQL Server，ETL 资源连接 MySQL/Oracle 时也使用同一驱动目录。更换驱动前停止实例，之后重新启动。驱动存在性检查不验证数据库连通性，也不授予使用许可。

对变更前创建的实例，新增外部目录，不替换已有配置和数据。Docker 在原 `compose.yaml` 中补充该目录到 `/opt/ijob/drivers` 的只读绑定，保留其它设置。使用源码 Compose 时，将 `IJOB_JDBC_DRIVERS_DIR` 设为源码之外的绝对目录（内置驱动数据库可使用空目录），或向 `prepare.ps1` 传入 `-DriversDirectory`。集群节点应使用一致的驱动版本。

直接启动后端 JAR 时，在 `-jar` **之前**传入 `-Dloader.path=file:/绝对路径/drivers/`；路径含空格、逗号或非 ASCII 字符时使用百分号编码的 file URI。例如：`java -Dloader.path=file:/srv/ijob-a/drivers/ -jar ijob-starter-1.0.0.jar ...`。不要把驱动重新塞入发布 JAR。
