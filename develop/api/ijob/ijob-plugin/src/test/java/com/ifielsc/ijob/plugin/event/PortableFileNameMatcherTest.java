// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortableFileNameMatcherTest {
    @Test
    void shouldMatchWildcardsIndependentlyOfHostFileSystem() {
        PortableFileNameMatcher matcher =
                PortableFileNameMatcher.compile("data-??.{csv,txt}", true);

        assertTrue(matcher.matches("data-01.csv"));
        assertTrue(matcher.matches("data-ab.txt"));
        assertFalse(matcher.matches("DATA-01.CSV"));
        assertFalse(matcher.matches("nested/data-01.csv"));
    }

    @Test
    void shouldMakeCaseSensitivityExplicit() {
        PortableFileNameMatcher matcher = PortableFileNameMatcher.compile("*.CSV", false);

        assertTrue(matcher.matches("report.csv"));
    }

    @Test
    void shouldRejectPathPatterns() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PortableFileNameMatcher.compile("**/*.csv", true));
    }
}
