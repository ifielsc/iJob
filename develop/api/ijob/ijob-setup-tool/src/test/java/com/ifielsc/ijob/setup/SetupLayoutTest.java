// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class SetupLayoutTest {
    @TempDir Path bundle;

    @Test
    void foregroundStartPrefersSupportLayoutAndAcceptsOldFlatPackages() throws Exception {
        Path flat = Files.writeString(bundle.resolve("logging-native.xml"), "old fixture");
        assertEquals(flat, SetupTool.nativeLogging(bundle));
        Path support = Files.createDirectory(bundle.resolve("support"));
        Path current = Files.writeString(support.resolve("logging-native.xml"), "new fixture");
        assertEquals(current, SetupTool.nativeLogging(bundle));
    }

    @Test
    void incompletePackageFailsBeforeStartingTheApplication() {
        assertThrows(IOException.class, () -> SetupTool.nativeLogging(bundle));
    }
}
