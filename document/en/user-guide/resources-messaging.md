# Kafka and RabbitMQ resources

[简体中文](../../zh-CN/user-guide/resources-messaging.md) · [Manual home](../README.md)

## Entry and workflow

Resources → Kafka/RabbitMQ. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `Kafka bootstrapServers / acks` | Broker list and acknowledgment policy; task-specific topic/groupId depend on use. |
| `Rabbit host / port / virtualHost / username / password` | Dedicated vhost/account/endpoint with explicit queue/exchange permissions. |
| `exchange / routingKey / publisherConfirm` | Publish destination and confirmation; consumer receipt needs separate verification. |

## Use and verify

PUSH publishes database rows; ETL can consume message sources. Use dedicated topics/queues/consumers and verify three business keys, ordering needs and duplicates. Do not assume unsupported protocol features by adding arbitrary raw properties.
