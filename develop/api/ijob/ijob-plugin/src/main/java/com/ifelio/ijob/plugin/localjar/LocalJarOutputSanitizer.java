package com.ifelio.ijob.plugin.localjar;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LocalJarOutputSanitizer {
    private static final int MAX_DEPTH = 16;
    private static final int MAX_NODES = 10_000;
    private static final int MAX_TEXT_CHARS = 256 * 1024;

    private int nodes;
    private int textChars;
    private final Set<Object> visiting = Collections.newSetFromMap(new IdentityHashMap<>());

    Object sanitize(Object value) {
        return sanitize(value, 0);
    }

    private Object sanitize(Object value, int depth) {
        if (++nodes > MAX_NODES || depth > MAX_DEPTH) {
            throw invalid();
        }
        if (value == null || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            addText(text.length());
            return text;
        }
        if (value instanceof Character character) {
            addText(1);
            return character.toString();
        }
        if (isJsonNumber(value)) {
            validateFinite(value);
            return value;
        }
        if (value instanceof Enum<?> enumeration) {
            String name = enumeration.name();
            addText(name.length());
            return name;
        }
        if (value instanceof List<?> list) {
            enter(list);
            try {
                List<Object> result = new ArrayList<>(list.size());
                for (Object item : list) {
                    result.add(sanitize(item, depth + 1));
                }
                return Collections.unmodifiableList(result);
            } finally {
                visiting.remove(list);
            }
        }
        if (value instanceof Map<?, ?> map) {
            enter(map);
            try {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw invalid();
                    }
                    addText(key.length());
                    result.put(key, sanitize(entry.getValue(), depth + 1));
                }
                return Collections.unmodifiableMap(result);
            } finally {
                visiting.remove(map);
            }
        }
        throw invalid();
    }

    private boolean isJsonNumber(Object value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }

    private void validateFinite(Object value) {
        if (value instanceof Double number && !Double.isFinite(number)) {
            throw invalid();
        }
        if (value instanceof Float number && !Float.isFinite(number)) {
            throw invalid();
        }
    }

    private void enter(Object value) {
        if (!visiting.add(value)) {
            throw invalid();
        }
    }

    private void addText(int amount) {
        if (amount > MAX_TEXT_CHARS || textChars > MAX_TEXT_CHARS - amount) {
            throw invalid();
        }
        textChars += amount;
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "Local JAR output must be bounded JSON-compatible data");
    }
}
