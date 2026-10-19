package com.ifielsc.ijob.plugin.etl.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataRecord {
    /** Preserves field insertion order for file output. */
    private final Map<String, Object> fields = new LinkedHashMap<>();

    public DataRecord() {}

    public DataRecord(Map<String, ?> source) {
        if (source != null) {
            fields.putAll(source);
        }
    }

    public Object get(String key) {
        return fields.get(key);
    }

    public void put(String key, Object value) {
        fields.put(key, value);
    }

    public boolean contains(String key) {
        return fields.containsKey(key);
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> newFields) {
        fields.clear();
        if (newFields != null) {
            fields.putAll(newFields);
        }
    }
}
