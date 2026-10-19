// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.event;

import java.util.regex.Pattern;

final class PortableFileNameMatcher {
    private final Pattern pattern;

    private PortableFileNameMatcher(Pattern pattern) {
        this.pattern = pattern;
    }

    static PortableFileNameMatcher compile(String glob, boolean caseSensitive) {
        if (glob == null || glob.isBlank() || glob.indexOf('/') >= 0 || glob.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("File probe pattern must match a file name only");
        }
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return new PortableFileNameMatcher(Pattern.compile(toRegex(glob), flags));
    }

    boolean matches(String fileName) {
        return fileName != null && pattern.matcher(fileName).matches();
    }

    private static String toRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            char value = glob.charAt(index);
            switch (value) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '[' -> index = appendCharacterClass(glob, index, regex);
                case '{' -> index = appendAlternatives(glob, index, regex);
                default -> appendLiteral(regex, value);
            }
        }
        return regex.append('$').toString();
    }

    private static int appendCharacterClass(String glob, int start, StringBuilder regex) {
        int end = glob.indexOf(']', start + 1);
        if (end < 0 || end == start + 1) {
            throw new IllegalArgumentException("File probe pattern has an invalid character class");
        }
        String content = glob.substring(start + 1, end);
        regex.append('[');
        int offset = 0;
        if (content.charAt(0) == '!' || content.charAt(0) == '^') {
            regex.append('^');
            offset = 1;
        }
        if (offset >= content.length()) {
            throw new IllegalArgumentException("File probe pattern has an invalid character class");
        }
        for (int index = offset; index < content.length(); index++) {
            char value = content.charAt(index);
            if (value == '\\' || value == ']') {
                regex.append('\\');
            }
            regex.append(value);
        }
        regex.append(']');
        return end;
    }

    private static int appendAlternatives(String glob, int start, StringBuilder regex) {
        int end = glob.indexOf('}', start + 1);
        if (end < 0) {
            throw new IllegalArgumentException("File probe pattern has an invalid alternative");
        }
        String[] alternatives = glob.substring(start + 1, end).split(",", -1);
        if (alternatives.length < 2) {
            throw new IllegalArgumentException("File probe pattern has an invalid alternative");
        }
        regex.append("(?:");
        for (int index = 0; index < alternatives.length; index++) {
            if (alternatives[index].isEmpty()) {
                throw new IllegalArgumentException("File probe pattern has an invalid alternative");
            }
            if (index > 0) {
                regex.append('|');
            }
            regex.append(Pattern.quote(alternatives[index]));
        }
        regex.append(')');
        return end;
    }

    private static void appendLiteral(StringBuilder regex, char value) {
        if (".()|+^$@%[]{}\\".indexOf(value) >= 0) {
            regex.append('\\');
        }
        regex.append(value);
    }
}
