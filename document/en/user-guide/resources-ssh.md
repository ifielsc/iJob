# SSH resources and host trust

[简体中文](../../zh-CN/user-guide/resources-ssh.md) · [Manual home](../README.md)

## Entry and workflow

Resources → SSH resources. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `host / port / username` | Target host, SSH port and dedicated remote account. |
| `password / privateKey / passphrase` | Supply password or private key/passphrase for the selected authentication mode; follow secret preservation rules. |
| `hostKeyVerification / hostKey / knownHosts` | Verify fingerprints/known_hosts through a trusted channel; do not blindly accept unknown hosts. |
| `connectTimeout` | Connection timeout is separate from script execution timeout. |

## Use and verify

After connection testing, execute `echo hello-ijob` using SCRIPT/SSH and verify remote user, directory and interpreter. Investigate changed host keys instead of disabling checks. SFTP file resources also require explicit paths/permissions/trust.
