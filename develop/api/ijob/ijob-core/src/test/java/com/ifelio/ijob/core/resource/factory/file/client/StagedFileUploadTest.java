package com.ifelio.ijob.core.resource.factory.file.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class StagedFileUploadTest {
    @Test
    void publishesOnlyAfterCompleteUploadAndKeepsExistingUntilRename() throws Exception {
        var client = new MemoryClient();
        client.files.put("out/data.csv", "old");
        assertTrue(
                client.uploadPublished(
                        "out/data.csv",
                        input("new"),
                        3,
                        true,
                        () -> assertEquals("old", client.files.get("out/data.csv"))));
        assertEquals(Map.of("out/data.csv", "new"), client.files);
        assertTrue(client.uploadPath.startsWith("out/.ijob-transfer-"));
    }

    @Test
    void interruptedUploadAndCancellationKeepOldTarget() throws Exception {
        var client = new MemoryClient();
        client.files.put("out/data.csv", "old");
        assertThrows(
                IOException.class,
                () -> client.uploadPublished("out/data.csv", input("new"), 4, true, () -> {}));
        assertEquals(Map.of("out/data.csv", "old"), client.files);
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        client.uploadPublished(
                                "out/data.csv",
                                input("new"),
                                3,
                                true,
                                () -> {
                                    if (checks.incrementAndGet() == 2)
                                        throw new IllegalStateException("cancel");
                                }));
        assertEquals(Map.of("out/data.csv", "old"), client.files);
    }

    @Test
    void skipAndLateConflictNeverReplaceTarget() throws Exception {
        var client = new MemoryClient();
        client.files.put("out/data.csv", "old");
        assertFalse(client.uploadPublished("out/data.csv", input("new"), 3, false, () -> {}));
        assertNull(client.uploadPath);
        client.files.clear();
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        assertFalse(
                client.uploadPublished(
                        "out/data.csv",
                        input("new"),
                        3,
                        false,
                        () -> {
                            if (checks.incrementAndGet() == 2)
                                client.files.put("out/data.csv", "other");
                        }));
        assertEquals(Map.of("out/data.csv", "other"), client.files);
    }

    @Test
    void rejectedRenameDoesNotDeleteTargetAndCleanupDoesNotMaskFailure() {
        var client = new MemoryClient();
        client.files.put("out/data.csv", "old");
        client.renameFailure = true;
        client.cleanupFailure = true;
        var failure =
                assertThrows(
                        IOException.class,
                        () ->
                                client.uploadPublished(
                                        "out/data.csv", input("new"), 3, true, () -> {}));
        assertEquals("rename rejected", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("old", client.files.get("out/data.csv"));
    }

    private static InputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static class MemoryClient implements StagedFileClient {
        final Map<String, String> files = new HashMap<>();
        String uploadPath;
        boolean renameFailure, cleanupFailure;

        public List<FileInfo> list(String path) {
            return List.of();
        }

        public InputStream download(String path) {
            return input(files.get(path));
        }

        public void upload(String path, InputStream in) throws IOException {
            uploadPath = path;
            files.put(path, new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }

        public void mkdirs(String path) {}

        public boolean exists(String path) {
            return files.containsKey(path);
        }

        public void delete(String path) {
            throw new AssertionError("Generic recursive delete must not be used");
        }

        public void renameFile(String from, String to) throws IOException {
            if (renameFailure) throw new IOException("rename rejected");
            files.put(to, files.remove(from));
        }

        public void deleteTemporaryFile(String path) throws IOException {
            if (cleanupFailure) throw new IOException("cleanup failed");
            files.remove(path);
        }
    }
}
