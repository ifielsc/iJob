// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.database.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

class SqliteDatabasePathTest {
    @TempDir Path temporaryDirectory;

    @Test
    void shouldNormalizeAbsoluteLocalFileAndDeriveSidecar() throws IOException {
        Path database = temporaryDirectory.resolve("nested/../platform.db");
        Path normalized = SqliteDatabasePath.fromJdbcUrl("jdbc:sqlite:" + database);

        assertEquals(temporaryDirectory.toRealPath().resolve("platform.db"), normalized);
        assertEquals(Path.of(normalized + ".ijob.lock"), SqliteDatabasePath.lockFile(normalized));
    }

    @Test
    void shouldRejectMemoryRelativeUriAndUncTargets() {
        assertTrue(
                assertThrows(
                                IllegalStateException.class,
                                () -> SqliteDatabasePath.fromJdbcUrl("jdbc:sqlite::memory:"))
                        .getMessage()
                        .contains("absolute local file"));
        assertThrows(
                IllegalStateException.class,
                () -> SqliteDatabasePath.fromJdbcUrl("jdbc:sqlite:relative.db"));
        assertThrows(
                IllegalStateException.class,
                () -> SqliteDatabasePath.fromJdbcUrl("jdbc:sqlite:file:C:/data/ijob.db"));
        assertThrows(
                IllegalStateException.class,
                () -> SqliteDatabasePath.fromJdbcUrl("jdbc:sqlite:\\\\server\\share\\ijob.db"));
    }
}
