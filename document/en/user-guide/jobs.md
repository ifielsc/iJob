# Workflows: composition, publication and versions

[简体中文](../../zh-CN/user-guide/jobs.md) · [Manual home](../README.md)

## Entry and workflow

Workflows → New workflow. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `名称 / 说明 / 启用 / 超时 / 分组` | Shared business metadata, optional timeout in seconds and groups for root-job claims. |
| `添加任务 / 子作业 / 网关` | Execution nodes reference published definitions; gateways choose conditional routes. |
| `连线 / 自动布局` | Arrows point downstream; layout/routing preferences do not change dependency semantics. |
| `校验 / 保存并发布` | Validate then review references. Publication takes current child versions transactionally; preflight does not lock them. |
| `历史版本 / 子拓扑` | History is a read-only snapshot unaffected by later child changes. |

## Use and verify

For a minimal flow, add hello/check task nodes, connect hello→check and use ALL_SUCCESS/STOP. Publish and run, then inspect the [run graph](runs.md). Multiple nodes cannot be isolated or cyclic; bounds are 1–500 nodes and 5000 edges. See [Nodes/gateways](job-nodes.md).

## Real page examples

Two-node workflow and job properties.

![Two-node workflow and job properties.](../images/manual/job-editor.png)
