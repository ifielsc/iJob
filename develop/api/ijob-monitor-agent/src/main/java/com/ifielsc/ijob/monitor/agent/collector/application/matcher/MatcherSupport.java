// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.collector.application.matcher;

import java.util.Locale;

public final class MatcherSupport {

    private MatcherSupport() {}

    /** Compares trimmed values without case sensitivity; null never matches. */
    public static boolean equals(String left, String right) {

        if (left == null || right == null) {
            return false;
        }

        return left.trim().equalsIgnoreCase(right.trim());
    }

    /** Matches a substring without case sensitivity using Locale.ROOT; null never matches. */
    public static boolean contains(String source, String keyword) {

        if (source == null || keyword == null) {
            return false;
        }

        return source.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    public static boolean regex(String source, String regex) {

        if (source == null || regex == null) {
            return false;
        }

        return source.matches(regex);
    }
}
