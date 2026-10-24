# 监控服务器与 Agent

[English](../../en/user-guide/resources-monitor.md) · [手册首页](../README.md)

## 入口与操作路径

资源 → 监控服务器。页面动作随当前账号权限提供；先确认操作对象及环境。

## 配置和动作说明

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `endpoint / protocol / token` | 独立 Agent 地址、协议及匹配 token；token 不展示到截图。 |
| `connectTimeout / readTimeout / headers` | 连接/读取时限与必要请求头；任务采集期限另计。 |
| `osType / agentVersion` | 记录真实操作系统和 Agent 版本，不据标签推断兼容。 |

## 操作与结果核对

先部署[独立 Agent](../configuration/deployment-agent.md)，测试连接和采集；核对指标时间与状态，再配置 [SERVER_MONITOR](plugins/server-monitor.md) 规则。Agent 不承担调度 Worker 职责。
