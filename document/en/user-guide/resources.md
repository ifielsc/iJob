# Resources and secret fields

[简体中文](../../zh-CN/user-guide/resources.md) · [Manual home](../README.md)

## Entry and workflow

Resources → resource type. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `code / name / enabled / description` | Stable code, display name, enabled state and purpose; tasks reference resource IDs. |
| `连接配置` | Supply type-specific endpoints/protocol/accounts/roots; connectivity is from API/Worker environments. |
| `秘密保留/替换/清除` | Editors do not return stored secret plaintext; unchanged retains, new input replaces, explicit clear removes. |
| `测试连接` | Performs real external access with permission; success does not prove business SQL/write/consumer privileges. |

## Use and verify

Create an independent resource before selecting it in tasks. Shared endpoint changes/disabling affect all consumers; inspect references first. See database, SSH, service, file, messaging and monitor chapters. Exports exclude credentials; fill them in the target environment.
