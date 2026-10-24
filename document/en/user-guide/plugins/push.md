# Push database results to messaging

[简体中文](../../../zh-CN/user-guide/plugins/push.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `PUSH`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Field/action | Purpose and rules |
| --- | --- |
| `sourceResourceId / sql` | Database source/query; select only necessary fields and avoid unbounded reads. |
| `target.resourceId / type / properties` | KAFKA uses topic; RABBITMQ uses exchange/routingKey. Match the messaging resource type. |
| `serializer / compression` | Defaults json / none; match the consumer contract. |
| `fetchSize / batchSize / maxInflight / publishTimeoutSeconds / retryTimes` | Defaults 500 / 500 / 500 / 60 seconds / 3; plugin retries differ from task retries. |
| `maxMessageBytes / maxMessages / maxTotalPayloadBytes` | Defaults 1 MiB / 1000000 / 1 GiB; Broker limits also apply. |

## Minimal example and verification

```sql
SELECT id, amount FROM orders ORDER BY id;
```

Use a dedicated test topic/queue and consumer. Send three rows first, checking consumed count, business keys and payloads. PUSH sends business messages, not platform email notifications. Confirmation/retry can duplicate deliveries; consumers need idempotency. A form screenshot without a real Broker is not delivery evidence.

Snippets require your resource IDs/paths. See the [case index](../../examples.md) for actual external-protocol verification status.
