package com.ifielsc.ijob.plugin.filetransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.io.IOException;

class TransferQuotaTest {
    private FileTransferConfig config() {
        FileTransferConfig config = new FileTransferConfig();
        config.setMaxScannedEntries(2);
        config.setMaxScanDepth(2);
        config.setMaxSourceFileBytes(4);
        config.setMaxSourceTotalBytes(6);
        return config;
    }

    @Test
    void exactByteLimitSucceedsAndNextByteFailsBeforeWrite() throws Exception {
        TransferQuota quota = new TransferQuota(config());
        assertEquals(4, quota.bytes(0, 4));
        assertThrows(IOException.class, () -> quota.bytes(4, 1));
        assertEquals(2, quota.bytes(0, 2));
        assertThrows(IOException.class, () -> quota.bytes(2, 1));
    }

    @Test
    void allEntriesAndDepthAreBoundedEvenIfNoFilesMatch() throws Exception {
        TransferQuota quota = new TransferQuota(config());
        quota.entry();
        quota.entry();
        assertEquals(0, quota.remainingEntries());
        assertThrows(IOException.class, quota::entry);
        quota.depth(2);
        assertThrows(IOException.class, () -> quota.depth(3));
    }

    @Test
    void invalidAndOverflowingBudgetsAreRejected() {
        FileTransferConfig config = config();
        config.setMaxSourceTotalBytes(Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> new TransferQuota(config));
        config.setMaxSourceTotalBytes(6);
        config.setMaxScanDepth(100000);
        assertThrows(IllegalArgumentException.class, () -> new TransferQuota(config));
    }
}
