# LOCAL, FTP, SFTP and S3 file resources

[简体中文](../../zh-CN/user-guide/resources-file.md) · [Manual home](../README.md)

## Entry and workflow

Resources → File resources. Creating, changing, deleting or testing resources and reading sensitive details require ADMIN. All three roles can read permitted summaries. A disabled resource or one unreachable from the execution node remains unusable by the task. See the [role matrix](users-audits.md) for all actions.

## Configuration and actions

See [common resource fields](resources.md#common-resource-fields) for Name, Resource code, Enable and Description, and [common resource actions](resources.md#common-resource-actions) for saving, deletion and secret preservation. Initial values below apply to new resources; editing loads saved non-secret values and keeps existing secrets by default.

### Protocol and root

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| File protocol (`protocol`) | Required | LOCAL | Always | LOCAL, FTP, SFTP or S3_COMPATIBLE; selecting a protocol shows its settings. |
| Root directory / prefix (`basePath`) | Required | Empty for LOCAL; `/` on first switch to another protocol | Always | Up to 2048 characters. LOCAL is an absolute directory on the executor; FTP/SFTP uses the account-visible remote directory; S3 uses an object prefix. Task paths are relative to this root, not a browser upload directory. |
| Connection timeout (milliseconds) (`timeout`) | Required; initialized | 30000 | Always | Integer 100–300000; separate from the file-transfer task timeout. |

### FTP and SFTP

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Host (`host`) | Required | Empty | FTP or SFTP | Target hostname or address, without scheme, path or username; up to 255 characters. |
| Port (`port`) | Required; initialized | 21 on first switch to FTP; 22 for SFTP | FTP or SFTP | Integer 1–65535; use the target listening port. |
| Username (`username`) | Required | Empty | FTP or SFTP | Up to 128 characters; use a dedicated restricted account. |
| Authentication | Initialized | Password (PASSWORD) | New SFTP resource | Choose Password or Private key. Existing resources manage stored credentials individually without this selector. |
| Password (`password`) | Required for FTP; retain a password or key for SFTP | Empty on creation; keep existing on edit | FTP; new SFTP password authentication; editing existing SFTP | Up to 4096 characters; saved values are hidden. |
| Private key file path (`privateKey`) | Required for key authentication; retain a key or password on edit | Empty on creation; keep existing on edit | New SFTP key authentication; editing existing SFTP | A local file readable by the executing Worker, not PEM contents or a remote path. The API node performing connection tests must also read the path. |
| Private key passphrase (`passphrase`) | Optional; requires a key path when supplied | Empty on creation; keep existing on edit | New SFTP key authentication; editing existing SFTP | Passphrase for an encrypted key, up to 4096 characters. |
| Host verification (`hostKeyVerification`) | Initialized | Managed by platform (MANAGED) | SFTP | May select Use a known_hosts file (KNOWN_HOSTS). There is no option to skip host verification. |
| Host identity (`hostKey`) | Confirmation required in MANAGED mode | Not confirmed | SFTP with MANAGED | Retrieval shows the key type and SHA256 fingerprint. Verify through a trusted source before confirming. The platform saves the host public key; this is not a private-key input. |
| known_hosts file path (`knownHosts`) | Required | Empty | SFTP with KNOWN_HOSTS | Up to 2048 characters. Both the executing Worker and API node running connection tests need a readable file. |

### S3-compatible object storage

| Field | Required | Initial form value | Shown when | Description |
| --- | --- | --- | --- | --- |
| Endpoint (optional) (`endpoint`) | Optional; set it for your compatible service as needed | Empty | S3_COMPATIBLE | Service endpoint, up to 2048 characters. Whether an explicit address is needed depends on the service. |
| Region (`region`) | Required | Empty | S3_COMPATIBLE | Up to 128 characters; match the service signing region. |
| Bucket (`bucket`) | Required | Empty | S3_COMPATIBLE | Up to 255 characters; use an authorized bucket. |
| Access Key (`accessKey`) | Required; existing value may be kept | Empty on creation; keep existing on edit | S3_COMPATIBLE | Up to 512 characters, hidden after saving. No direct Clear control is offered. |
| Secret Key (`secretKey`) | Required; existing value may be kept | Empty on creation; keep existing on edit | S3_COMPATIBLE | Up to 4096 characters, hidden after saving; enter the complete replacement value. |
| Session Token (optional) (`sessionToken`) | Optional; supply when temporary credentials require it | Empty on creation; keep existing on edit | S3_COMPATIBLE | Up to 65535 characters; use with the matching access credentials. Can be explicitly cleared. |
| Path-style access (`pathStyleAccess`) | Initialized | On | S3_COMPATIBLE | Choose path-style or virtual-host-style addressing to match the object service. |

### File resource actions

| Action | Roles | Available when | Result |
| --- | --- | --- | --- |
| Browse root directory | ADMIN | Writable editor and no unknown write outcome; remote root entered, SFTP host trust confirmed | Browse accessible directories and fill the root path. LOCAL starts from roots allowed on the API node. This is not the local disk of the computer running your browser; execution still uses the Worker’s filesystem and permissions. |
| Get and verify fingerprint | ADMIN | SFTP with MANAGED; valid host, port and connection timeout | Connect to retrieve the fingerprint without sending a password. Confirm trust to store it in the draft; save the resource to apply. |
| Verify fingerprint again | ADMIN | Host identity already confirmed and editor writable | Retrieve it again; changes show the previous fingerprint. Verify before trusting a new key. |
| Test connection | ADMIN | Resource saved and enabled | List the resource root using saved settings without transferring files. Paths and access conditions can differ between the test node and Worker. |

## Use and verify

Place orders.csv under the resource root, test listing/reading, then write to an independent target. Browser root allowlists do not sandbox all script filesystem access. Never use platform runtime/secrets as transfer sources.
