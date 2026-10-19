// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.filetransfer.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.plugin.filetransfer.CompressType;
import com.ifielsc.ijob.plugin.filetransfer.CompressionLevel;
import com.ifielsc.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifielsc.ijob.plugin.filetransfer.TransferFile;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ArchiveRoundTripTest {
    @TempDir Path tempDir;

    @Test
    void zipShouldRoundTripNestedFile() throws Exception {
        roundTrip(new ZipArchiveHandler(), CompressType.ZIP);
    }

    @Test
    void tarShouldRoundTripNestedFile() throws Exception {
        roundTrip(new TarArchiveHandler(), CompressType.TAR);
    }

    @Test
    void tarGzipShouldRoundTripNestedFile() throws Exception {
        roundTrip(new TarGzArchiveHandler(), CompressType.TAR_GZ);
    }

    @Test
    void sevenZipShouldRoundTripNestedFile() throws Exception {
        roundTrip(new SevenZipArchiveHandler(), CompressType.SEVEN_ZIP);
    }

    private void roundTrip(ArchiveHandler handler, CompressType type) throws Exception {
        Path work = Files.createDirectory(tempDir.resolve(type.name()));
        Path source = work.resolve("source.txt");
        Files.writeString(source, "payload", StandardCharsets.UTF_8);
        TransferFile file =
                TransferFile.builder()
                        .relativePath("nested/source.txt")
                        .localFile(source.toFile())
                        .build();
        FileTransferConfig config =
                FileTransferConfig.builder()
                        .compressType(type)
                        .archiveName("bundle")
                        .compressionLevel(
                                type == CompressType.ZIP
                                        ? CompressionLevel.FAST
                                        : CompressionLevel.DEFAULT)
                        .build();
        TaskContext context = context(type.ordinal() + 100L);

        List<TransferFile> compressed = handler.compress(List.of(file), config, context, work);
        Path extract = Files.createDirectory(work.resolve("extract"));
        List<TransferFile> extracted = handler.decompress(compressed, context, extract);

        assertEquals(1, compressed.size());
        assertEquals(1, extracted.size());
        assertEquals("nested/source.txt", extracted.getFirst().getRelativePath());
        assertEquals(
                "payload",
                Files.readString(
                        extracted.getFirst().getLocalFile().toPath(), StandardCharsets.UTF_8));
    }

    private TaskContext context(long id) {
        return new TaskContext(
                id,
                new ExecutionHandle(id),
                null,
                new RecordingInstanceLogs(),
                new VariableResolver());
    }
}
