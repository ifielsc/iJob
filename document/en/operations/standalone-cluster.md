# Standalone and cluster: prerequisites and roles

[简体中文](../../zh-CN/operations/standalone-cluster.md) · [Manual home](../README.md)

## What each mode controls

STATIC/DATABASE authentication selects account sources; STANDALONE/CLUSTER runtime selects node cooperation. Default STANDALONE uses one platform instance. SQLite supports a single instance only.

CLUSTER requires a supported shared service database, compatible program/protocol versions and matching authentication/AES/Token configuration. Give every node a distinct name, home, logs and file directories. Use the identical BCrypt hash string across nodes; generating separate hashes for the same plaintext is not equivalent.

| Role | Responsibility | Verification |
| --- | --- | --- |
| API | Management and API entry | Login, authorization, reads/triggers |
| SCHEDULER | Schedule scanning | Preview and actual trigger records |
| WORKER | Claim and execute work | Root instance and executing node |
| MAINTENANCE | Background maintenance | Maintenance status/logs |

The initial default includes all four roles. After splitting roles, retain required coverage and assess load. Prepare interpreters, business files, plugins and credentials on executing nodes; a shared database does not copy local files.

Two processes on one host demonstrate cooperation, not physical high availability. Execution has at-least-once semantics; use business idempotency keys for external writes. Clustering cannot guarantee exactly-once external calls. See [Mode changes](runtime-mode-change.md) for ordering and rollback boundaries.
