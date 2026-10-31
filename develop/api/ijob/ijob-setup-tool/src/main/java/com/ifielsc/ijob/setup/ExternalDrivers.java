// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.setup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;

/**
 * Checks locally supplied JDBC archives without executing their code or connecting to a database.
 */
final class ExternalDrivers {
    private ExternalDrivers() {}

    static List<String> missing(Path directory, String vendor) throws IOException {
        List<String> required =
                switch (vendor) {
                    case "mysql" -> List.of("com/mysql/cj/jdbc/Driver.class");
                    case "oracle" -> List.of("oracle/jdbc/OracleDriver.class");
                    default -> List.of();
                };
        if (required.isEmpty()) return List.of();
        if (Files.isSymbolicLink(directory)) throw new IOException("Driver directory is a link");
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return required;
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Driver directory is not a directory");
        List<String> missing = new ArrayList<>(required);
        List<String> found = new ArrayList<>();
        try (var entries = Files.list(directory)) {
            for (Path path : entries.sorted().toList()) {
                if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    continue;
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Driver archive is not a regular file");
                try (JarFile jar = new JarFile(path.toFile())) {
                    for (String name : required) {
                        if (jar.getJarEntry(name) == null) continue;
                        if (found.contains(name)) throw new IOException("Duplicate JDBC driver");
                        found.add(name);
                        missing.remove(name);
                    }
                }
            }
        }
        return List.copyOf(missing);
    }
}
