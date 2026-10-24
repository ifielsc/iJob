# 接口调用

[English](../../../en/user-guide/plugins/api-call.md) · [手册首页](../../README.md)

## 入口与前提

任务 → 新增/编辑 → 插件 `API_CALL`。先准备字段表要求的资源、权限及执行节点依赖。任务公共超时、重试和发布规则见[任务](../tasks.md)。

## 字段与作用

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `resourceId / path / method` | 服务资源提供基础地址和认证；填写相对路径与方法，默认 POST，读取样例明确选 GET。 |
| `headers / body / webService` | 按协议填写请求头、请求体或 WebService 参数；已保存敏感头按保留/替换规则维护。 |
| `maxResponseBytes` | 默认 1 MiB，限制读取响应体；不要为返回大文件无限增加。 |
| `extractors / successCondition` | 提取器填写 variable 与 JSONPath expression；业务成功条件和 HTTP 状态分别核对。 |
| `remoteStop` | 仅在服务支持明确的远程取消协议时配置；停止本地等待不保证远端业务停止。 |

## 最小样例与核对

```json
{"path":"/orders/summary","method":"GET","extractors":[{"variable":"total","expression":"$.total"}]}
```

此片段需补真实服务资源 ID。先测试连接，再调用只读接口，核对响应、提取变量及业务成功条件。HTTP 连接成功不等于业务成功；网络超时可能发生在远端已提交之后，重试前使用幂等键或查询结果。

样例代码是配置片段，资源 ID/路径需替换；实际外部协议验收状态见[案例索引](../../examples.md)。
