# Monitor servers and Agent

[简体中文](../../zh-CN/user-guide/resources-monitor.md) · [Manual home](../README.md)

## Entry and workflow

Resources → Monitor servers. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `endpoint / protocol / token` | Dedicated Agent endpoint/protocol and matching token; exclude tokens from screenshots. |
| `connectTimeout / readTimeout / headers` | Connection/read bounds and required headers; task collection deadline is separate. |
| `osType / agentVersion` | Record actual OS/Agent version; labels alone do not establish compatibility. |

## Use and verify

Deploy the [independent Agent](../configuration/deployment-agent.md), test connectivity/collection and check metric timestamps/status before [SERVER_MONITOR](plugins/server-monitor.md) rules. The Agent is not a scheduling Worker.
