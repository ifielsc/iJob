// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.localjar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalJarClassLoaderTest {
    @Test
    void reservedApplicationPackagesShouldAlwaysLoadFromParent() {
        assertTrue(LocalJarClassLoader.parentFirst("com.ifielsc.ijob.spi.localjar.LocalJarTask"));
        assertTrue(LocalJarClassLoader.parentFirst("com.ifeel.ijob.spi.localjar.LocalJarTask"));
        assertTrue(
                LocalJarClassLoader.parentFirst("com.ifielsc.ijob.core.execute.task.TaskContext"));
        assertTrue(
                LocalJarClassLoader.parentFirst("org.springframework.context.ApplicationContext"));
        assertFalse(LocalJarClassLoader.parentFirst("sample.localjar.ExtensionDependency"));
        assertFalse(LocalJarClassLoader.parentFirst("com.fasterxml.jackson.databind.ObjectMapper"));
    }
}
