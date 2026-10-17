package com.ifelio.ijob.plugin.filetransfer.archive;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.plugin.filetransfer.CompressType;
import com.ifelio.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifelio.ijob.plugin.filetransfer.TransferFile;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class ZipArchiveHandler implements ArchiveHandler {
    @Override
    public CompressType type() {
        return CompressType.ZIP;
    }

    @Override
    public List<TransferFile> compress(
            List<TransferFile> files, FileTransferConfig config, TaskContext context, Path tempDir)
            throws IOException {
        Path target =
                ArchiveUtils.safeArchivePath(
                        tempDir, context.resolve(config.getArchiveName()), ".zip");
        try (ZipOutputStream zos =
                new ZipOutputStream(
                        com.ifelio.ijob.plugin.filetransfer.StagingBudget.newOutputStream(
                                target))) {
            zos.setLevel(config.getCompressionLevel().getLevel());
            for (TransferFile file : files) {
                context.checkStop();
                String entryName = ArchiveUtils.safeEntryName(tempDir, file.getRelativePath());
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                try (InputStream in = Files.newInputStream(file.getLocalFile().toPath())) {
                    ArchiveUtils.copy(in, zos, context);
                }
                zos.closeEntry();
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
            try (ZipInputStream zis =
                    new ZipInputStream(Files.newInputStream(file.getLocalFile().toPath()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    context.checkStop();
                    ArchiveExtractionQuota.Entry quotaEntry = quota.beginEntry(entry.getSize());
                    if (entry.isDirectory()) continue;

                    String entryName = ArchiveUtils.safeEntryName(tempDir, entry.getName());
                    Path target = ArchiveUtils.safeResolve(tempDir, entryName);
                    Files.createDirectories(target.getParent());
                    try (OutputStream out =
                            com.ifelio.ijob.plugin.filetransfer.StagingBudget.newOutputStream(
                                    target)) {
                        ArchiveUtils.copy(zis, out, context, quota, quotaEntry);
                    }
                    result.add(ArchiveUtils.buildExtractFile(target, entryName));
                }
            }
        }
        return result;
    }
}
