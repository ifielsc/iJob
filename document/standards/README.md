# 维护规范入口

本目录是当前规范的唯一维护位置，适用于后端、Agent、前端、数据库及部署工具。历史参考副本保存在工作区 `maintenance/references/`，只用于追溯；新增约定应更新本目录，不在旧工作区重复维护。

| 内容 | 当前规范 |
| --- | --- |
| Java、包名、格式、版本与 Local JAR | [Java 与插件](java-and-plugins.md) |
| 前端代码、样式、交互、主题与国际化 | [前端](frontend.md)，以及源码的[模块契约](../../develop/web/ijob/src/modules/README.md) |
| 数据库、迁移和隔离 | [数据库](database.md) |
| 工作区、环境资源与配置落点 | [工作区和环境](../operations/workspace-and-environments.md) |
| 安全、配置与发布要求 | [安全与配置](security-and-configuration.md) |
| CI、完整本地验证与后续阶段 | [基础 CI](../ci/basic-ci.md)、[隔离集成](../ci/isolated-integration.md) |

规范修改应与实现和验证同时审查；涉及配置落点、资源共享、数据库版本或协议兼容性的变化必须写明迁移方式，不能只改示例。
