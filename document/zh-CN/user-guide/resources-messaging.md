# Kafka 与 RabbitMQ 资源

[English](../../en/user-guide/resources-messaging.md) · [手册首页](../README.md)

## 入口与操作路径

资源 → Kafka/RabbitMQ。页面动作随当前账号权限提供；先确认操作对象及环境。

## 配置和动作说明

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `Kafka bootstrapServers / acks` | Broker 地址列表与确认策略；topic、groupId 在具体任务按用途配置。 |
| `Rabbit host / port / virtualHost / username / password` | 独立 vhost/账号与网络入口；队列和交换机权限明确。 |
| `exchange / routingKey / publisherConfirm` | 发布目标和确认设置；消费端收到仍需独立核验。 |

## 操作与结果核对

PUSH 从数据库发布，ETL 可以消费消息源；不要混淆方向。用专属 topic/queue 和消费者，验证三条消息的业务键、顺序要求和重复处理。未配置的协议能力不通过随意追加原始属性来假定支持。
