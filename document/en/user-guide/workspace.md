# Workspace, preferences and nodes

[简体中文](../../zh-CN/user-guide/workspace.md) · [Manual home](../README.md)

## Entry and workflow

Workspace/Overview. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `登录与身份` | Use the installation account; complete required password changes first. Changing URLs does not grant permissions. |
| `语言 / 主题 / 显示时区` | Use header controls for language, theme and display zone; they do not change scheduling business time. |
| `节点、角色与新鲜度` | Read node names, roles and freshness; unknown/stale does not mean zero. |
| `业务入口` | Tasks define one step, workflows combine dependencies, and the run center shows execution. |

## Use and verify

Choose language on first login and review overview/nodes. An available API page without an eligible Worker may accept triggers without executing them. See [Routing](../configuration/routing.md) for node scope.
