// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.setup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

class ExternalDriversTest {
    @TempDir Path directory;

    private void archive(String filename, String driver) throws IOException {
        try (var jar = new JarOutputStream(Files.newOutputStream(directory.resolve(filename)))) {
            jar.putNextEntry(new JarEntry(driver));
            jar.write(new byte[] {0, 1, 2});
            jar.closeEntry();
        }
    }

    @Test
    void builtInVendorsDoNotRequireExternalArchives() throws Exception {
        for (String vendor : new String[] {"sqlite", "postgresql", "sqlserver"})
            assertTrue(ExternalDrivers.missing(directory.resolve("absent"), vendor).isEmpty());
    }

    @Test
    void requiresTheSelectedDriverAndDoesNotExecuteArchiveContents() throws Exception {
        assertFalse(ExternalDrivers.missing(directory, "mysql").isEmpty());
        archive("mysql.jar", "com/mysql/cj/jdbc/Driver.class");
        assertTrue(ExternalDrivers.missing(directory, "mysql").isEmpty());
        assertFalse(ExternalDrivers.missing(directory, "oracle").isEmpty());
        archive("oracle.jar", "oracle/jdbc/OracleDriver.class");
        assertTrue(ExternalDrivers.missing(directory, "oracle").isEmpty());
    }

    @Test
    void rejectsAmbiguousDriverVersions() throws Exception {
        archive("one.jar", "oracle/jdbc/OracleDriver.class");
        archive("two.jar", "oracle/jdbc/OracleDriver.class");
        assertThrows(IOException.class, () -> ExternalDrivers.missing(directory, "oracle"));
    }

    @Test
    void rejectsMalformedArchives() throws Exception {
        Files.writeString(directory.resolve("broken.jar"), "not a zip");
        assertThrows(IOException.class, () -> ExternalDrivers.missing(directory, "mysql"));
    }
}
