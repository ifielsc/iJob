package com.ifielsc.ijob.plugin.etl.transformer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractTransformer implements Transformer {
    protected Object get(TransformConfig config, String key) {
        return config.getProperties().get(key);
    }

    protected String getString(TransformConfig config, String key) {
        Object value = get(config, key);

        return value == null ? null : value.toString();
    }

    protected List<String> getStringList(TransformConfig config, String key) {
        Object value = get(config, key);
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException(key + " must be a list");
        }
        return values.stream().map(String::valueOf).toList();
    }

    protected Map<String, Object> getStringObjectMap(TransformConfig config, String key) {
        Object value = get(config, key);
        if (!(value instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((mapKey, mapValue) -> result.put(String.valueOf(mapKey), mapValue));
        return result;
    }
}
