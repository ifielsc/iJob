package com.ifelio.ijob.contract.monitor.application;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Shared validation of the process-match wire contract; does not execute user patterns. */
public final class ProcessMatchPolicy {
    static final int MAX_REGEX_LENGTH = 256;

    private ProcessMatchPolicy() {}

    public static void validate(ProcessMatchType type, String value) {
        if (type == ProcessMatchType.PID) {
            validatePid(value);
        } else if (type == ProcessMatchType.REGEX) {
            validateRegex(value);
        }
    }

    private static void validatePid(String value) {
        if (!value.equals(value.trim())) {
            throw invalid("application PID cannot have surrounding whitespace");
        }
        try {
            if (Integer.parseInt(value) <= 0) {
                throw invalid("application PID must be positive");
            }
        } catch (NumberFormatException exception) {
            throw invalid("application PID is invalid");
        }
    }

    private static void validateRegex(String value) {
        if (value.length() > MAX_REGEX_LENGTH) {
            throw invalid("application regex is too long");
        }
        requireSafeStructure(value);
        try {
            Pattern.compile(value);
        } catch (PatternSyntaxException exception) {
            throw invalid("application regex is invalid");
        }
    }

    private static void requireSafeStructure(String value) {
        Deque<Boolean> riskyGroups = new ArrayDeque<>();
        boolean escaped = false;
        boolean characterClass = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaped) {
                if (Character.isDigit(current) || current == 'k') {
                    throw invalid("application regex back references are not supported");
                }
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (characterClass) {
                if (current == ']') {
                    characterClass = false;
                }
                continue;
            }
            if (current == '[') {
                characterClass = true;
                continue;
            }
            if (current == '(') {
                if (index + 1 < value.length() && value.charAt(index + 1) == '?') {
                    if (index + 2 >= value.length() || value.charAt(index + 2) != ':') {
                        throw invalid("application regex lookarounds and flags are not supported");
                    }
                    index += 2;
                }
                riskyGroups.push(false);
                continue;
            }
            if (current == ')') {
                if (riskyGroups.isEmpty()) {
                    continue;
                }
                boolean risky = riskyGroups.pop();
                boolean repeated =
                        index + 1 < value.length() && isQuantifier(value.charAt(index + 1));
                if (risky && repeated) {
                    throw invalid("application regex contains unsafe nested repetition");
                }
                if (!riskyGroups.isEmpty() && (risky || repeated)) {
                    riskyGroups.pop();
                    riskyGroups.push(true);
                }
                continue;
            }
            if (!riskyGroups.isEmpty() && (current == '|' || isQuantifier(current))) {
                riskyGroups.pop();
                riskyGroups.push(true);
            }
        }
    }

    private static boolean isQuantifier(char value) {
        return value == '*' || value == '+' || value == '?' || value == '{';
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
