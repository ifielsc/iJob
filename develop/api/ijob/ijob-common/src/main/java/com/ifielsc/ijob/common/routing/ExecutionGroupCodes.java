package com.ifielsc.ijob.common.routing;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class ExecutionGroupCodes {
    public static final int MAX_GROUPS = 16;
    public static final int MAX_LENGTH = 64;

    private static final Pattern VALID_CODE =
            Pattern.compile("[a-z0-9][a-z0-9._-]{0," + (MAX_LENGTH - 1) + "}");

    private ExecutionGroupCodes() {}

    public static List<String> normalize(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Execution group cannot be blank");
            }
            String code = value.trim().toLowerCase(Locale.ROOT);
            if (!VALID_CODE.matcher(code).matches()) {
                throw new IllegalArgumentException(
                        "Execution group must match [a-z0-9][a-z0-9._-]{0,63}");
            }
            normalized.add(code);
        }
        if (normalized.size() > MAX_GROUPS) {
            throw new IllegalArgumentException(
                    "At most " + MAX_GROUPS + " execution groups are allowed");
        }
        return List.copyOf(normalized);
    }
}
