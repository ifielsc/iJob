// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.sql;

import java.util.ArrayList;
import java.util.List;

public final class SqlScriptParser {
    private SqlScriptParser() {}

    public static List<String> parse(String script, String delimiter) {
        if (script == null) {
            throw new IllegalArgumentException("SQL script is required");
        }
        if (delimiter == null || delimiter.isBlank()) {
            throw new IllegalArgumentException("SQL delimiter is required");
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        State state = State.NORMAL;
        boolean hasExecutableContent = false;
        String dollarQuote = null;

        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);

            if (state == State.NORMAL && script.startsWith(delimiter, i)) {
                addStatement(result, current, hasExecutableContent);
                current.setLength(0);
                hasExecutableContent = false;
                i += delimiter.length() - 1;
                continue;
            }

            if (state == State.NORMAL && startsWith(script, "--", i)) {
                current.append("--");
                state = State.LINE_COMMENT;
                i++;
                continue;
            }
            if (state == State.NORMAL && c == '#') {
                current.append(c);
                state = State.LINE_COMMENT;
                continue;
            }
            if (state == State.NORMAL && startsWith(script, "/*", i)) {
                current.append("/*");
                state = State.BLOCK_COMMENT;
                i++;
                continue;
            }
            if (state == State.NORMAL) {
                String candidate = dollarQuoteAt(script, i);
                if (candidate != null) {
                    current.append(candidate);
                    state = State.DOLLAR_QUOTE;
                    dollarQuote = candidate;
                    hasExecutableContent = true;
                    i += candidate.length() - 1;
                    continue;
                }
            }

            if (state == State.DOLLAR_QUOTE && script.startsWith(dollarQuote, i)) {
                current.append(dollarQuote);
                state = State.NORMAL;
                i += dollarQuote.length() - 1;
                dollarQuote = null;
                continue;
            }

            current.append(c);

            if (state == State.LINE_COMMENT) {
                if (c == '\n' || c == '\r') {
                    state = State.NORMAL;
                }
                continue;
            }
            if (state == State.BLOCK_COMMENT) {
                if (c == '*' && nextIs(script, '/', i)) {
                    current.append('/');
                    state = State.NORMAL;
                    i++;
                }
                continue;
            }
            if (state != State.NORMAL) {
                if (c == '\\' && i + 1 < script.length()) {
                    current.append(script.charAt(++i));
                    continue;
                }
                if (isClosingQuote(state, c)) {
                    if (nextIs(script, c, i)) {
                        current.append(script.charAt(++i));
                    } else {
                        state = State.NORMAL;
                    }
                }
                continue;
            }

            if (c == '\'') {
                state = State.SINGLE_QUOTE;
                hasExecutableContent = true;
            } else if (c == '"') {
                state = State.DOUBLE_QUOTE;
                hasExecutableContent = true;
            } else if (c == '`') {
                state = State.BACKTICK;
                hasExecutableContent = true;
            } else if (c == '[') {
                state = State.BRACKET;
                hasExecutableContent = true;
            } else if (!Character.isWhitespace(c)) {
                hasExecutableContent = true;
            }
        }

        if (state == State.SINGLE_QUOTE
                || state == State.DOUBLE_QUOTE
                || state == State.BACKTICK
                || state == State.BRACKET
                || state == State.DOLLAR_QUOTE
                || state == State.BLOCK_COMMENT) {
            throw new IllegalArgumentException("SQL script contains an unterminated token");
        }
        addStatement(result, current, hasExecutableContent);

        return result;
    }

    private static void addStatement(
            List<String> result, StringBuilder current, boolean hasExecutableContent) {
        String sql = current.toString().trim();
        if (hasExecutableContent && !sql.isEmpty()) {
            result.add(sql);
        }
    }

    private static boolean startsWith(String source, String expected, int index) {
        return source.startsWith(expected, index);
    }

    private static boolean nextIs(String source, char expected, int index) {
        return index + 1 < source.length() && source.charAt(index + 1) == expected;
    }

    private static boolean isClosingQuote(State state, char value) {
        return state == State.SINGLE_QUOTE && value == '\''
                || state == State.DOUBLE_QUOTE && value == '"'
                || state == State.BACKTICK && value == '`'
                || state == State.BRACKET && value == ']';
    }

    private static String dollarQuoteAt(String source, int index) {
        if (source.charAt(index) != '$') {
            return null;
        }
        int end = source.indexOf('$', index + 1);
        if (end < 0) {
            return null;
        }
        String tag = source.substring(index + 1, end);
        if (!tag.isEmpty() && !tag.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return null;
        }
        return source.substring(index, end + 1);
    }

    private enum State {
        NORMAL,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        BACKTICK,
        BRACKET,
        DOLLAR_QUOTE,
        LINE_COMMENT,
        BLOCK_COMMENT
    }
}
