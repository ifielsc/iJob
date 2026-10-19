package com.ifielsc.ijob.plugin.filetransfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileTransferConfig {
    public static final int DEFAULT_MAX_ARCHIVE_ENTRIES = 10_000;
    public static final long DEFAULT_MAX_EXTRACTED_ENTRY_BYTES = 256L * 1024 * 1024;
    public static final long DEFAULT_MAX_EXTRACTED_TOTAL_BYTES = 1024L * 1024 * 1024;
    public static final int DEFAULT_MAX_COMPRESSION_RATIO = 100;

    @Builder.Default private TransferMode mode = TransferMode.TRANSFER;

    private Long sourceResourceId;

    private Long targetResourceId;

    private String sourcePath;

    private String targetPath;

    @Builder.Default private String filePattern = "*";

    @Builder.Default private boolean recursive = false;

    /** Decompression applied after download. */
    @Builder.Default private CompressType decompressType = CompressType.NONE;

    /** Compression applied before upload. */
    @Builder.Default private CompressType compressType = CompressType.NONE;

    @Builder.Default private RemotePublishMode remotePublishMode = RemotePublishMode.DIRECT;

    @Builder.Default private boolean overwrite = true;

    @Builder.Default private boolean deleteSource = false;

    /** Archive filename, for example {@code backup_${bizDate}.zip}. */
    private String archiveName;

    @Builder.Default private CompressionLevel compressionLevel = CompressionLevel.DEFAULT;

    @Builder.Default private int maxArchiveEntries = DEFAULT_MAX_ARCHIVE_ENTRIES;

    @Builder.Default private long maxExtractedEntryBytes = DEFAULT_MAX_EXTRACTED_ENTRY_BYTES;

    @Builder.Default private long maxExtractedTotalBytes = DEFAULT_MAX_EXTRACTED_TOTAL_BYTES;

    @Builder.Default private int maxCompressionRatio = DEFAULT_MAX_COMPRESSION_RATIO;

    @Builder.Default private int maxScanDepth = 32;
    @Builder.Default private int maxScannedEntries = 10_000;
    @Builder.Default private long maxSourceFileBytes = 256L * 1024 * 1024;
    @Builder.Default private long maxSourceTotalBytes = 1024L * 1024 * 1024;
    @Builder.Default private long maxStagingBytes = 2L * 1024 * 1024 * 1024;
}
