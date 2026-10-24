# 服务器监控

[English](../../../en/user-guide/plugins/server-monitor.md) · [手册首页](../../README.md)

## 入口与前提

任务 → 新增/编辑 → 插件 `SERVER_MONITOR`。先准备字段表要求的资源、权限及执行节点依赖。任务公共超时、重试和发布规则见[任务](../tasks.md)。

## 字段与作用

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `resourceId` | 选择监控服务器资源，Agent 地址和 token 在资源内维护。 |
| `collect.items / collect.applications` | 选择实际需要的系统指标与应用采集配置，必须由 Agent 支持。 |
| `rules.cpu / memory / disk / network / applications` | 按采集指标配置阈值和应用规则，注意单位、路径和缺失指标。 |
| `notify.enabled / notifyConfigId / recovery` | 是否通知、通知配置与恢复通知；先预览接收对象，不向真实业务发送演练消息。 |

## 最小样例与核对

```text
Resource: dedicated monitor Agent
Collect: CPU + memory
Notify: disabled for first exercise
```

测试连接后采集一次，核对时间、新鲜度、指标和规则结果。采集失败、指标缺失与阈值告警不同，不能把不可用显示成 0。再用独立规则样例验证告警和恢复；通知发送需要单独证据。

样例代码是配置片段，资源 ID/路径需替换；实际外部协议验收状态见[案例索引](../../examples.md)。
