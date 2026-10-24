# 从源码构建完整包

[English](../../en/installation/source-build.md) · [手册首页](../README.md)

## 前置条件

从有访问权限的 GitHub 账号 clone，或下载指定提交的源码 ZIP，进入包含 `tools/` 的仓库根目录。原生构建工具：Python 3.11+、JDK 21、Maven 3.9.11、Node 24.14.1、pnpm 11.19.0；将工具放到 PATH，JAVA_HOME 指向 JDK。保留锁文件；首次构建需要 Maven/npm 依赖源。

## 原生包：一条命令

```sh
python tools/distribution/build.py --target native --output ../ijob-build-001
```

输出必须是不存在的新目录，父目录已存在，且在仓库外。脚本按精确源码清单复制隔离快照，执行后端/setup/Agent 打包、前端依赖安装和完整 `pnpm check`，然后组装前后端、可选 Agent/SPI、启动器及双语文档。无需手动复制 target 或 dist，也无需再编译。

| 输出 | 用途 |
| --- | --- |
| `ijob-1.0.0/` | 可直接复制和解压使用的程序目录 |
| ZIP / tar.gz、SHA256SUMS.txt | 传输与完整性核验 |
| 包内 manifest / 源码快照 | 追溯源码、工具链和打包检查结果 |
| `reports/` | 每个构建步骤的日志 |
| `build-source/` | 本轮隔离构建现场，不是另一份维护源码 |

Windows、Linux 使用同一套 Java/Web 程序与各自启动器。任务使用的 BAT、SHELL、Python 等仍必须匹配执行节点的系统和解释器。

## Docker 包

在具备 Docker Engine/Compose 的构建环境运行：

```sh
python tools/distribution/build.py --target docker --output ../ijob-docker-build-001
```

Docker 构建需要拉取构建基础镜像和依赖。输出包含离线镜像归档、镜像 ID 锁定文件和启动器；搬到目标 Linux 主机后按[Docker 安装](docker.md)启动。不要把 native 包当成 Docker 完整包。

## 失败时怎么查

先看退出错误指向的 `reports` 日志：工具版本 → 依赖下载 → Java 编译 → 前端 check → 镜像构建/导出。修复原因后另选新输出目录，保留失败现场。源码 ZIP 可构建新安装包，但不包含完整 Git 历史，不能据此声称历史数据库迁移测试通过。后端测试和浏览器测试另见[质量门禁](../development/quality-gates.md)。
