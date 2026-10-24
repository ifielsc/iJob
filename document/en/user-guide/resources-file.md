# LOCAL, FTP, SFTP and S3 file resources

[简体中文](../../zh-CN/user-guide/resources-file.md) · [Manual home](../README.md)

## Entry and workflow

Resources → File resources. Actions depend on account permissions; identify the object and environment first.

## Configuration and actions

| Field/action | Purpose and rules |
| --- | --- |
| `protocol / basePath` | LOCAL roots belong to the executor; remote paths/object prefixes belong to the resource, not the browser. |
| `host / port / username / password / timeout` | FTP/SFTP connection identity/timeouts; authorize only the test directory. |
| `privateKey / passphrase / hostKey / knownHosts` | SFTP key/passphrase and host trust follow the SSH principles. |
| `endpoint / region / bucket / accessKey / secretKey / sessionToken / pathStyleAccess` | S3 endpoint, region, bucket, credentials and addressing style must match the service. |

## Use and verify

Place orders.csv under the resource root, test listing/reading, then write to an independent target. Browser root allowlists do not sandbox all script filesystem access. Never use platform runtime/secrets as transfer sources.
