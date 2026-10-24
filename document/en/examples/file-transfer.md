# Local and remote file transfer

[简体中文](../../zh-CN/examples/file-transfer.md) · [Manual home](../README.md)

**Verification: the baseline path was exercised in isolation; extensions do not inherit that result.**

Fields: [File transfer and archives](../user-guide/plugins/file-transfer.md).

## Prepare, configure and run

Prepare two independent LOCAL file resources and orders.csv under the source root:

```csv
id,amount
1,100
2,200
3,300
```

Choose source/target resources, sourcePath=`.`, targetPath=`.`, filePattern=`*.csv`, recursive=false, overwrite=false and deleteSource=false. After running, compare target filename, bytes and SHA-256 and confirm the source remains.

A repeat may fail because the target exists: this tests overwrite policy. Use fresh targets for overwrite/archive variants. SFTP/S3 need separate services, identities, buckets/directories and write verification; LOCAL success does not establish those protocols.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.

## Real page examples

Exercised LOCAL transfer configuration, not proof of SFTP/S3 acceptance.

![Exercised LOCAL transfer configuration, not proof of SFTP/S3 acceptance.](../images/manual/transfer-editor.png)
