package com.ifielsc.ijob.plugin.filetransfer.archive;

import com.ifielsc.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifielsc.ijob.plugin.filetransfer.TransferFile;

import java.io.IOException;
import java.util.List;

final class ArchiveExtractionQuota {
    private final int maxEntries;
    private final long maxEntryBytes;
    private final long maxTotalBytes;
    private final int maxCompressionRatio;
    private final long compressedBytes;
    private int entries;
    private long totalBytes;

    private ArchiveExtractionQuota(FileTransferConfig config, long compressedBytes) {
        this.maxEntries = config.getMaxArchiveEntries();
        this.maxEntryBytes = config.getMaxExtractedEntryBytes();
        this.maxTotalBytes = config.getMaxExtractedTotalBytes();
        this.maxCompressionRatio = config.getMaxCompressionRatio();
        this.compressedBytes = Math.max(1, compressedBytes);
    }

    static ArchiveExtractionQuota create(FileTransferConfig config, List<TransferFile> files)
            throws IOException {
        if (config == null
                || config.getMaxArchiveEntries() < 1
                || config.getMaxExtractedEntryBytes() < 1
                || config.getMaxExtractedTotalBytes() < 1
                || config.getMaxCompressionRatio() < 1) {
            throw new IOException("Invalid archive extraction limits");
        }
        long compressedBytes = 0;
        for (TransferFile file : files) {
            if (file != null && file.getLocalFile() != null) {
                compressedBytes = saturatedAdd(compressedBytes, file.getLocalFile().length());
            }
        }
        return new ArchiveExtractionQuota(config, compressedBytes);
    }

    Entry beginEntry(long declaredSize) throws IOException {
        entries++;
        if (entries > maxEntries) {
            throw new IOException("Archive entry count exceeds limit");
        }
        if (declaredSize > maxEntryBytes) {
            throw new IOException("Archive entry size exceeds limit");
        }
        return new Entry();
    }

    void record(Entry entry, int bytes) throws IOException {
        entry.bytes = saturatedAdd(entry.bytes, bytes);
        totalBytes = saturatedAdd(totalBytes, bytes);
        if (entry.bytes > maxEntryBytes) {
            throw new IOException("Archive entry size exceeds limit");
        }
        if (totalBytes > maxTotalBytes) {
            throw new IOException("Archive total size exceeds limit");
        }
        long ratioLimit =
                compressedBytes > Long.MAX_VALUE / maxCompressionRatio
                        ? Long.MAX_VALUE
                        : compressedBytes * maxCompressionRatio;
        if (totalBytes > ratioLimit) {
            throw new IOException("Archive compression ratio exceeds limit");
        }
    }

    private static long saturatedAdd(long current, long increment) {
        return increment > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + increment;
    }

    static final class Entry {
        private long bytes;
    }
}
