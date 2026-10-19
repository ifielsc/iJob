// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.filetransfer;

import java.io.IOException;

/** Per-transfer raw-source budget; metadata is never trusted for byte accounting. */
final class TransferQuota {
    private final FileTransferConfig config;
    private int entries;
    private long bytes;

    TransferQuota(FileTransferConfig config) {
        validate(config);
        this.config = config;
    }

    static void validate(FileTransferConfig config) {
        if (config.getMaxScanDepth() < 0
                || config.getMaxScanDepth() > 64
                || config.getMaxScannedEntries() < 1
                || config.getMaxScannedEntries() > 100_000
                || config.getMaxSourceFileBytes() < 1
                || config.getMaxSourceFileBytes() > 1024L * 1024 * 1024
                || config.getMaxSourceTotalBytes() < config.getMaxSourceFileBytes()
                || config.getMaxSourceTotalBytes() > 10L * 1024 * 1024 * 1024) {
            throw new IllegalArgumentException("File transfer source limits are invalid");
        }
        if (config.getMaxStagingBytes() < 1
                || config.getMaxStagingBytes() > 20L * 1024 * 1024 * 1024) {
            throw new IllegalArgumentException("File transfer staging limit is invalid");
        }
    }

    void depth(int depth) throws IOException {
        if (depth > config.getMaxScanDepth()) throw exceeded();
    }

    void entry() throws IOException {
        if (++entries > config.getMaxScannedEntries()) throw exceeded();
    }

    int remainingEntries() {
        return config.getMaxScannedEntries() - entries;
    }

    long bytes(long fileBytes, int count) throws IOException {
        if (count < 0
                || count > config.getMaxSourceFileBytes() - fileBytes
                || count > config.getMaxSourceTotalBytes() - bytes) throw exceeded();
        bytes += count;
        return fileBytes + count;
    }

    private IOException exceeded() {
        return new IOException("File transfer source quota exceeded");
    }
}
