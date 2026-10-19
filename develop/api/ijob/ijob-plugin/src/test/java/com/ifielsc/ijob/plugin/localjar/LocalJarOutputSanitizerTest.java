// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.localjar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class LocalJarOutputSanitizerTest {
    @Test
    void shouldCopyJsonCompatibleValuesWithoutExtensionObjects() {
        List<Object> source = new ArrayList<>();
        source.add(1);
        source.add(State.READY);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("items", source);

        @SuppressWarnings("unchecked")
        Map<String, Object> sanitized =
                (Map<String, Object>) new LocalJarOutputSanitizer().sanitize(value);
        source.add("late");

        assertEquals(List.of(1, "READY"), sanitized.get("items"));
    }

    @Test
    void shouldRejectCustomAndCyclicOutputValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalJarOutputSanitizer().sanitize(new Object()));
        List<Object> cyclic = new ArrayList<>();
        cyclic.add(cyclic);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalJarOutputSanitizer().sanitize(cyclic));
    }

    private enum State {
        READY
    }
}
