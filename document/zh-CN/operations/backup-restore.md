# 备份与独立恢复

[English](../../en/operations/backup-restore.md) · [手册首页](../README.md)

## 备份什么

程序包/哈希、完整 IJOB_HOME、原 AES key/key-id、数据库备份以及额外业务文件/插件必须配套保留。home 内包含私密配置、installation.properties、setup.complete、runtime、日志等；限制备份读取权限，按组织要求加密，不提交 Git。

## SQLite 停机一致备份

1. 暂停调度/新触发，等待或处理在途工作。
2. 优雅停止并确认进程退出，再复制整个 home 到全新备份目录。
3. 记录程序哈希、备份时间、数据库路径和校验和。不要只复制运行中的 platform.db；WAL 状态可能尚未合并。

## 独立恢复演练

恢复到新目录、新端口；原备份和现有实例不动。原生将 spring.datasource.url 指向新 runtime/platform.db，核对绝对路径、监听端口和权限。复制来的 PID 记录不是现有进程身份，不得按其中 PID 直接杀进程。

Docker 保留内部数据库路径和 8080；为复制的 compose.yaml 使用新项目名，把两个绑定源分别改为新 application.properties 和 runtime，保持容器 UID/GID 10001 权限。使用对应包 `legacy/start-docker.sh --recreate`。

验证就绪、登录、任务/作业版本、历史实例、加密资源可读性，再运行独立无副作用任务。记录恢复耗时与差异；一次历史演练不构成生产 RPO/RTO 承诺。

## 服务数据库

用相应产品的一致备份机制，并与应用配置、原密钥、业务文件建立同一恢复点。恢复目标为新库；先禁用可能自动执行的业务入口，再验证。数据库备份不包含外部 API、邮件或消息队列已产生的副作用。
