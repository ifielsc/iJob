// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

class SourceConventionTest {
    @Test
    void packagesMatchPathsAndProductionLiteralsDoNotEmbedChinese() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(root.resolve("ijob-common")));
        Pattern packageName = Pattern.compile("(?m)^package ([\\w.]+);");
        Pattern chineseLiteral = Pattern.compile("\"[^\"\\r\\n]*[\\p{IsHan}][^\"\\r\\n]*\"");
        Pattern retiredPackages =
                Pattern.compile(
                        "com\\.ifeel\\.ijob\\.(?:plugin\\.(?:api|check|transfer|monitor)\\.|(?:contract|core|infra\\.(?:mapper|entity))\\.job\\.Task)");
        int files = 0;
        try (var modules = Files.list(root)) {
            for (Path module :
                    modules.filter(p -> p.getFileName().toString().startsWith("ijob-")).toList()) {
                Path source = module.resolve("src/main/java");
                if (!Files.isDirectory(source)) continue;
                try (var paths = Files.walk(source)) {
                    for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                        files++;
                        String text = Files.readString(file);
                        var match = packageName.matcher(text);
                        assertTrue(match.find(), file.toString());
                        assertEquals(
                                match.group(1).replace('.', '/'),
                                source.relativize(file.getParent()).toString().replace('\\', '/'),
                                file.toString());
                        assertFalse(
                                chineseLiteral.matcher(text).find(),
                                "Move built-in text to a message catalog: " + file);
                        assertFalse(
                                retiredPackages.matcher(text).find(),
                                "Retired package reference: " + file);
                    }
                }
            }
        }
        assertTrue(files > 500, "Source scan must cover the complete reactor");
    }
}
