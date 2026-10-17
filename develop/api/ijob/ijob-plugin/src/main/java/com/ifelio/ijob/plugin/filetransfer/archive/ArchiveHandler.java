package com.ifelio.ijob.plugin.filetransfer.archive;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.plugin.filetransfer.CompressType;
import com.ifelio.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifelio.ijob.plugin.filetransfer.TransferFile;

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
