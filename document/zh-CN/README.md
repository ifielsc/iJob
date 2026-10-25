# iJob 1.0.0 用户手册

[English](../en/README.md) · [手册首页](README.md)

## 按目标开始

| 目标 | 阅读路线 |
| --- | --- |
| 首次安装/打包 | [安装入口](installation.md) → Windows/Linux/Docker → [快速入门](quickstart.md) |
| 修改平台参数 | [配置入口](configuration.md) → 分组、插件、容量等主题 → [254 项索引](configuration-catalog.md) |
| 配置和使用页面 | [页面手册](user-guide/README.md) → [11 种插件](user-guide/plugins/README.md) → [16 组案例](examples.md) |
| 管账号、切模式、备份升级 | [运维入口](operations.md) → 对应变更流程 |
| 修改源码/准备候选 | [开发验证](development.md) → [后续事项](development/release-checklist.md) |

## 术语

任务是单个插件动作；作业组合任务/子作业及依赖；保存发布定义版本；触发是一次执行请求；实例是实际运行。资源保存外部连接和秘密；参数模板描述运行输入。节点角色与账号角色不同，执行分组也不是用户组。

程序包与 IJOB_HOME 分开；后者包含配置、秘密和数据。文档截图使用独立真实环境和合成数据，中文/英文分别采集；具体案例标注验证范围。GitHub 当前保持私密。[已确认目录与范围](documentation-plan.md)保留组织原则。
