package com.ifelio.ijob.core.resource.factory.file.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONUtil;

import com.ifelio.ijob.core.resource.factory.file.FileResourceConfig;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

class RemotePublicationWireTest {
    @ParameterizedTest
    @ValueSource(strings = {"FTP", "SFTP"})
    void completeUploadPublishesAndRejectedRenamePreservesOldTarget(String protocol)
            throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null
                && !Files.exists(root.resolve("tools/ci/fixtures/remote-publication-fixture.py")))
            root = root.getParent();
        if (root == null) throw new IllegalStateException("Fixture missing");
        var process =
                new ProcessBuilder(
                                System.getProperty("fixture.python", "python"),
                                root.resolve("tools/ci/fixtures/remote-publication-fixture.py")
                                        .toString(),
                                protocol)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
        try (var input = new BufferedReader(new InputStreamReader(process.getInputStream()));
                var output = new java.io.PrintWriter(process.getOutputStream(), true)) {
            var fixture = JSONUtil.parseObj(input.readLine());
            var config = new FileResourceConfig();
            config.setHost("127.0.0.1");
            config.setPort(fixture.getInt("port"));
            config.setUsername("fixture");
            config.setPassword("fixture-password");
            config.setTimeout(3000);
            config.setBasePath("/out");
            config.setHostKeyVerification("MANAGED");
            config.setHostKey(fixture.getStr("key"));
            try (StagedFileClient client =
                    protocol.equals("FTP")
                            ? new FtpFileClient(config)
                            : new SftpFileClient(config)) {
                assertFalse(client.uploadPublished("data.csv", bytes("new"), 3, false, () -> {}));
                assertTrue(
                        client.uploadPublished(
                                "data.csv",
                                bytes("new"),
                                3,
                                true,
                                () -> {
                                    try {
                                        assertTrue(client.exists("data.csv"));
                                    } catch (IOException e) {
                                        throw new AssertionError(e);
                                    }
                                }));
                try (var data = client.download("data.csv")) {
                    assertEquals("new", new String(data.readAllBytes(), StandardCharsets.UTF_8));
                }
                assertThrows(
                        IOException.class,
                        () ->
                                client.uploadPublished(
                                        "reject.csv", bytes("new"), 3, true, () -> {}));
                try (var data = client.download("reject.csv")) {
                    assertEquals("old", new String(data.readAllBytes(), StandardCharsets.UTF_8));
                }
                assertTrue(
                        client.list("").stream()
                                .noneMatch(f -> StagedFileClient.isTemporaryName(f.getName())));
                assertTrue(client.uploadPublished("fresh.csv", bytes("fresh"), 5, false, () -> {}));
            }
            output.println("stop");
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
        } finally {
            process.destroyForcibly();
        }
    }

    private static ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
