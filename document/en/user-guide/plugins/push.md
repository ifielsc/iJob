# Push database results to messaging

[简体中文](../../../zh-CN/user-guide/plugins/push.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `PUSH`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Page field/action | Purpose and rules |
| --- | --- |
| Database resource / Query SQL | Database source/query; select only necessary fields and avoid unbounded reads. |
| Target resource / Type / Topic or exchange and routing key | KAFKA uses topic; RABBITMQ uses exchange/routingKey. Match the messaging resource type. |
| Serialization / Message compression | Defaults json / none; match the consumer contract. |
| Fetch size / Publish buffer / In-flight window / Window timeout / Publish retries | Defaults 500 / 500 / 500 / 60 seconds / 3; plugin retries differ from task retries. |
| Message size / Message count / Total payload limits | Defaults 1 MiB / 1000000 / 1 GiB; Broker limits also apply. |

## Minimal example and verification

Prepare the dedicated resources above, fill the form, save, then trigger manually and verify the instance, outputs and target system as described below. Saving alone does not execute.

| Setting | Value to enter |
| --- | --- |
| Database resource | Select the test DB containing three sample rows |
| Target type / Resource | KAFKA / Dedicated test messaging resource |
| Topic | `docs-orders` (create in advance with a test consumer) |
| Serialization / Compression | json / none |

```sql
SELECT id, amount FROM orders ORDER BY id;
```

Use a dedicated test topic/queue and consumer. Send three rows first, checking consumed count, business keys and payloads. PUSH sends business messages, not platform email notifications. Confirmation/retry can duplicate deliveries; consumers need idempotency. A form screenshot without a real Broker is not delivery evidence.

Replace example resources, paths and object names with values from your environment. See the [case index](../../examples.md) for actual external-protocol verification status.

## Technical key reference (optional)

These stored keys help developers and troubleshooting; they are not a task JSON blob to paste into the UI. Automatically validated metadata is not a manual form input.

| Page field/group | Stored keys |
| --- | --- |
| Database resource / Query SQL | `sourceResourceId / sql` |
| Target resource / Type / Topic or exchange and routing key | `target.resourceId / type / properties` |
| Serialization / Message compression | `serializer / compression` |
| Fetch size / Publish buffer / In-flight window / Window timeout / Publish retries | `fetchSize / batchSize / maxInflight / publishTimeoutSeconds / retryTimes` |
| Message size / Message count / Total payload limits | `maxMessageBytes / maxMessages / maxTotalPayloadBytes` |
