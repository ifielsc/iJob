# 服务资源与认证

[English](../../en/user-guide/resources-service.md) · [手册首页](../README.md)

## 入口与操作路径

资源 → 服务资源。页面动作随当前账号权限提供；先确认操作对象及环境。

## 配置和动作说明

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `protocol / endpoint` | 按 HTTP/WebService 协议填写基础地址；任务补相对路径。 |
| `authType / headers` | 按认证类型管理请求头；敏感头有保留/替换语义。 |
| `oauth2` | Client Credentials：tokenEndpoint、clientId、clientSecret、scopes、audience、认证方法与刷新提前量。 |
| `webService` | WSDL、namespace、serviceName、portName，需与真实服务一致。 |
| `connect/read/write/call Timeout` | 连接、读、写、整次调用分别限制；对照表单单位，不等于任务超时。 |

## 操作与结果核对

先用独立只读接口测试；密钥不放任务 body、URL 或截图。OAuth 获取 token 成功仍需业务接口授权；远端停止端点需另外设计契约。详见 [API_CALL](plugins/api-call.md)。
