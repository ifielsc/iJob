// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.plugin.sql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Query diagnostics only; connection/setup failures never expose driver messages. */
final class SqlPreviewDiagnostic {
    private static final String MASK = "[REDACTED]";
    private static final Pattern LITERAL = Pattern.compile("'((?:''|\\\\.|[^'\\\\])*+)(?:'|$)");
    private static final Pattern DOLLAR =
            Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*|)\\$([\\s\\S]*?)\\$\\1\\$");
    private static final Pattern ORACLE = Pattern.compile("(?i)q'([\\[({<])([\\s\\S]*?)[\\])}>]'");
    private static final Pattern CREDENTIAL =
            Pattern.compile(
                    "(?i)(\\b(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|secret[_-]?key|session[_-]?token|username|user|authorization|host|hostname|server|address)\\b[\\\"']?\\s*[:=]\\s*)(?:\\\"[^\\\"]*(?:\\\"|$)|'[^']*(?:'|$)|[^\\s;,]+)");
    private static final Pattern URL =
            Pattern.compile("(?i)\\bjdbc:[^\\s\\\"']+|\\b[a-z][a-z0-9+.-]*://[^\\s\\\"']+");
    private static final Pattern ACCOUNT =
            Pattern.compile("(?i)\\buser\\s+['\\\"][^'\\\"]*['\\\"](?:@['\\\"][^'\\\"]*['\\\"])?");
    private static final Pattern PATH =
            Pattern.compile("(?i)(?:[A-Z]:[\\\\/]|/(?:[A-Za-z0-9_.-]+/)+)[^\\s'\\\",;)]*");
    private static final Pattern QUOTED =
            Pattern.compile(
                    "'(?:''|\\\\.|[^'\\\\])*+'|\"(?:\"\"|\\\\.|[^\"\\\\])*+\"|`(?:``|[^`])*+`");
    private static final Pattern OBJECT =
            Pattern.compile(
                    "(?i)\\b(?:column|table|relation|view|schema|database|function|operator|constraint)\\s*$");

    private SqlPreviewDiagnostic() {}

    static String message(String raw, String sql, Collection<?> values) {
        if (raw == null || raw.isBlank()) return null;
        // Reject oversized text before processing; truncating first could expose half a secret.
        if (raw.length() > 65536) return "Database diagnostic exceeds the safe display limit.";
        Set<String> hidden = new LinkedHashSet<>();
        for (Object value : values) if (value != null) add(hidden, String.valueOf(value));
        var dollar = DOLLAR.matcher(sql);
        while (dollar.find()) add(hidden, dollar.group(2));
        var oracle = ORACLE.matcher(sql);
        while (oracle.find()) add(hidden, oracle.group(2));
        var literal = LITERAL.matcher(sql);
        while (literal.find())
            add(hidden, literal.group(1).replace("''", "'").replaceAll("\\\\(.)", "$1"));
        String result =
                raw.replaceAll(
                        "(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?(?:-----END [^-]*PRIVATE"
                                + " KEY-----|$)",
                        MASK);
        result = CREDENTIAL.matcher(result).replaceAll("$1" + MASK);
        result = ACCOUNT.matcher(result).replaceAll("user " + MASK);
        result = URL.matcher(result).replaceAll(MASK);
        result = PATH.matcher(result).replaceAll(MASK);
        result = result.replaceAll("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b", MASK);
        result = result.replaceAll("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*", "Bearer " + MASK);
        var ordered = new ArrayList<>(hidden);
        ordered.sort(Comparator.comparingInt(String::length).reversed());
        for (String value : ordered) {
            String pattern = Pattern.quote(value);
            if (value.length() < 4)
                pattern = "(?<![\\p{L}\\p{N}_])" + pattern + "(?![\\p{L}\\p{N}_])";
            result =
                    Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                            .matcher(result)
                            .replaceAll(MASK);
        }
        // Keep quoted schema identifiers, but hide values/snippets echoed by a driver.
        var quoted = QUOTED.matcher(result);
        StringBuilder safe = new StringBuilder();
        while (quoted.find()) {
            String prefix = result.substring(Math.max(0, quoted.start() - 40), quoted.start());
            quoted.appendReplacement(
                    safe,
                    java.util.regex.Matcher.quoteReplacement(
                            OBJECT.matcher(prefix).find() ? quoted.group() : MASK));
        }
        quoted.appendTail(safe);
        result = safe.toString();
        result = result.replaceAll("[\\p{Cc}\\p{Cf}&&[^\\n\\t]]", " ").strip();
        int count = result.codePointCount(0, result.length());
        return count > 4096
                ? result.substring(0, result.offsetByCodePoints(0, 4096)) + "…"
                : result;
    }

    private static void add(Set<String> hidden, String value) {
        if (value.isEmpty()) return;
        hidden.add(value);
        hidden.add(value.replace("'", "''"));
        hidden.add(value.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\""));
    }
}
