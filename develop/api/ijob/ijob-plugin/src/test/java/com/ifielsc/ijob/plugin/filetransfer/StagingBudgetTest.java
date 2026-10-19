package com.ifielsc.ijob.plugin.filetransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

class StagingBudgetTest {
    @TempDir Path dir;

    @Test
    void allStageFilesShareOneBudgetFromFirstByteAndScopeIsCleared() throws Exception {
        try (var budget = StagingBudget.open(4)) {
            try (var first = StagingBudget.newOutputStream(dir.resolve("source"))) {
                first.write(new byte[3]);
            }
            try (var second = StagingBudget.newOutputStream(dir.resolve("expanded"))) {
                second.write(1);
                assertThrows(IOException.class, () -> second.write(2));
            }
            assertEquals(1, Files.size(dir.resolve("expanded")));
        }
        try (var budget = StagingBudget.open(4);
                var next = StagingBudget.newOutputStream(dir.resolve("next"))) {
            next.write(new byte[4]);
        }
    }

    @Test
    void seekableArchiveWritesCannotBypassBudget() throws Exception {
        try (var budget = StagingBudget.open(4);
                var channel = StagingBudget.newByteChannel(dir.resolve("archive"))) {
            assertEquals(4, channel.write(ByteBuffer.wrap(new byte[4])));
            channel.position(0);
            assertThrows(IOException.class, () -> channel.write(ByteBuffer.wrap(new byte[1])));
        }
    }
}
