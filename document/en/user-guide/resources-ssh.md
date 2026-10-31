# SSH resources and host trust

[简体中文](../../zh-CN/user-guide/resources-ssh.md) · [Manual home](../README.md)

## Entry and workflow

Resources → SSH resources. Creating, changing, deleting or testing resources and reading sensitive details require ADMIN. All three roles can read permitted summaries. A disabled resource or one unreachable from the execution node remains unusable by the task. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

See [common resource fields](resources.md#common-resource-fields) for Name, Resource code, Enable and Description, and [common resource actions](resources.md#common-resource-actions) for saving, deletion and secret preservation. Initial values below apply to new resources; editing loads saved non-secret values and keeps existing secrets by default.

### Connection and identity

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Host (`host`) | Required | Empty | Always | Target hostname or address, without scheme, path or username; up to 255 characters. |
| Port (`port`) | Required; initialized | 22 | Always | Integer 1–65535; use the target listening port. |
| Username (`username`) | Required | Empty | Always | Up to 128 characters; use a dedicated restricted account. |
| Authentication | Initialized | Password (PASSWORD) | New resource | Choose Password or Private key. Existing resources manage stored credentials individually without this selector. |
| Password (`password`) | Required for password authentication; retain a password or key on edit | Empty on creation; keep existing on edit | Password selected on creation; editing existing resource | Up to 4096 characters; saved values are hidden. |
| Private key file path (`privateKey`) | Required for key authentication; retain a key or password on edit | Empty on creation; keep existing on edit | Private key selected on creation; editing existing resource | A local file readable by the executing Worker, not PEM contents or a remote path. The API node performing connection tests must also read the path. |
| Private key passphrase (`passphrase`) | Optional; requires a key path when supplied | Empty on creation; keep existing on edit | Private key selected on creation; editing existing resource | Passphrase for an encrypted key, up to 4096 characters. |
| Host verification (`hostKeyVerification`) | Initialized | Managed by platform (MANAGED) | Advanced settings expanded | May select Use a known_hosts file (KNOWN_HOSTS). There is no option to skip host verification. |
| Host identity (`hostKey`) | Confirmation required in MANAGED mode | Not confirmed | MANAGED mode | Retrieval shows the key type and SHA256 fingerprint. Verify through a trusted source before confirming. The platform saves the host public key; this is not a private-key input. |
| known_hosts file path (`knownHosts`) | Required | Empty | KNOWN_HOSTS selected in advanced settings | Up to 2048 characters. Both the executing Worker and API node running connection tests need a readable file. |
| Connection timeout (milliseconds) (`connectTimeout`) | Required; initialized | 30000 | Advanced settings expanded | Integer 100–300000; separate from the SCRIPT execution timeout. |

### SSH resource actions

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Get and verify fingerprint | ADMIN | MANAGED selected; valid host, port and connection timeout | Connect to retrieve the fingerprint without sending a password. Confirm trust to store it in the draft; save the resource to apply. |
| Verify fingerprint again | ADMIN | Host identity already confirmed and editor writable | Retrieve it again; changes show the previous fingerprint. Verify before trusting a new key. |
| Test connection | ADMIN | Resource saved and enabled; valid trust and authentication | Connect over SSH from the API node and execute `true`; key and known_hosts files must be readable there. |

## Use and verify

After connection testing, execute `echo hello-ijob` using SCRIPT/SSH and verify remote user, directory and interpreter. Investigate changed host keys instead of disabling checks. SFTP file resources also require explicit paths/permissions/trust.
