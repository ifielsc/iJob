# File transfer and archives

[简体中文](../../../zh-CN/user-guide/plugins/file-transfer.md) · [Manual home](../../README.md)

## Entry and prerequisites

Tasks → New/Edit → plugin `FILE_TRANSFER`. Prepare the resources, permissions and execution-node dependencies listed below. See [Tasks](../tasks.md) for shared timeout/retry/publication behavior.

## Fields and behavior

| Page field/action | Purpose and rules |
| --- | --- |
| Transfer mode / Source file resource / Target file resource | Choose Transfer or Read only and the applicable source/target file resources; default TRANSFER. Compression and decompression have separate settings. |
| Source directory / Target directory / File pattern / Recursion | Paths under resource roots, pattern and recursion; defaults * and nonrecursive. Start with one CSV. |
| Overwrite / Delete source after success / Remote publication | Defaults overwrite=true, deleteSource=false and DIRECT publication. Use overwrite=false and preserve source for the first exercise. |
| Decompression / Compression / Archive name / Compression level | Select decompression/compression format, archive name and level; defaults NONE/NONE/DEFAULT. |
| Archive entry / Expanded entry / Total expansion / Compression-ratio limits | Defaults 10000 / 256 MiB / 1 GiB / 100; bound archive expansion. |
| Scan depth / Entry / Source file / Total source / Staging limits | Defaults 32 / 10000 / 256 MiB / 1 GiB / 2 GiB; account for Worker staging disk. |

## Minimal example and verification

Prepare the dedicated resources above, fill the form, save, then trigger manually and verify the instance, outputs and target system as described below. Saving alone does not execute.

| Setting | Value to enter |
| --- | --- |
| Transfer mode | Transfer (TRANSFER) |
| Source file resource | Select the dedicated source resource |
| Target file resource | Select the dedicated target resource |
| Source directory | Leave empty to use the resource root |
| Target directory | Leave empty to use the resource root |
| File pattern | `*.csv` |
| Overwrite | Off |
| Delete source after success | Off |

Add source/target resources, compare target names, sizes and SHA-256, and confirm sources remain. Test FTP/SFTP/S3 connectivity/permissions first; remote publication/rename capabilities differ. Inspect existing targets before retrying a failure; logs alone do not prove no files were copied.

Replace example resources, paths and object names with values from your environment. See the [case index](../../examples.md) for actual external-protocol verification status.

## Real page examples

Source/target resources and CSV matching; write options continue below.

![Source/target resources and CSV matching; write options continue below.](../../images/manual/transfer-editor.png)

## Technical key reference (optional)

These stored keys help developers and troubleshooting; they are not a task JSON blob to paste into the UI. Automatically validated metadata is not a manual form input.

| Page field/group | Stored keys |
| --- | --- |
| Transfer mode / Source file resource / Target file resource | `mode / sourceResourceId / targetResourceId` |
| Source directory / Target directory / File pattern / Recursion | `sourcePath / targetPath / filePattern / recursive` |
| Overwrite / Delete source after success / Remote publication | `overwrite / deleteSource / remotePublishMode` |
| Decompression / Compression / Archive name / Compression level | `decompressType / compressType / archiveName / compressionLevel` |
| Archive entry / Expanded entry / Total expansion / Compression-ratio limits | `maxArchiveEntries / maxExtractedEntryBytes / maxExtractedTotalBytes / maxCompressionRatio` |
| Scan depth / Entry / Source file / Total source / Staging limits | `maxScanDepth / maxScannedEntries / maxSourceFileBytes / maxSourceTotalBytes / maxStagingBytes` |
