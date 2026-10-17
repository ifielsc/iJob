# iJob 1.0.0

任务调度与执行平台，包含 Java 后端、Vue 前端、监控 Agent、首次初始化和离线迁移工具。私有仓库 [ifelio/iJob](https://github.com/ifelio/iJob) 已建立，默认分支 `main`，目前仅所有者使用。2026-10-18 首次推送提交 `92eed059e605`，2,190 个文件已核对；日常开发与同步步骤见[维护说明](document/repository/private-repository.md)。

## 源码与工具链

| 目录 | 内容 |
| --- | --- |
| `develop/api/ijob` | Maven 主工程及 setup / migration 工具 |
| `develop/api/ijob-monitor-agent` | 独立监控 Agent |
| `develop/web/ijob` | 前端源码、单元/浏览器/集成测试、依赖锁和补丁 |
| `develop/deployment` | 原生启动器、Docker 构建/Compose、配置示例 |
| `tools/repository`、`.repository` | 入库白名单、检查和干净目录导出工具 |

后端使用 JDK 21、Maven 3.9.11；前端使用 Node 24.14.1、pnpm 11.19.0。先在 `develop/api/ijob` 执行 `mvn -DskipTests install`，再在 Agent 目录执行 `mvn -DskipTests package`。构建命令跳过测试，只生成制品，不代表质量门禁通过。主工程的 starter、setup-tool、migration-tool 和 Agent 的版本均为 1.0.0。

前端执行 `pnpm install --frozen-lockfile` 和 `pnpm check`；开发代理、隔离测试变量及命令见[前端说明](develop/web/ijob/README.md)。后端质量入口为 `develop/api/ijob/tools/verify-backend.ps1`，使用新 RunId；Java 格式工具为 google-java-format 1.24.0 AOSP，Windows 脚本要求 `JAVA_HOME`。其他系统可使用 Maven 的 quality profile，数据库集成测试须单独配置隔离资源。

## 源码与交付的区别

基础 GitHub Actions 与本地统一检查入口见 [CI 说明](document/ci/basic-ci.md)。工作流只检查和构建，不推送镜像或部署；真实后端浏览器集成、容器运行及五库验收另行接入。

后续开发在连接 `origin` 的 Git 工作目录中进行，以 `git rev-parse --show-toplevel` 确认根目录。维护电脑上的精确路径记在外层工作区入口；旧外层 develop 树、冻结 release 包和数据目录不作为日常开发位置。

当前完整交付包名称仍为 `release-1.0.0-20261017-setup-guide`，保存在维护工作区的 outputs 下，没有上传为 GitHub Release。源码更新不会自动更新其中 JAR、镜像、静态文件和源码压缩包；重新发布必须另建候选、执行对应门禁并记录提交号。

## 启动与数据库

完整交付包的初始化、STATIC / DATABASE 切换、MySQL 等数据库和多实例步骤见[首次启动说明](develop/deployment/first-run/README.md)。源码仓库不附带交付二进制和运行数据，不能把克隆目录直接当作已组装的 release 包；Docker 从源码构建见[Docker 说明](develop/deployment/docker/README.md)。

默认认证保持 STATIC，`app.auth.bootstrap-enabled=false`。真实配置由初始化器生成到源码树外，或由部署者按模板在受限目录准备；源码中的 `application-local.yml` 明确排除，没有随仓库分发的默认账户/密码。生产连接、AES 密钥和 Agent token 不进入源码或镜像构建上下文。

首次安装使用冻结 V1；旧迁移链仍随源码保留，不能通过改写历史 SQL 或随意 baseline 绕过版本边界。服务器数据库可按文档配置 CLUSTER，SQLite 仅单实例；同宿主双进程不等于物理高可用。

## 入库边界

[私有仓库维护说明](document/repository/private-repository.md)列出入库范围、排除项、扫描和复验命令。根 `.gitignore` 按逐文件白名单生成；新增文件必须先审查再登记，禁止强制添加被忽略的运行资料。

历史备份、验收证据、发布包和本机环境清单保留在维护工作区，不纳入初始源码。仓库尚未选择开源许可证；私有准备不表示授权公开或第三方素材/数据库驱动已经完成公开再分发审核。
