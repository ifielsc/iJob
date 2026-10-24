# 事件发布、等待与审批

[English](../../../en/user-guide/plugins/event.md) · [手册首页](../../README.md)

## 入口与前提

任务 → 新增/编辑 → 插件 `EVENT`。先准备字段表要求的资源、权限及执行节点依赖。任务公共超时、重试和发布规则见[任务](../tasks.md)。

## 字段与作用

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `action / eventCode` | 按页面选择发布、等待、审批或文件等待动作；eventCode 区分事件类别。保存发布配置，不会立即发送事件。 |
| `businessKey / correlationKey / idempotencyKey` | 分别表达业务关联、等待匹配和去重意图；按场景使用稳定值，不能所有业务共用一个键。 |
| `occurredAt / summary / payload / suppressSameInbound` | 设置发生时间、摘要、载荷；默认抑制同一入站事件回发，避免回环。 |
| `timeoutSeconds / lookbackSeconds / allowedOperatorIds` | 等待期限、历史回看（默认 0）和允许审批人；核对账号及事件权限。 |
| `fileResourceId / filePath / filePattern / recursive` | 文件等待选择独立文件资源、路径、模式与递归；默认 *、不递归。 |
| `minStableAgeSeconds / maxMatches / maxDepth / maxScannedEntries / scanTimeoutSeconds` | 默认 30 秒、100、16、100000、60 秒；限制扫描与不稳定文件，fileCaseSensitive 默认 true。 |

## 最小样例与核对

```json
{"action":"PUBLISH","eventCode":"docs.order.ready","businessKey":"order-001","summary":"Synthetic order ready","payload":{"rows":3}}
```

先用专属事件代码和样例数据，检查事件记录，再建立匹配等待。审批由具备权限的用户在审批页完成。超时、重复和不匹配事件应分别验证；不得把“等待中”当作执行失败，也不要因重复点击发布制造重复业务。

样例代码是配置片段，资源 ID/路径需替换；实际外部协议验收状态见[案例索引](../../examples.md)。
