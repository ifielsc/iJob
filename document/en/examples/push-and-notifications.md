# Business messages and run notifications

[简体中文](../../zh-CN/examples/push-and-notifications.md) · [Manual home](../README.md)

**Verification: configuration/workflow example; this round did not complete end-to-end acceptance of its external dependencies.**

Fields: [Push database results to messaging](../user-guide/plugins/push.md).

## Prepare, configure and run

Messaging: query three independent orders and PUSH to a dedicated Kafka topic or RabbitMQ exchange/routingKey using json/none and small bounded batches. Verify three business IDs/payloads at the consumer and deduplicate retries by ID.

Notifications: separately configure a test mail channel, rule and task binding. Preview recipients/language/template, then use a local receiver or dedicated mailbox and verify records plus receipt. PUSH success does not imply email success. Without a Broker/mail receiver, only configuration checks are established; no business notifications are sent by this documentation exercise.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
