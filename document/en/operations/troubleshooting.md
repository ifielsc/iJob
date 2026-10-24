# Troubleshoot by symptom

[简体中文](../../zh-CN/operations/troubleshooting.md) · [Manual home](../README.md)

| Symptom | Check in order | Verify recovery |
| --- | --- | --- |
| Startup failure/timeout | Java 21 → home permissions → port → bootstrap/application logs → database; timeout need not mean process exit | READY, login, node state |
| Setting ignored | Correct home → duplicate keys → command/environment overrides → restart/recreate | Actual node and small task |
| Login failure | STATIC/DATABASE → accessed node → account/required password change → audits | Fresh session and correct role |
| Accepted but not executing | Trigger → root instance → Worker/groups/allowlists → concurrency | Eligible node claims and completes |
| Schedule not firing | Valid target → individual/global switch → zone/window/calendar → SCHEDULER | Preview and real fire separately |
| Missing file/interpreter | Executing node/container path, mounts and permissions | Small case on that node |
| Duplicate writes | Retries, recovery, catch-up and target idempotency | Check target before retrying |
| Protected after DB restore | Database health, original keys, compatibility, A ready before B | Controlled restart, no coordination-table clearing |

Report version/source or artifact hash, platform, time, steps, instance/trigger IDs, sanitized error and expected/actual behavior. Do not attach complete private config, databases, tokens or passwords. Retain failed attempts and retries. Unexecuted or failed checks must not be reported as passed.
