# Runtime diagnostics and health

[简体中文](../../zh-CN/user-guide/diagnostics.md) · [Manual home](../README.md)

## Entry and workflow

Diagnostics; workspace node details. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `节点与组件状态` | Distinguish online state, freshness, roles and scanner state. |
| `队列与容量` | Interpret waiting/in-flight/thread/connection metrics using available data; unknown is not zero. |
| `错误上下文` | Correlate timestamps, node, trigger/instance IDs and logs without copying secrets. |

## Use and verify

Establish a baseline with docs-hello, then inspect the intentional failure. Diagnostics support investigation but do not replace target-system verification. Follow [Troubleshooting](../operations/troubleshooting.md) after database recovery/node changes.
