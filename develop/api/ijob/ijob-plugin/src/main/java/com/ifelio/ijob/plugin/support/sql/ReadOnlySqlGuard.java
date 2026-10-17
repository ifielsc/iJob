package com.ifelio.ijob.plugin.support.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight admission guard for SQL that must remain read-only. Database credentials and JDBC
 * read-only mode remain the authoritative enforcement boundary.
 */
public final class ReadOnlySqlGuard {
    private static final Set<String> MUTATING_CTE_KEYWORDS =
            Set.of("insert", "update", "delete", "merge", "replace");

    private ReadOnlySqlGuard() {}

    public static boolean isReadQuery(String sql) {
        List<String> tokens = tokens(sql);
        if (tokens.isEmpty()
                || !hasOnlyTerminalSemicolon(tokens)
                || !hasBalancedParentheses(tokens)) {
            return false;
        }
        tokens = withoutTerminalSemicolon(tokens);
        int first = firstKeywordIndex(tokens);
        if (first < 0) {
            return false;
        }
        String keyword = tokens.get(first);
        if ("select".equals(keyword)) {
            return !containsSelectMutation(tokens, first + 1);
        }
        if (!"with".equals(keyword)) {
            return false;
        }
        for (int index = first + 1; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (MUTATING_CTE_KEYWORDS.contains(token)) {
                return false;
            }
        }
        return hasTopLevelSelect(tokens, first) && !containsSelectMutation(tokens, first + 1);
    }

    private static boolean hasTopLevelSelect(List<String> tokens, int withIndex) {
        int baseDepth = 0;
        for (int index = 0; index < withIndex; index++) {
            if ("(".equals(tokens.get(index))) {
                baseDepth++;
            }
        }
        int depth = baseDepth;
        for (int index = withIndex + 1; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if ("(".equals(token)) {
                depth++;
            } else if (")".equals(token)) {
                depth--;
            } else if (depth == baseDepth && "select".equals(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSelectMutation(List<String> tokens, int start) {
        for (int index = start; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if ("into".equals(token)) {
                return true;
            }
            if ("for".equals(token) && nextWordIs(tokens, index + 1, "update")) {
                return true;
            }
            if ("lock".equals(token) && nextWordsAre(tokens, index + 1, "in", "share", "mode")) {
                return true;
            }
        }
        return false;
    }

    private static boolean nextWordIs(List<String> tokens, int start, String expected) {
        int index = nextWordIndex(tokens, start);
        return index >= 0 && expected.equals(tokens.get(index));
    }

    private static boolean nextWordsAre(List<String> tokens, int start, String... expected) {
        int index = start;
        for (String word : expected) {
            index = nextWordIndex(tokens, index);
            if (index < 0 || !word.equals(tokens.get(index))) {
                return false;
            }
            index++;
        }
        return true;
    }

    private static int nextWordIndex(List<String> tokens, int start) {
        for (int index = start; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (!"(".equals(token) && !")".equals(token)) {
                return index;
            }
        }
        return -1;
    }

    private static int firstKeywordIndex(List<String> tokens) {
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (!"(".equals(token) && !")".equals(token)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean hasOnlyTerminalSemicolon(List<String> tokens) {
        int semicolons = 0;
        for (int index = 0; index < tokens.size(); index++) {
            if (";".equals(tokens.get(index))) {
                semicolons++;
                if (index != tokens.size() - 1) {
                    return false;
                }
            }
        }
        return semicolons <= 1;
    }

    private static boolean hasBalancedParentheses(List<String> tokens) {
        int depth = 0;
        for (String token : tokens) {
            if ("(".equals(token)) {
                depth++;
            } else if (")".equals(token) && --depth < 0) {
                return false;
            }
        }
        return depth == 0;
    }

    private static List<String> withoutTerminalSemicolon(List<String> tokens) {
        if (!tokens.isEmpty() && ";".equals(tokens.getLast())) {
            return tokens.subList(0, tokens.size() - 1);
        }
        return tokens;
    }

    private static List<String> tokens(String sql) {
        if (sql == null) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (startsWith(sql, index, "--")) {
                index = skipLine(sql, index + 2);
                continue;
            }
            if (current == '#') {
                index = skipLine(sql, index + 1);
                continue;
            }
            if (startsWith(sql, index, "/*")) {
                index = skipBlockComment(sql, index + 2);
                if (index < 0) {
                    return List.of();
                }
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                index = skipQuoted(sql, index, current);
                if (index < 0) {
                    return List.of();
                }
                continue;
            }
            if (current == '[') {
                index = skipBracketIdentifier(sql, index + 1);
                if (index < 0) {
                    return List.of();
                }
                continue;
            }
            if (current == '(' || current == ')' || current == ';') {
                tokens.add(String.valueOf(current));
                index++;
                continue;
            }
            if (Character.isLetter(current) || current == '_') {
                int end = index + 1;
                while (end < sql.length()) {
                    char value = sql.charAt(end);
                    if (!Character.isLetterOrDigit(value) && value != '_' && value != '$') {
                        break;
                    }
                    end++;
                }
                tokens.add(sql.substring(index, end).toLowerCase(Locale.ROOT));
                index = end;
                continue;
            }
            index++;
        }
        return tokens;
    }

    private static int skipLine(String sql, int index) {
        int lineEnd = sql.indexOf('\n', index);
        return lineEnd < 0 ? sql.length() : lineEnd + 1;
    }

    private static int skipBlockComment(String sql, int index) {
        int end = sql.indexOf("*/", index);
        return end < 0 ? -1 : end + 2;
    }

    private static int skipQuoted(String sql, int index, char quote) {
        int current = index + 1;
        while (current < sql.length()) {
            char value = sql.charAt(current);
            if (value == '\\' && quote == '\'' && current + 1 < sql.length()) {
                current += 2;
                continue;
            }
            if (value == quote) {
                if (current + 1 < sql.length() && sql.charAt(current + 1) == quote) {
                    current += 2;
                    continue;
                }
                return current + 1;
            }
            current++;
        }
        return -1;
    }

    private static int skipBracketIdentifier(String sql, int index) {
        int current = index;
        while (current < sql.length()) {
            if (sql.charAt(current) == ']') {
                if (current + 1 < sql.length() && sql.charAt(current + 1) == ']') {
                    current += 2;
                    continue;
                }
                return current + 1;
            }
            current++;
        }
        return -1;
    }

    private static boolean startsWith(String value, int index, String expected) {
        return value.regionMatches(index, expected, 0, expected.length());
    }
}
