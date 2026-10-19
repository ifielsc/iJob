package com.ifielsc.ijob.plugin.etl.validator;

import java.util.List;

public abstract class AbstractValidator implements Validator {
    protected String getString(ValidatorConfig config, String key) {
        Object value = config.getProperties().get(key);

        return value == null ? null : value.toString();
    }

    protected long getLong(ValidatorConfig config, String key) {
        Object value = config.getProperties().get(key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value instanceof Number number
                ? number.longValue()
                : Long.parseLong(value.toString());
    }

    protected int getInt(ValidatorConfig config, String key, int fallback) {
        Object value = config.getProperties().get(key);
        if (value == null) {
            return fallback;
        }
        return value instanceof Number number
                ? number.intValue()
                : Integer.parseInt(value.toString());
    }

    protected List<String> getStringList(ValidatorConfig config, String key) {
        Object value = config.getProperties().get(key);
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException(key + " must be a list");
        }
        return values.stream().map(String::valueOf).toList();
    }
}
