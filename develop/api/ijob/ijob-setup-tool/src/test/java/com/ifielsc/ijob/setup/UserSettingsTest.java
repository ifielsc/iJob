// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.setup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class UserSettingsTest {
    @TempDir Path home;

    @Test
    void validatesDocumentedFormatsAndBounds() {
        assertTrue(UserSettings.valid("port", "18080"));
        assertFalse(UserSettings.valid("port", "65536"));
        assertFalse(UserSettings.valid("port", "0"));
        assertFalse(UserSettings.valid("boolean", "yes"));
        assertFalse(UserSettings.valid("duration", "-3s"));
        assertTrue(UserSettings.valid("duration", "500ms"));
        assertFalse(UserSettings.valid("size", "0MB"));
        assertTrue(UserSettings.valid("locale", "zh-CN"));
    }

    @Test
    void reportsKeysWithoutLeakingValues() throws Exception {
        String privateValue = "not-a-port-private-value";
        Files.writeString(home.resolve("application.properties"), "server.port=" + privateValue);
        var bytes = new ByteArrayOutputStream();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UserSettings.run(
                                new String[] {
                                    "check-settings", "--home", home.toString(), "--lang", "zh"
                                },
                                new PrintStream(bytes, true, StandardCharsets.UTF_8)));
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("server.port"));
        assertFalse(output.contains(privateValue));
    }

    @Test
    void duplicateKeysCannotSilentlyOverrideConfiguration() throws Exception {
        Files.writeString(
                home.resolve("application.properties"), "server.port=18080\nserver.port=18081\n");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UserSettings.run(
                                new String[] {"check-settings", "--home", home.toString()},
                                System.out));
    }

    @Test
    void catalogHasBothLanguagesAndNoGeneratedSecrets() throws Exception {
        for (String[] row : UserSettings.catalog()) {
            assertTrue(row.length == 5);
            assertFalse(row[3].isBlank());
            assertFalse(row[4].isBlank());
            assertFalse(row[0].contains("aes"));
            assertFalse(row[0].contains("bootstrap"));
        }
        assertTrue(SetupMessages.text(false, "prompt.username").contains("username"));
        assertTrue(SetupMessages.text(true, "prompt.username").contains("管理员"));
    }
}
