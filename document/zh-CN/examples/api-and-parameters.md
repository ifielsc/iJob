# HTTP、参数与结果提取

[English](../../en/examples/api-and-parameters.md) · [手册首页](../README.md)

**验证状态：本轮隔离环境实测，范围及未覆盖项见下文。**

相关字段：[接口调用](../user-guide/plugins/api-call.md)。

## 准备、配置与执行

准备专属只读接口，`GET /orders/summary` 返回 `{"total":3,"amount":600}`；服务资源填写其基础地址与独立认证。API_CALL 选 GET、路径 /orders/summary，提取 total→`$.total`、amount→`$.amount`，设置合理响应上限。

建立参数模板 batchDate/limit（DATE 必填、INTEGER 默认 3），本次执行确认输入快照。按具体字段支持的变量绑定方式传参，不把模板名称当变量值。核对响应、输出变量、业务成功条件；用返回非成功业务码、缺失字段和超时分别验证失败。对写接口增加业务幂等键，超时后先查远端结果再重试。

## 本轮实测范围

独立 HTTP 接收器实测参数、结果提取、业务失败、缺失字段、HTTP 503、请求超时及同一业务键的重复写入去重。HTTP 503 为不可重试失败，即使设置 retryTimes 也不会自动重发；修复后手动重执行。SOAP、OAuth2 与远端停止接口另验。

## 结束检查

保留输入、定义版本、触发/实例 ID、脱敏日志和目标核对结果。只停止本案例拥有的进程或调度；其他环境和历史证据不作为清理对象。
