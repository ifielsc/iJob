package com.ifielsc.ijob.plugin.filetransfer.archive;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.filetransfer.CompressType;
import com.ifielsc.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifielsc.ijob.plugin.filetransfer.TransferFile;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Component
public class GzipArchiveHandler implements ArchiveHandler {
    @Override
    public CompressType type() {
        return CompressType.GZIP;
    }

    @Override
    public List<TransferFile> compress(
            List<TransferFile> files, FileTransferConfig config, TaskContext context, Path tempDir)
            throws IOException {
        List<TransferFile> result = new ArrayList<>();
        for (TransferFile file : files) {
            context.checkStop();
            String relativePath =
                    ArchiveUtils.safeEntryName(tempDir, file.getRelativePath()) + ".gz";
            Path target = ArchiveUtils.safeResolve(tempDir, relativePath);
            Files.createDirectories(target.getParent());
            try (GZIPOutputStream out =
                            new GZIPOutputStream(
                                    com.ifielsc.ijob.plugin.filetransfer.StagingBudget
                                            .newOutputStream(target));
                    InputStream in = Files.newInputStream(file.getLocalFile().toPath())) {
                ArchiveUtils.copy(in, out, context);
            }
            result.add(ArchiveUtils.buildExtractFile(target, relativePath));
        }
        return result;
    }

    @Override
    public List<TransferFile> decompress(
            List<TransferFile> files, FileTransferConfig config, TaskContext context, Path tempDir)
            throws IOException {
        List<TransferFile> result = new ArrayList<>();
        ArchiveExtractionQuota quota = ArchiveExtractionQuota.create(config, files);
        for (TransferFile file : files) {
            context.checkStop();
            ArchiveExtractionQuota.Entry quotaEntry = quota.beginEntry(-1);
            String relativePath = file.getRelativePath();
            String name =
                    relativePath.toLowerCase().endsWith(".gz")
                            ? relativePath.substring(0, relativePath.length() - 3)
                            : relativePath + ".out";
            name = ArchiveUtils.safeEntryName(tempDir, name);
            Path target = ArchiveUtils.safeResolve(tempDir, name);
            Files.createDirectories(target.getParent());
            try (GZIPInputStream in =
                            new GZIPInputStream(
                                    Files.newInputStream(file.getLocalFile().toPath()));
                    OutputStream out =
                            com.ifielsc.ijob.plugin.filetransfer.StagingBudget.newOutputStream(
                                    target)) {
                ArchiveUtils.copy(in, out, context, quota, quotaEntry);
            }
            result.add(ArchiveUtils.buildExtractFile(target, name));
        }
        return result;
    }
}
