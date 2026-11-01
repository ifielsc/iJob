# iJob 1.0.0

[English](README.en.md) · [中文用户手册](document/zh-CN/README.md) · [English manual](document/en/README.md)

iJob 是一个任务调度与执行平台，提供任务配置、可视化作业编排、定时与事件触发、运行监控和执行日志。包含 Java 后端、Vue 前端、可选监控 Agent，以及首次初始化工具。

## 发布状态

**1.0.0 已作为首个正式版本发布。** 从 [GitHub Releases](https://github.com/ifielsc/iJob/releases/tag/v1.0.0) 下载 Native/Docker 安装包、项目源码、匹配的第三方源码和 SHA-256 校验文件。请同时阅读发布说明中的验证范围与已知风险。官网：[https://ijob.ifielsc.com](https://ijob.ifielsc.com)（暂未上线）。源码 ZIP 是构建输入，不是可直接启动的完整运行包。

暂不提供独立在线演示，也未安排开放时间。可以通过[场景教程](document/zh-CN/examples.md)和手册中的真实界面截图了解使用流程。

## 从这里开始

1. 阅读[核心概念](document/zh-CN/concepts.md)与[部署条件和兼容性](document/zh-CN/installation/requirements.md)。
2. 选择[安装方式](document/zh-CN/installation.md)，完成[首次初始化与登录](document/zh-CN/installation/first-start.md)。
3. 跟做[快速入门](document/zh-CN/quickstart.md)，再查看[场景教程](document/zh-CN/examples.md)。

任务能力包括 SQL、脚本、HTTP API、文件传输、ETL、批量同步、数据校验、通知与 Local JAR；具体参数、前提和限制见[用户指南](document/zh-CN/user-guide/README.md)。运维与配置分别见[运维手册](document/zh-CN/operations.md)和[配置说明](document/zh-CN/configuration.md)。

## 从源码构建

前提：Python 3.11+、JDK 21、Maven 3.9.11、Node 24.22.1、pnpm 11.28.8。使用仓库或源码 ZIP，在根目录执行，输出路径必须是源码树之外的全新目录：

```sh
python tools/distribution/build.py --target native --output ../ijob-build-001
```

构建器组装后端、前端、Agent、启动工具、文档、源码及校验文件。Native 运行不需要前端构建工具，但需要 Java 21 和对应平台的启动器依赖。Docker 构建使用 `--target docker`，需要 Linux amd64 Docker 引擎；详见[源码构建](document/zh-CN/installation/source-build.md)。

打包过程会执行前端检查，但 Maven 打包跳过后端测试，浏览器及真实环境验收需单独执行。构建日志和 MANIFEST 明确记录执行范围；没有通过的门禁不能记作通过。

## 部署边界

- 配置、密钥和数据库放在源码及程序目录之外。首次初始化创建部署者自己的管理员和密钥，不附带通用默认账号密码。
- SQLite 仅支持单实例；服务器数据库与集群需按文档显式准备。同一宿主上的多个进程不等于物理高可用。
- 停止、超时和重试不会自动回滚外部系统的副作用；写入任务需要幂等设计与业务结果核对。
- 可配置的数据库或协议不代表任意版本和组合均已验证；以[兼容性说明](document/zh-CN/installation/requirements.md)和实际发行说明为准。
- 当前产品版本为 1.0.0，Local JAR 协议为 1，正式新装数据库迁移为 V1。

## 源码与开发

| 目录 | 内容 |
| --- | --- |
| `develop/api/ijob` | 后端、插件 SPI、首次初始化工具 |
| `develop/api/ijob-monitor-agent` | 可选监控 Agent |
| `develop/web/ijob` | Vue 前端、依赖锁和自动化测试 |
| `develop/deployment` | Native 启动器、Docker 模板及配置示例 |
| `document/zh-CN`、`document/en` | 双语用户与运维手册 |
| `tools/distribution` | 分发包构建与文档检查 |

开发前阅读 [AGENTS.md](AGENTS.md)、[规范入口](document/standards/README.md)和[开发说明](document/zh-CN/development.md)。[CI 说明](document/ci/basic-ci.md)区分当前文件检查、Basic、Full 和适用运行矩阵；历史测试不能自动替代最终候选验收。维护流程见[仓库维护说明](document/repository/private-repository.md)。

## 问题反馈与安全

源码仓库已公开。普通问题、一般缺陷和功能建议使用 [GitHub Issues](https://github.com/ifielsc/iJob/issues)；安全漏洞使用已启用的 [GitHub 私密漏洞报告](https://github.com/ifielsc/iJob/security/advisories/new)，提交报告需要登录 GitHub。不另设报告邮箱；正式安装包、匹配的第三方源码和校验文件见 [v1.0.0 Release](https://github.com/ifielsc/iJob/releases/tag/v1.0.0)。

普通反馈请提供版本、操作系统、部署方式、复现步骤、预期与实际结果及脱敏日志。安全漏洞请通过仓库 **Security and quality → Report a vulnerability** 私密提交，说明受影响版本、影响与复现方法；不要在公开 Issue、PR 或评论中披露漏洞细节，也不要提交真实密码、令牌、私钥、数据库或完整配置。具体流程和当前状态见 [安全报告政策](SECURITY.md)。

## 版权与许可

iJob 自有代码采用 [MIT License](LICENSE)，Copyright (c) 2026 ifielsc。第三方组件保留各自的版权与许可；实际发行包的 LICENSE、NOTICE、组件清单和适用条件共同说明分发范围。源码许可、仓库公开与成品包再分发分别审查，详见[许可与素材规范](document/standards/licensing.md)。
