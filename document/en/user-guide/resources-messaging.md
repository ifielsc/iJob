# Kafka and RabbitMQ resources

[简体中文](../../zh-CN/user-guide/resources-messaging.md) · [Manual home](../README.md)

## Entry and workflow

Resources → Kafka/RabbitMQ. Creating, changing, deleting or testing resources and reading sensitive details require ADMIN. All three roles can read permitted summaries. A disabled resource or one unreachable from the execution node remains unusable by the task. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

See [common resource fields](resources.md#common-resource-fields) for Name, Resource code, Enable and Description, and [common resource actions](resources.md#common-resource-actions) for saving, deletion and secret preservation. Initial values below apply to new resources; editing loads saved non-secret values and keeps existing secrets by default.

### Kafka

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Broker addresses (`bootstrapServers`) | Required | Empty | Kafka resource | Comma-separated `host:port` addresses, up to 2048 characters. |
| Acknowledgements (acks) (`acks`) | Initialized | all · All in-sync replicas | Kafka resource | all, 1 (Leader acknowledgement) or 0 (No acknowledgement). Broker acknowledgement does not prove consumption; 0 does not prove delivery. |

### RabbitMQ

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Host (`host`) | Required | Empty | RabbitMQ resource | Broker hostname or address, up to 255 characters. |
| Port (`port`) | Required; initialized | 5672 | RabbitMQ resource | Integer 1–65535; use the target listening port. |
| Virtual host (`virtualHost`) | Optional | `/` | RabbitMQ resource | Up to 255 characters; empty input is submitted as `/`. Use a dedicated authorized vhost. |
| Username (`username`) | Required | Empty | RabbitMQ resource | Up to 128 characters; permissions must cover the exchanges and queues used by tasks. |
| Password (`password`) | Required; existing value may be kept | Empty on creation; keep existing on edit | RabbitMQ resource | Up to 4096 characters. Replacement is supported; no Clear control is offered. |
| Publisher confirms (`publisherConfirm`) | Initialized | On | RabbitMQ resource | Wait for Broker confirmation, not consumption. With confirms off, asynchronous returns may be missed. |

`topic`, `exchange` and `routingKey` belong to task publication targets, not the resource form; configure them in [PUSH](plugins/push.md). Consumer `groupId` and Topic belong to [ETL](plugins/etl.md) source settings. These task-level settings still require independent verification of consumer results.

### Messaging resource actions

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Kafka connection test | ADMIN | Kafka resource saved and enabled | Read cluster information without sending messages; this does not prove Topic publication privileges or reachability from every Worker. |
| RabbitMQ connection test | ADMIN | RabbitMQ resource saved and enabled | Open and close a connection without sending messages or creating queues or exchanges; this does not prove business routing. |

## Use and verify

PUSH publishes database rows; ETL can consume message sources. Use dedicated topics/queues/consumers and verify three business keys, ordering needs and duplicates. Do not assume unsupported protocol features by adding arbitrary raw properties.
