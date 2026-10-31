# Kafka 与 RabbitMQ 资源

[English](../../en/user-guide/resources-messaging.md) · [手册首页](../README.md)

## 入口与操作路径

资源 → Kafka/RabbitMQ。资源创建、修改、删除、连接测试及敏感详情需要 ADMIN；三个角色均可读取允许公开的概要。禁用资源或执行节点不可达时，任务仍不能正常使用该资源。 完整动作见[角色权限矩阵](users-audits.md)。

## 配置和动作说明

名称、资源标识、启用和说明见[通用资源字段](resources.md#通用资源字段)；保存、删除和秘密保留规则见[通用资源动作](resources.md#通用资源动作)。下表初始值指新建资源；编辑时载入已有非秘密值，秘密字段默认保持不变。

### Kafka

| 字段 | 是否必填 | 界面初始值 | 显示条件 | 说明 |
| --- | --- | --- | --- | --- |
| Broker 地址（`bootstrapServers`） | 必填 | 空 | Kafka 资源 | 多个 `host:port` 用逗号分隔，最多 2048 个字符。 |
| 确认方式（acks）（`acks`） | 有初始值 | all · 所有同步副本 | Kafka 资源 | 可选 all、1（Leader 确认）或 0（不等待确认）。Broker 确认不等于消费者处理完成；0 不证明消息送达。 |

### RabbitMQ

| 字段 | 是否必填 | 界面初始值 | 显示条件 | 说明 |
| --- | --- | --- | --- | --- |
| 主机（`host`） | 必填 | 空 | RabbitMQ 资源 | Broker 主机名或地址，最多 255 个字符。 |
| 端口（`port`） | 必填（有初始值） | 5672 | RabbitMQ 资源 | 1–65535 的整数，按目标监听端口填写。 |
| Virtual host（`virtualHost`） | 可选 | `/` | RabbitMQ 资源 | 最多 255 个字符；留空时提交为 `/`。使用已授权的独立 vhost。 |
| 用户名（`username`） | 必填 | 空 | RabbitMQ 资源 | 最多 128 个字符，权限须覆盖任务实际使用的交换机和队列。 |
| 密码（`password`） | 必填，可保留已有值 | 新建为空；编辑时保持不变 | RabbitMQ 资源 | 最多 4096 个字符；支持替换，不提供清除入口。 |
| 发布确认（`publisherConfirm`） | 有初始值 | 开启 | RabbitMQ 资源 | 等待 Broker 确认，不表示消费者已处理；关闭后不能保证收到异步退回。 |

`topic`、`exchange`、`routingKey` 是具体任务的发布目标，不是资源表单字段；分别在 [PUSH](plugins/push.md) 的目标配置中填写。消费用的 `groupId` 与 Topic 在 [ETL](plugins/etl.md) 的来源配置中填写。保留这些任务级配置后，仍需独立核验消费者结果。

### 消息资源动作

| 动作 | 可用角色 | 可用条件 | 结果 |
| --- | --- | --- | --- |
| Kafka 连接测试 | ADMIN | Kafka 资源已保存并启用 | 读取集群信息，不发送消息；不证明 Topic 发布权限或所有 Worker 可达。 |
| RabbitMQ 连接测试 | ADMIN | RabbitMQ 资源已保存并启用 | 打开再关闭连接，不发送消息、不创建队列或交换机；不证明业务路由可用。 |

## 操作与结果核对

PUSH 从数据库发布，ETL 可以消费消息源；不要混淆方向。用专属 topic/queue 和消费者，验证三条消息的业务键、顺序要求和重复处理。未配置的协议能力不通过随意追加原始属性来假定支持。
