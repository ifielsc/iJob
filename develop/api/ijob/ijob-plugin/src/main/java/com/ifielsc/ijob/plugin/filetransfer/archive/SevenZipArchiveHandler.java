package com.ifielsc.ijob.plugin.filetransfer.archive;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.filetransfer.CompressType;
import com.ifielsc.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifielsc.ijob.plugin.filetransfer.TransferFile;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class SevenZipArchiveHandler implements ArchiveHandler {
    private static final int BUFFER_SIZE = 8192;

    @Override
    public CompressType type() {
        return CompressType.SEVEN_ZIP;
    }

    @Override
    public List<TransferFile> compress(
            List<TransferFile> files, FileTransferConfig config, TaskContext context, Path tempDir)
            throws IOException {
        Path target =
                ArchiveUtils.safeArchivePath(
                        tempDir, context.resolve(config.getArchiveName()), ".7z");
        try (SevenZOutputFile out =
                new SevenZOutputFile(
                        com.ifielsc.ijob.plugin.filetransfer.StagingBudget.newByteChannel(
                                target))) {
            for (TransferFile file : files) {
                context.checkStop();
                String entryName = ArchiveUtils.safeEntryName(tempDir, file.getRelativePath());
                SevenZArchiveEntry entry = out.createArchiveEntry(file.getLocalFile(), entryName);
                out.putArchiveEntry(entry);
                try (InputStream in = Files.newInputStream(file.getLocalFile().toPath())) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        context.checkExecution();
                        out.write(buffer, 0, len);
                    }
                }
                out.closeArchiveEntry();
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
            try (SevenZFile seven = SevenZFile.builder().setFile(file.getLocalFile()).get()) {
                SevenZArchiveEntry entry;
                while ((entry = seven.getNextEntry()) != null) {
                    context.checkStop();
                    ArchiveExtractionQuota.Entry quotaEntry = quota.beginEntry(entry.getSize());
                    if (entry.isDirectory()) {
                        continue;
                    }

                    String entryName = ArchiveUtils.safeEntryName(tempDir, entry.getName());
                    Path target = ArchiveUtils.safeResolve(tempDir, entryName);
                    Files.createDirectories(target.getParent());
                    try (OutputStream out =
                            com.ifielsc.ijob.plugin.filetransfer.StagingBudget.newOutputStream(
                                    target)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int len;
                        while ((len = seven.read(buffer)) != -1) {
                            context.checkExecution();
                            quota.record(quotaEntry, len);
                            out.write(buffer, 0, len);
                        }
                    }
                    result.add(ArchiveUtils.buildExtractFile(target, entryName));
                }
            }
        }
        return result;
    }
}
