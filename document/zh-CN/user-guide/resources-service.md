# 服务资源与认证

[English](../../en/user-guide/resources-service.md) · [手册首页](../README.md)

## 入口与操作路径

资源 → 服务资源。资源创建、修改、删除、连接测试及敏感详情需要 ADMIN；三个角色均可读取允许公开的概要。禁用资源或执行节点不可达时，任务仍不能正常使用该资源。 完整动作见[角色权限矩阵](users-audits.md)。

## 配置和动作说明

名称、资源标识、启用和说明见[通用资源字段](resources.md#通用资源字段)；保存、删除和秘密保留规则见[通用资源动作](resources.md#通用资源动作)。下表初始值指新建资源；编辑时载入已有非秘密值，秘密字段默认保持不变。

### 连接与认证

| 字段 | 是否必填 | 界面初始值 | 显示条件 | 说明 |
| --- | --- | --- | --- | --- |
| 协议（`protocol`） | 必填 | HTTP | 始终 | HTTP 或 SOAP；SOAP 对应技术值 WEB_SERVICE。 |
| 服务地址（`endpoint`） | 必填 | 空 | 始终 | 完整 HTTP(S) 地址，最多 2048 个字符；不能含 URL 用户名、密码或敏感查询参数。HTTP 任务在此基础上配置路径。 |
| 认证方式（`authType`） | 有初始值 | 无独立认证（NONE） | HTTP | 可选择 OAuth 2.0 客户端凭据（OAUTH2_CLIENT_CREDENTIALS）；SOAP 不显示该选择。 |

### OAuth 2.0 客户端凭据

| 字段 | 是否必填 | 界面初始值 | 显示条件 | 说明 |
| --- | --- | --- | --- | --- |
| Token 地址（`oauth2.tokenEndpoint`） | 必填 | 空 | HTTP 且选择 OAuth 2.0 客户端凭据 | 完整 HTTP(S) 地址，最多 2048 个字符；遵循服务地址的秘密参数限制。 |
| 客户端 ID（`oauth2.clientId`） | 必填 | 空 | HTTP 且选择 OAuth 2.0 客户端凭据 | 最多 512 个字符，由认证服务提供。 |
| 客户端密钥（`oauth2.clientSecret`） | 必填，可保留已有值 | 新建为空；编辑时保持不变 | HTTP 且选择 OAuth 2.0 客户端凭据 | 最多 4096 个字符，不回显旧值；替换时填写完整新密钥。 |
| 授权范围（`oauth2.scopes`） | 可选 | 空 | OAuth 选项展开时 | 按认证服务要求填写 scopes，最多 2048 个字符。 |
| 目标受众（`oauth2.audience`） | 可选 | 空 | OAuth 选项展开时 | 按认证服务要求填写 audience，最多 2048 个字符。 |
| 客户端认证方式（`oauth2.clientAuthenticationMethod`） | 有初始值 | CLIENT_SECRET_BASIC | OAuth 选项展开时 | 可选 CLIENT_SECRET_BASIC 或 CLIENT_SECRET_POST，与 Token 服务要求一致。 |
| 提前刷新（秒）（`oauth2.refreshSkewSeconds`） | 必填（有初始值） | 30 | OAuth 选项展开时 | 0–300 的整数，Token 到期前的提前刷新量。 |

### SOAP 服务描述

| 字段 | 是否必填 | 界面初始值 | 显示条件 | 说明 |
| --- | --- | --- | --- | --- |
| WSDL 地址（`webService.wsdl`） | 必填 | 空 | SOAP | 完整 HTTP(S) 地址，指向真实服务的 WSDL。 |
| 命名空间（`webService.namespace`） | 必填 | 空 | SOAP | 与 WSDL 中的服务命名空间一致。 |
| 服务名（`webService.serviceName`） | 必填 | 空 | SOAP | 与 WSDL 中的 service 名称一致。 |
| 端口名（`webService.portName`） | 必填 | 空 | SOAP | 与 WSDL 中的 port 名称一致，不是 TCP 端口号。 |

### 默认请求头

| 字段 | 是否必填 | 界面初始值 | 显示条件 | 说明 |
| --- | --- | --- | --- | --- |
| 名称（`headers` 的键） | 每行必填 | 无请求头；新增行为空 | HTTP 与 SOAP 的默认请求头表 | 最多 50 行；名称最多 128 个字符，使用合法 HTTP 头名称且不区分大小写去重。OAuth 模式不能自行配置 Authorization。 |
| 值（`headers` 的值） | 每行填写或保留 | 新增行为空；已有行保持不变 | 默认请求头表中存在该行 | 最多 4096 个字符，不含控制字符；已保存值不回显。删除整行移除请求头。 |

### 超时

| 字段 | 是否必填 | 界面初始值 | 显示条件 | 说明 |
| --- | --- | --- | --- | --- |
| 连接超时（秒）（`connectTimeout`） | 必填（有初始值） | 10 | 高级配置展开时 | 1–300 的整数，限制建立连接的等待。 |
| 读取超时（秒）（`readTimeout`） | 必填（有初始值） | 30 | 高级配置展开时 | 1–300 的整数，限制读取等待。 |
| 写入超时（秒）（`writeTimeout`） | 必填（有初始值） | 30 | 高级配置展开时 | 1–300 的整数，限制写入等待。 |
| 单次调用超时（秒）（`callTimeout`） | 必填（有初始值） | 60 | 高级配置展开时 | 1–900 的整数，限制整次调用；与任务自身超时分别配置。 |

### 服务资源动作

| 动作 | 可用角色 | 可用条件 | 结果 |
| --- | --- | --- | --- |
| 添加请求头 | ADMIN | 编辑表单可写 | 新增一行名称和值，保存后生效。 |
| 移除 | ADMIN | 对应请求头行存在且表单可写 | 删除该行；保存时移除该请求头。 |
| 连接测试 | ADMIN | 资源已保存并启用 | HTTP 发送 HEAD，可能获取 OAuth Token；SOAP 读取 WSDL。收到响应不证明认证或业务调用成功。 |

## 操作与结果核对

先用独立只读接口测试；密钥不放任务 body、URL 或截图。OAuth 获取 token 成功仍需业务接口授权；远端停止端点需另外设计契约。详见 [API_CALL](plugins/api-call.md)。
