# 文件传输与归档

[English](../../../en/user-guide/plugins/file-transfer.md) · [手册首页](../../README.md)

## 入口与前提

任务 → 新增/编辑 → 插件 `FILE_TRANSFER`。先准备字段表要求的资源、权限及执行节点依赖。任务公共超时、重试和发布规则见[任务](../tasks.md)。

## 字段与作用

| 字段/动作 | 用途与填写规则 |
| --- | --- |
| `mode / sourceResourceId / targetResourceId` | 选择传输/页面支持的归档模式和源目标文件资源；默认 TRANSFER。 |
| `sourcePath / targetPath / filePattern / recursive` | 使用资源根下的路径、匹配模式，默认 *、不递归；先用一个 CSV 验证。 |
| `overwrite / deleteSource / remotePublishMode` | 默认覆盖 true、删除源 false、DIRECT 发布。初次演练改 overwrite=false 并保留源。 |
| `decompressType / compressType / archiveName / compressionLevel` | 按实际格式选择解压/压缩、归档文件名和级别，默认 NONE/NONE/DEFAULT。 |
| `maxArchiveEntries / maxExtractedEntryBytes / maxExtractedTotalBytes / maxCompressionRatio` | 默认 10000 / 256 MiB / 1 GiB / 100；保护解压规模。 |
| `maxScanDepth / maxScannedEntries / maxSourceFileBytes / maxSourceTotalBytes / maxStagingBytes` | 默认 32 / 10000 / 256 MiB / 1 GiB / 2 GiB；考虑 Worker 临时磁盘。 |

## 最小样例与核对

```json
{"sourcePath":".","targetPath":".","filePattern":"*.csv","overwrite":false,"deleteSource":false}
```

补源目标资源后，核验目标文件名、字节数与 SHA-256，并确认源文件仍在。FTP/SFTP/S3 需先测资源连通和权限；远端发布/重命名能力因协议而异。失败重试前检查已存在目标，不能仅看任务日志推断没有复制任何文件。

样例代码是配置片段，资源 ID/路径需替换；实际外部协议验收状态见[案例索引](../../examples.md)。

## 真实页面示例

源目标资源及 CSV 匹配；写入选项在下方。

![源目标资源及 CSV 匹配；写入选项在下方。](../../images/manual/transfer-editor.png)
