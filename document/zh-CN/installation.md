# 安装、构建与启停

[English](../en/installation.md) · [手册首页](README.md)

## 先选择入口

| 手上有什么 | 下一步 | 安装机器需要什么 |
| --- | --- | --- |
| GitHub clone / 源码 ZIP | [构建一次](installation/source-build.md)，取得完整包 | 构建工具链；可在另一台构建机完成 |
| native 完整包 | [Windows](installation/windows.md) 或 [Linux](installation/linux.md) | Java 21；运行脚本需要的解释器另备 |
| Docker 完整包 | [Linux Docker](installation/docker.md) | Docker Engine、Compose v2、Linux 容器 |

`python tools/distribution/build.py --target native --output ../ijob-build-001` **会完成编译和打包**。文中的 `pnpm check`、Maven `-DskipTests` 是脚本内部步骤，不是拿到包以后还要执行的步骤。`-DskipTests` 表示本次后端打包没有运行测试；完整质量门禁用于开发/发布验收，见[质量门禁](development/quality-gates.md)。

构建得到的 `ijob-1.0.0/`、ZIP、tar.gz 与交付目录里的 native 包属于同一种完整交付形式，但不同源码或构建批次的字节和哈希可能不同。旧交付目录不会随 Git 更新自动更新。核对包内 manifest 和 SHA256SUMS.txt，不能只看文件名判断相同。

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
