// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.filetransfer.archive;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.filetransfer.CompressType;
import com.ifielsc.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifielsc.ijob.plugin.filetransfer.TransferFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ArchiveHandler {
    CompressType type();

    List<TransferFile> compress(
            List<TransferFile> files, FileTransferConfig config, TaskContext context, Path tempDir)
            throws IOException;

    default List<TransferFile> decompress(
            List<TransferFile> files, TaskContext context, Path tempDir) throws IOException {
        return decompress(files, FileTransferConfig.builder().build(), context, tempDir);
    }

    List<TransferFile> decompress(
            List<TransferFile> files, FileTransferConfig config, TaskContext context, Path tempDir)
            throws IOException;
}
