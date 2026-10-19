package com.ifielsc.ijob.plugin.filetransfer.archive;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.filetransfer.CompressType;
import com.ifielsc.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifielsc.ijob.plugin.filetransfer.TransferFile;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class TarGzArchiveHandler implements ArchiveHandler {
    @Override
    public CompressType type() {
        return CompressType.TAR_GZ;
    }

    @Override
    public List<TransferFile> compress(
            List<TransferFile> files, FileTransferConfig config, TaskContext context, Path tempDir)
            throws IOException {
        Path target =
                ArchiveUtils.safeArchivePath(
                        tempDir, context.resolve(config.getArchiveName()), ".tar.gz");
        try (OutputStream fos =
                        com.ifielsc.ijob.plugin.filetransfer.StagingBudget.newOutputStream(target);
                GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(fos);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
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
            try (InputStream fis = Files.newInputStream(file.getLocalFile().toPath());
                    GzipCompressorInputStream gzip = new GzipCompressorInputStream(fis);
                    TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
                extractTar(tar, tempDir, context, result, quota);
            }
        }
        return result;
    }

    private void extractTar(
            TarArchiveInputStream tar,
            Path tempDir,
            TaskContext context,
            List<TransferFile> result,
            ArchiveExtractionQuota quota)
            throws IOException {
        TarArchiveEntry entry;
        while ((entry = tar.getNextEntry()) != null) {
            context.checkStop();
            ArchiveExtractionQuota.Entry quotaEntry = quota.beginEntry(entry.getSize());
            if (entry.isDirectory()) {
                continue;
            }

            String entryName = ArchiveUtils.safeEntryName(tempDir, entry.getName());
            Path target = ArchiveUtils.safeResolve(tempDir, entryName);
            Files.createDirectories(target.getParent());
            try (OutputStream out =
                    com.ifielsc.ijob.plugin.filetransfer.StagingBudget.newOutputStream(target)) {
                ArchiveUtils.copy(tar, out, context, quota, quotaEntry);
            }
            result.add(ArchiveUtils.buildExtractFile(target, entryName));
        }
    }
}
