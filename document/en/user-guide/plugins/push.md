# Push database results to messaging

[简体中文](../../../zh-CN/user-guide/plugins/push.md) · [Manual](../../README.md)

Applies to **1.0.0**. Tasks → New/Edit → plugin `PUSH`.

## Before you start

Use ADMIN to create, configure and save tasks. ADMIN or OPERATOR can execute published definitions; ADMIN reads detailed logs and input/output variables. Resources must be enabled and reachable from the execution node. See [tasks](../tasks.md) for common timeout, retry and version settings, and the [permission matrix](../users-audits.md).

## Field reference

“Default” means the **initial value of a new 1.0.0 form**. An empty value means the field has not been filled in; required fields still need a value. Visibility follows the selected branch; conditional requirements apply only to that branch.

### Source and target

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Database resource | Required | None | Always | Current task version |
| Query SQL | Required | Empty | Always | Current task version |
| Target type | Select | KAFKA | Always | Current task version |
| Target resource | Required | None | Always | Current task version |
| Topic | Conditionally required | Empty | Kafka | Current task version |
| Exchange | Conditionally required | Empty string allowed | RabbitMQ | Current task version |
| Routing key | Conditionally required | Empty | RabbitMQ | Current task version |
| Serializer | Fixed supported value | json | Always | Current task version |
| Compression | Select | none | Always | Current task version |

- **Database resource**: Provide the database connection for querying business data.
- **Query SQL**: Use a read-only query to obtain business records to publish.
- **Target type**: Choose a Kafka or RabbitMQ target.
- **Target resource**: Provide connection and authentication for the target broker.
- **Topic**: Specify the target Kafka topic.
- **Exchange**: Specify the RabbitMQ exchange; an empty string selects the default exchange.
- **Routing key**: Specify the RabbitMQ publication routing key.
- **Serializer**: Serialize each business record as JSON.
- **Compression**: Choose uncompressed or gzip-compressed message payloads.

### Batching and limits

| Field | Required | Default | Visible when | Scope |
| --- | --- | --- | --- | --- |
| Fetch size | Has a default | 500 | Advanced settings | Current task version |
| Publish buffer size | Has a default | 500 | Advanced settings | Current task version |
| Send window size | Has a default | 500 | Advanced settings | Current task version |
| Window wait timeout (s) | Has a default | 60 | Advanced settings | Current task version |
| Message send retries | Has a default | 3 | Advanced settings | Current task version |
| Maximum message bytes | Has a default | 1048576 | Advanced settings | Current task version |
| Maximum message count | Has a default | 1000000 | Advanced settings | Current task version |
| Maximum total payload bytes | Has a default | 1073741824 | Advanced settings | Current task version |

- **Fetch size**: 1–10000。
- **Publish buffer size**: 1–10000。
- **Send window size**: 1–10000。
- **Window wait timeout (s)**: 1–3600。
- **Message send retries**: 0–10; retryTimes retries the message window, not the task-level execution.
- **Maximum message bytes**: 1024–16777216。
- **Maximum message count**: 1–100000000。
- **Maximum total payload bytes**: 1024–107374182400。

## Behavior and boundaries

- SQL must be read-only; serializer supports only json, and compression supports none/gzip.
- PUSH publishes business messages; it does not send run-result notifications.
- Publication acknowledgement does not establish downstream completion; use stable business keys for deduplication.

## Typical configuration: Publish order data to Kafka

Fill in the form below, replacing resource names with your enabled resources. This is a configuration guide, not importable JSON. Follow the tutorial linked below for data preparation and execution.

Prepare the three-row orders dataset, a dedicated empty topic and an independent consumer that can inspect records; task SUCCESS alone does not prove downstream receipt.

| Setting | Value |
| --- | --- |
| Database resource | Order business database |
| Query SQL | See code below |
| Target type | KAFKA |
| Target resource | Isolated Kafka resource |
| Topic | docs-orders |
| Serializer | json |
| Compression | none |

Query SQL：

```sql
SELECT id, amount FROM orders ORDER BY id
```

**Expected result:** An independent consumer reads three business records whose id and amount match the source; publication success alone is insufficient.

[Follow the full tutorial](../../examples/push-and-notifications.md). Save the definition before running it; inspect execution state and business results separately.

## Configuration keys

Use these keys when inspecting imported configuration, API payloads or errors. `[]` denotes a collection item; the form does not require you to enter key names.

| Category / field | Configuration key |
| --- | --- |
| Source and target / Database resource | `sourceResourceId` |
| Source and target / Query SQL | `sql` |
| Source and target / Target type | `target.type` |
| Source and target / Target resource | `target.resourceId` |
| Source and target / Topic | `target.properties.topic` |
| Source and target / Exchange | `target.properties.exchange` |
| Source and target / Routing key | `target.properties.routingKey` |
| Source and target / Serializer | `serializer` |
| Source and target / Compression | `compression` |
| Batching and limits / Fetch size | `fetchSize` |
| Batching and limits / Publish buffer size | `batchSize` |
| Batching and limits / Send window size | `maxInflight` |
| Batching and limits / Window wait timeout (s) | `publishTimeoutSeconds` |
| Batching and limits / Message send retries | `retryTimes` |
| Batching and limits / Maximum message bytes | `maxMessageBytes` |
| Batching and limits / Maximum message count | `maxMessages` |
| Batching and limits / Maximum total payload bytes | `maxTotalPayloadBytes` |

## Related pages

[Tasks and versions](../tasks.md) · [Parameters and expressions](../../reference/variables.md) · [Scenario tutorials](../../examples.md)
