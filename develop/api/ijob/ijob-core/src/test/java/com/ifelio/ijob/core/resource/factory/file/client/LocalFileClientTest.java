package com.ifelio.ijob.core.resource.factory.file.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.core.resource.factory.file.FileResourceConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class LocalFileClientTest {
    @TempDir Path root;

    @Test
    void shouldUploadListDownloadAndDeleteWithinRoot() throws Exception {
        LocalFileClient client = client();
        byte[] content = "accepted".getBytes(StandardCharsets.UTF_8);

        client.upload("/nested/data.txt", new ByteArrayInputStream(content));

        assertTrue(client.exists("nested/data.txt"));
        assertEquals("nested", client.list("/").getFirst().getName());
        assertEquals("nested", client.list("").getFirst().getName());
        assertEquals("data.txt", client.list("nested").getFirst().getName());
        try (InputStream input = client.download("nested/data.txt")) {
            assertArrayEquals(content, input.readAllBytes());
        }
        client.delete("nested");
        assertFalse(Files.exists(root.resolve("nested")));
    }

    @Test
    void failedUploadShouldKeepExistingTargetAndDeletePartFile() throws Exception {
        LocalFileClient client = client();
        Path target = root.resolve("data.txt");
        Files.writeString(target, "original", StandardCharsets.UTF_8);
        InputStream failure =
                new InputStream() {
                    private boolean first = true;

                    @Override
                    public int read(byte[] bytes, int offset, int length) throws IOException {
                        if (first) {
                            first = false;
                            bytes[offset] = 'x';
                            return 1;
                        }
                        throw new IOException("controlled failure");
                    }

                    @Override
                    public int read() throws IOException {
                        throw new IOException("not used");
                    }
                };

        assertThrows(IOException.class, () -> client.upload("data.txt", failure));
        assertEquals("original", Files.readString(target, StandardCharsets.UTF_8));
        try (var files = Files.list(root)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    void shouldRejectTraversalAbsoluteDriveAndMissingRoot() {
        LocalFileClient client = client();

        assertThrows(SecurityException.class, () -> client.exists("../outside"));
        assertThrows(SecurityException.class, () -> client.exists("C:/outside"));

        FileResourceConfig missing = new FileResourceConfig();
        missing.setBasePath(root.resolve("missing").toString());
        assertThrows(IllegalArgumentException.class, () -> new LocalFileClient(missing));
    }

    private LocalFileClient client() {
        FileResourceConfig config = new FileResourceConfig();
        config.setBasePath(root.toString());
        return new LocalFileClient(config);
    }

    @Test
    void shouldRejectDirectoryLinksAcrossOperations() throws Exception {
        Path base = Files.createDirectory(root.resolve("base"));
        Path outside = Files.createDirectory(root.resolve("outside"));
        Files.writeString(outside.resolve("sentinel"), "unchanged");
        Path link = base.resolve("link");
        if (System.getProperty("os.name").startsWith("Windows")) {
            Process process =
                    new ProcessBuilder(
                                    "powershell",
                                    "-NoProfile",
                                    "-Command",
                                    "New-Item -ItemType Junction -Path '"
                                            + link.toString().replace("'", "''")
                                            + "' -Target '"
                                            + outside.toString().replace("'", "''")
                                            + "' | Out-Null")
                            .redirectErrorStream(true)
                            .start();
            assertEquals(0, process.waitFor());
        } else {
            Files.createSymbolicLink(link, outside);
        }
        try {
            FileResourceConfig config = new FileResourceConfig();
            config.setBasePath(base.toString());
            LocalFileClient client = new LocalFileClient(config);
            assertThrows(SecurityException.class, () -> client.list("link"));
            assertThrows(SecurityException.class, () -> client.download("link/sentinel"));
            assertThrows(
                    SecurityException.class,
                    () -> client.upload("link/new", new ByteArrayInputStream(new byte[0])));
            assertThrows(SecurityException.class, () -> client.mkdirs("link/new"));
            assertThrows(SecurityException.class, () -> client.exists("link/sentinel"));
            assertThrows(SecurityException.class, () -> client.delete("link"));
            assertEquals("unchanged", Files.readString(outside.resolve("sentinel")));
        } finally {
            Files.delete(link);
        }
    }
}
