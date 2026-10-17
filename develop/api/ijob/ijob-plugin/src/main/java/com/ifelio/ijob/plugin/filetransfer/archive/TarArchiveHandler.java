package com.ifelio.ijob.plugin.filetransfer.archive;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.plugin.filetransfer.CompressType;
import com.ifelio.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifelio.ijob.plugin.filetransfer.TransferFile;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class TarArchiveHandler implements ArchiveHandler {
    @Override
    public CompressType type() {
        return CompressType.TAR;
    }

    @Override
    public List<TransferFile> compress(
            List<TransferFile> files, FileTransferConfig config, TaskContext context, Path tempDir)
            throws IOException {
        Path target =
                ArchiveUtils.safeArchivePath(
                        tempDir, context.resolve(config.getArchiveName()), ".tar");
        try (TarArchiveOutputStream tar =
                new TarArchiveOutputStream(
                        com.ifelio.ijob.plugin.filetransfer.StagingBudget.newOutputStream(
                                target))) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (TransferFile file : files) {
                context.checkStop();
                String entryName = ArchiveUtils.safeEntryName(tempDir, file.getRelativePath());
                TarArchiveEntry entry = new TarArchiveEntry(file.getLocalFile(), entryName);
                tar.putArchiveEntry(entry);
                try (InputStream in = Files.newInputStream(file.getLocalFile().toPath())) {
                    ArchiveUtils.copy(in, tar, context);
                }
                tar.closeArchiveEntry();
            }
        }
        return List.of(ArchiveUtils.buildArchiveFile(target));
    }

    @Override
    public List<TransferFile> decompress(
            List<TransferFile> files, FileTransferConfig config, TaskContext context, Path tempDir)
            throws IOException {
        List<TransferFile> result = new ArrayList<>();
        ArchiveExtractionQuota quota = ArchiveExtractionQuota.create(config, files);
        for (TransferFile file : files) {
            try (TarArchiveInputStream tar =
                    new TarArchiveInputStream(Files.newInputStream(file.getLocalFile().toPath()))) {
                TarArchiveEntry entry;
                while ((entry = tar.getNextEntry()) != null) {
                    context.checkStop();
                    ArchiveExtractionQuota.Entry quotaEntry = quota.beginEntry(entry.getSize());
                    if (entry.isDirectory()) continue;

                    String entryName = ArchiveUtils.safeEntryName(tempDir, entry.getName());
                    Path target = ArchiveUtils.safeResolve(tempDir, entryName);
                    Files.createDirectories(target.getParent());
                    try (OutputStream out =
                            com.ifelio.ijob.plugin.filetransfer.StagingBudget.newOutputStream(
                                    target)) {
                        ArchiveUtils.copy(tar, out, context, quota, quotaEntry);
                    }
                    result.add(ArchiveUtils.buildExtractFile(target, entryName));
                }
            }
        }
        return result;
    }
}
