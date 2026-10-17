package com.ifelio.ijob.plugin.filetransfer.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifelio.ijob.plugin.filetransfer.TransferFile;
import com.ifelio.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class ArchiveSafetyTest {
    @TempDir Path tempDir;

    @Test
    void zipTraversalEntryShouldBeRejectedWithoutOutsideFile() throws Exception {
        Path archive = tempDir.resolve("unsafe.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("../outside.txt"));
            zip.write("unsafe".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Path extractRoot = Files.createDirectory(tempDir.resolve("extract"));
        TransferFile file =
                TransferFile.builder()
                        .relativePath("unsafe.zip")
                        .localFile(archive.toFile())
                        .build();

        assertThrows(
                IOException.class,
                () ->
                        new ZipArchiveHandler()
                                .decompress(
                                        List.of(file),
                                        context(new ExecutionHandle(1L)),
                                        extractRoot));
        assertFalse(Files.exists(tempDir.resolve("outside.txt")));
    }

    @Test
    void resolvedArchiveNameTraversalShouldBeRejected() throws Exception {
        Path source = tempDir.resolve("data.txt");
        Files.writeString(source, "data", StandardCharsets.UTF_8);
        TransferFile file =
                TransferFile.builder().relativePath("data.txt").localFile(source.toFile()).build();
        FileTransferConfig config = FileTransferConfig.builder().archiveName("${archive!}").build();
        TaskContext context = context(new ExecutionHandle(2L));
        context.putVariable("archive", "../outside");

        assertThrows(
                IOException.class,
                () -> new ZipArchiveHandler().compress(List.of(file), config, context, tempDir));
        assertFalse(Files.exists(tempDir.getParent().resolve("outside.zip")));
    }

    @Test
    void archiveCopyShouldObserveStopBetweenChunks() {
        ExecutionHandle handle = new ExecutionHandle(3L);
        TaskContext context = context(handle);
        ByteArrayInputStream input =
                new ByteArrayInputStream("two-chunks".getBytes(StandardCharsets.UTF_8));
        OutputStream output =
                new OutputStream() {
                    @Override
                    public void write(int value) {}

                    @Override
                    public void write(byte[] bytes, int offset, int length) {
                        handle.requestStop();
                    }
                };

        assertThrows(StopException.class, () -> ArchiveUtils.copy(input, output, context));
    }

    @Test
    void gzipShouldPreserveNestedRelativePath() throws Exception {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "payload", StandardCharsets.UTF_8);
        TransferFile file =
                TransferFile.builder()
                        .relativePath("nested/source.txt")
                        .localFile(source.toFile())
                        .build();
        TaskContext context = context(new ExecutionHandle(4L));

        List<TransferFile> compressed =
                new GzipArchiveHandler()
                        .compress(
                                List.of(file),
                                FileTransferConfig.builder().build(),
                                context,
                                tempDir);
        List<TransferFile> extracted =
                new GzipArchiveHandler()
                        .decompress(compressed, context, tempDir.resolve("extract"));

        assertEquals("nested/source.txt.gz", compressed.getFirst().getRelativePath());
        assertEquals("nested/source.txt", extracted.getFirst().getRelativePath());
        assertEquals(
                "payload",
                Files.readString(
                        extracted.getFirst().getLocalFile().toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void allFormatsShouldEnforceSingleEntryLimit() throws Exception {
        List<ArchiveHandler> handlers =
                List.of(
                        new ZipArchiveHandler(),
                        new TarArchiveHandler(),
                        new TarGzArchiveHandler(),
                        new SevenZipArchiveHandler(),
                        new GzipArchiveHandler());
        for (ArchiveHandler handler : handlers) {
            Path work = Files.createDirectory(tempDir.resolve("limit-" + handler.type().name()));
            Path source = work.resolve("payload.bin");
            Files.write(source, new byte[2048]);
            TransferFile transferFile =
                    TransferFile.builder()
                            .relativePath("payload.bin")
                            .localFile(source.toFile())
                            .build();
            FileTransferConfig compressConfig =
                    FileTransferConfig.builder()
                            .compressType(handler.type())
                            .archiveName("bundle")
                            .build();
            TaskContext context = context(new ExecutionHandle(100L + handler.type().ordinal()));
            List<TransferFile> compressed =
                    handler.compress(List.of(transferFile), compressConfig, context, work);
            Path extract = Files.createDirectory(work.resolve("extract"));
            FileTransferConfig limits =
                    FileTransferConfig.builder()
                            .maxExtractedEntryBytes(1024)
                            .maxExtractedTotalBytes(4096)
                            .maxCompressionRatio(1000)
                            .build();

            assertThrows(
                    IOException.class,
                    () -> handler.decompress(compressed, limits, context, extract),
                    handler.type().name());
        }
    }

    @Test
    void sharedQuotaShouldEnforceCountTotalAndCompressionRatio() throws Exception {
        Path compressed = tempDir.resolve("compressed.bin");
        Files.write(compressed, new byte[10]);
        List<TransferFile> files =
                List.of(TransferFile.builder().localFile(compressed.toFile()).build());

        ArchiveExtractionQuota countQuota =
                ArchiveExtractionQuota.create(limits(1, 100, 100, 100), files);
        countQuota.beginEntry(0);
        assertThrows(IOException.class, () -> countQuota.beginEntry(0));

        ArchiveExtractionQuota totalQuota =
                ArchiveExtractionQuota.create(limits(10, 10, 12, 100), files);
        ArchiveExtractionQuota.Entry first = totalQuota.beginEntry(-1);
        ArchiveExtractionQuota.Entry second = totalQuota.beginEntry(-1);
        totalQuota.record(first, 8);
        assertThrows(IOException.class, () -> totalQuota.record(second, 5));

        ArchiveExtractionQuota ratioQuota =
                ArchiveExtractionQuota.create(limits(10, 100, 100, 2), files);
        ArchiveExtractionQuota.Entry ratioEntry = ratioQuota.beginEntry(-1);
        assertThrows(IOException.class, () -> ratioQuota.record(ratioEntry, 21));
    }

    @Test
    void windowsReservedDeviceNamesShouldBeRejectedOnEveryPlatform() {
        assertThrows(IOException.class, () -> ArchiveUtils.safeResolve(tempDir, "nested/CON.txt"));
        assertThrows(IOException.class, () -> ArchiveUtils.safeResolve(tempDir, "LPT1"));
    }

    private FileTransferConfig limits(int entries, long entryBytes, long totalBytes, int ratio) {
        return FileTransferConfig.builder()
                .maxArchiveEntries(entries)
                .maxExtractedEntryBytes(entryBytes)
                .maxExtractedTotalBytes(totalBytes)
                .maxCompressionRatio(ratio)
                .build();
    }

    private TaskContext context(ExecutionHandle handle) {
        return new TaskContext(
                handle.getInstanceId(),
                handle,
                null,
                new RecordingInstanceLogs(),
                new VariableResolver());
    }
}
