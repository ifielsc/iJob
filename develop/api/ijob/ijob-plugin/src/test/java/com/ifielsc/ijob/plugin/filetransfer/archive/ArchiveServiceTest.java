// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.filetransfer.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.filetransfer.CompressType;
import com.ifielsc.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifielsc.ijob.plugin.filetransfer.TransferFile;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;

class ArchiveServiceTest {
    @Test
    void shouldExposeOnlyHandlerListConstructor() {
        Constructor<?>[] constructors = ArchiveService.class.getDeclaredConstructors();

        assertEquals(1, constructors.length);
        assertArrayEquals(new Class<?>[] {List.class}, constructors[0].getParameterTypes());
    }

    @Test
    void shouldRejectDuplicateHandlerTypes() {
        RecordingArchiveHandler first = new RecordingArchiveHandler(CompressType.ZIP);
        RecordingArchiveHandler duplicate = new RecordingArchiveHandler(CompressType.ZIP);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> new ArchiveService(List.of(first, duplicate)));

        assertEquals("Duplicate archive handler type: ZIP", exception.getMessage());
    }

    @Test
    void shouldRouteCompressionByCompressType() throws IOException {
        RecordingArchiveHandler handler = new RecordingArchiveHandler(CompressType.ZIP);
        ArchiveService service = new ArchiveService(List.of(handler));
        FileTransferConfig config =
                FileTransferConfig.builder().compressType(CompressType.ZIP).build();

        List<TransferFile> result =
                service.compress(List.of(new TransferFile()), config, null, Path.of("."));

        assertSame(handler.result, result);
        assertEquals(1, handler.compressCalls);
        assertEquals(0, handler.decompressCalls);
    }

    @Test
    void shouldRouteDecompressionByDecompressType() throws IOException {
        RecordingArchiveHandler handler = new RecordingArchiveHandler(CompressType.GZIP);
        ArchiveService service = new ArchiveService(List.of(handler));
        FileTransferConfig config =
                FileTransferConfig.builder()
                        .compressType(CompressType.NONE)
                        .decompressType(CompressType.GZIP)
                        .build();

        List<TransferFile> result =
                service.decompress(
                        List.of(TransferFile.builder().relativePath("data.gz").build()),
                        config,
                        null,
                        Path.of("."));

        assertEquals(handler.result, result);
        assertEquals(0, handler.compressCalls);
        assertEquals(1, handler.decompressCalls);
    }

    @Test
    void shouldPassThroughWhenArchiveOperationIsDisabled() throws IOException {
        ArchiveService service = new ArchiveService(List.of());
        FileTransferConfig config = FileTransferConfig.builder().build();
        List<TransferFile> files = List.of(new TransferFile());

        assertSame(files, service.compress(files, config, null, Path.of(".")));
        assertSame(files, service.decompress(files, config, null, Path.of(".")));
    }

    @Test
    void decompressionShouldExpandOnlyMatchingArchivesAndKeepPlainFiles() throws IOException {
        RecordingArchiveHandler handler = new RecordingArchiveHandler(CompressType.ZIP);
        ArchiveService service = new ArchiveService(List.of(handler));
        FileTransferConfig config =
                FileTransferConfig.builder().decompressType(CompressType.ZIP).build();
        TransferFile plain = TransferFile.builder().relativePath("plain.csv").build();
        TransferFile archive = TransferFile.builder().relativePath("bundle.ZIP").build();

        List<TransferFile> result =
                service.decompress(List.of(plain, archive), config, null, Path.of("."));

        assertEquals(List.of(plain, handler.result.getFirst()), result);
        assertEquals(List.of(archive), handler.decompressedFiles);
    }

    @Test
    void shouldNotCreateArchiveForEmptyInput() throws IOException {
        RecordingArchiveHandler handler = new RecordingArchiveHandler(CompressType.ZIP);
        ArchiveService service = new ArchiveService(List.of(handler));
        FileTransferConfig config =
                FileTransferConfig.builder()
                        .compressType(CompressType.ZIP)
                        .decompressType(CompressType.ZIP)
                        .build();
        List<TransferFile> files = List.of();

        assertSame(files, service.compress(files, config, null, Path.of(".")));
        assertSame(files, service.decompress(files, config, null, Path.of(".")));
        assertEquals(0, handler.compressCalls);
        assertEquals(0, handler.decompressCalls);
    }

    private static class RecordingArchiveHandler implements ArchiveHandler {
        private final CompressType type;
        private final List<TransferFile> result = List.of(new TransferFile());
        private int compressCalls;
        private int decompressCalls;
        private List<TransferFile> decompressedFiles;

        RecordingArchiveHandler(CompressType type) {
            this.type = type;
        }

        @Override
        public CompressType type() {
            return type;
        }

        @Override
        public List<TransferFile> compress(
                List<TransferFile> files,
                FileTransferConfig config,
                TaskContext context,
                Path tempDir) {
            compressCalls++;
            return result;
        }

        @Override
        public List<TransferFile> decompress(
                List<TransferFile> files,
                FileTransferConfig config,
                TaskContext context,
                Path tempDir) {
            decompressCalls++;
            decompressedFiles = files;
            return result;
        }
    }
}
