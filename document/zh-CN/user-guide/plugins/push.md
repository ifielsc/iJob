# 数据库结果推送到消息队列

[English](../../../en/user-guide/plugins/push.md) · [手册首页](../../README.md)

## 入口与前提

任务 → 新增/编辑 → 插件 `PUSH`。先准备字段表要求的资源、权限及执行节点依赖。任务公共超时、重试和发布规则见[任务](../tasks.md)。

## 字段与作用

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `sourceResourceId / sql` | 数据库源与查询；只选择需发送的字段，避免无界查询。 |
| `target.resourceId / type / properties` | KAFKA 使用 topic；RABBITMQ 使用 exchange/routingKey，匹配消息资源类型。 |
| `serializer / compression` | 默认 json / none，必须与消费端契约一致。 |
| `fetchSize / batchSize / maxInflight / publishTimeoutSeconds / retryTimes` | 默认 500 / 500 / 500 / 60 秒 / 3；插件重试与任务重试是不同层。 |
| `maxMessageBytes / maxMessages / maxTotalPayloadBytes` | 默认 1 MiB / 1000000 / 1 GiB；同时受 Broker 限制。 |

## 最小样例与核对

```sql
SELECT id, amount FROM orders ORDER BY id;
```

使用专属测试 topic/queue 和消费账号，先推送三条，核对消费端数量、业务键和内容。PUSH 是发业务消息，不是发平台邮件通知。确认/重试可能产生重复，消费端按业务键幂等；本手册不将未连接真实 Broker 的表单截图当作投递验证。

样例代码是配置片段，资源 ID/路径需替换；实际外部协议验收状态见[案例索引](../../examples.md)。
