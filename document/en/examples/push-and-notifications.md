# Business messages and run notifications

[简体中文](../../zh-CN/examples/push-and-notifications.md) · [Manual home](../README.md)

**Verification: tested in this round’s isolated environment within the scope below.**

Fields: [Push database results to messaging](../user-guide/plugins/push.md).

## Prepare, configure and run

Messaging: query three independent orders and PUSH to a dedicated Kafka topic or RabbitMQ exchange/routingKey using json/none and small bounded batches. Verify three business IDs/payloads at the consumer and deduplicate retries by ID.

Notifications: separately configure a test mail channel, rule and task binding. Preview recipients/language/template, then use a local receiver or dedicated mailbox and verify records plus receipt. PUSH success does not imply email success. Without a Broker/mail receiver, only configuration checks are established; no business notifications are sent by this documentation exercise.

## Scope verified this round

Dedicated Kafka and RabbitMQ receivers checked three business messages each. Messaging ETL wrote PostgreSQL; read-only target failures preserved Kafka offsets and requeued RabbitMQ messages. After repair, consumption and replay left three rows totaling 600. A separate SMTP sink captured test, task-failure and monitoring-alarm MIME messages, plus offline failure and a manual send after recovery. This is not an end-to-end exactly-once claim; SMTP TLS/authentication and automatic notification retries remain separate.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
