// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuntimeVariablePreviewTest {
    @Test
    void redactionPreservesExactDecimalAndIntegerValues() {
        var preview =
                RuntimeVariablePreview.render(
                        "{\"row\":{\"amount\":12345678901234.123456,\"id\":9007199254740993,\"password\":\"hidden\"},\"delta\":-0.000001}");
        assertTrue(preview.redacted());
        assertFalse(preview.omitted());
        assertTrue(preview.value().contains("12345678901234.123456"));
        assertTrue(preview.value().contains("9007199254740993"));
        assertTrue(preview.value().contains("-0.000001"));
        assertFalse(preview.value().contains("hidden"));
    }

    @Test
    void recursivelyRedactsSensitiveFieldsWithoutChangingExecutionSource() {
        String source =
                "{\"count\":3,\"nested\":[{\"PASSWORD\":\"synthetic-private\",\"api_key\":\"private-key\"}],\"accessToken\":{\"value\":\"private-token\"}}";
        var preview = RuntimeVariablePreview.render(source);
        assertTrue(preview.redacted());
        assertFalse(preview.omitted());
        assertTrue(preview.value().contains("\"count\":3"));
        assertFalse(preview.value().contains("private"));
        assertTrue(source.contains("synthetic-private"));
    }

    @Test
    void rejectsOversizeMalformedAndDeepPayloadsInsteadOfReturningRawData() {
        for (String value :
                new String[] {
                    "x".repeat(4097), "password=private", "[".repeat(40) + "1" + "]".repeat(40)
                }) {
            var preview = RuntimeVariablePreview.render(value);
            assertTrue(preview.omitted());
            assertNull(preview.value());
        }
        assertEquals("{\"small\":true}", RuntimeVariablePreview.render("{\"small\":true}").value());
        assertFalse(RuntimeVariablePreview.render(null).omitted());
    }
}
