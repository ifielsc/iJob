package com.ifielsc.ijob.plugin.filetransfer;

public record FileTransferResult(
        int sourceFileCount,
        int outputFileCount,
        int uploadedFileCount,
        int skippedFileCount,
        int deletedSourceFileCount) {}
