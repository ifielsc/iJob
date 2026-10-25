# 单机与集群：前提和职责

[English](../../en/operations/standalone-cluster.md) · [手册首页](../README.md)

## 两类模式分别决定什么

认证模式 STATIC/DATABASE 决定账号来源；运行模式 STANDALONE/CLUSTER 决定节点协作。默认 STANDALONE 使用一套平台实例；SQLite 仅用于单实例。

CLUSTER 要求支持的共享服务数据库、兼容的程序版本/协议、相同认证与 AES/Token 配置。每个节点有独立名字、home、日志与文件目录。所有节点使用完全相同的 BCrypt 哈希字符串，不能各自为同一明文生成不同哈希。

| 角色 | 职责 | 验证 |
| --- | --- | --- |
| API | 管理入口与 API | 登录、权限、读取/触发 |
| SCHEDULER | 扫描调度 | 预览与真实触发记录 |
| WORKER | 领取与执行 | 根实例及执行节点 |
| MAINTENANCE | 后台维护 | 维护状态和日志 |

初始默认包含四种角色。拆分后仍需覆盖所需职责并评估负载。运行所需解释器、业务文件、插件和凭据必须在对应执行节点准备；共享数据库不会复制本地文件。

同宿主双进程可验证协作，不代表物理高可用。运行具有至少一次语义，外部写入使用业务幂等键；集群不能保证所有外部调用恰好一次。[切换流程](runtime-mode-change.md)说明停止顺序和回退边界。

## 每个节点独立启动

各节点分别建立[统一控制入口](../installation/instance-launcher.md)，各自保存 home 与语言；平台连接使用同一个逻辑库/schema，见[数据库说明](../configuration/platform-database.md)。控制入口不自动创建集群或复制共享密钥。
