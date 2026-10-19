// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.script;

import java.nio.charset.StandardCharsets;

public final class ScriptOutputLimits {
    public static final int MAX_LINE_BYTES = 16 * 1024;
    public static final int MAX_CAPTURE_BYTES = 256 * 1024;
    public static final int MAX_LOG_BYTES = 256 * 1024;
    public static final int MAX_VARIABLE_VALUE_BYTES = 16 * 1024;
    public static final int MAX_VARIABLES = 100;
    public static final int MAX_FAILURE_MESSAGE_BYTES = 16 * 1024;

    private ScriptOutputLimits() {}

    public static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    public static String truncateUtf8(String value, int maxBytes) {
        if (value == null || utf8Length(value) <= maxBytes) {
            return value;
        }

        int bytes = 0;
        int end = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int codePointBytes = utf8Bytes(codePoint);
            if (bytes + codePointBytes > maxBytes) {
                break;
            }
            bytes += codePointBytes;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }

    private static int utf8Bytes(int codePoint) {
        if (codePoint <= 0x7f) {
            return 1;
        }
        if (codePoint <= 0x7ff) {
            return 2;
        }
        return codePoint <= 0xffff ? 3 : 4;
    }
}
