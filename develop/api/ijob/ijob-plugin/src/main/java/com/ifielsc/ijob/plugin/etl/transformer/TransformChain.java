// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.transformer;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransformChain {
    private static final int DEFAULT_MAX_DEDUPLICATE_KEYS = 1_000_000;

    private final TransformerFactory factory;
    private final List<TransformConfig> configs;
    private final Map<Integer, Set<List<Object>>> duplicateKeys = new HashMap<>();

    public TransformChain(TransformerFactory factory, List<TransformConfig> configs) {
        this.factory = factory;
        this.configs = configs == null ? List.of() : List.copyOf(configs);
    }

    public List<DataRecord> transform(List<DataRecord> records) {
        return transform(records, null);
    }

    public List<DataRecord> transform(List<DataRecord> records, EtlContext context) {
        List<DataRecord> result = records;
        for (int index = 0; index < configs.size(); index++) {
            TransformConfig config = configs.get(index);
            if (config.getType() == TransformType.DEDUPLICATE) {
                result = deduplicate(index, result, config);
            } else {
                result = factory.get(config.getType()).transform(result, config, context);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<DataRecord> deduplicate(
            int index, List<DataRecord> records, TransformConfig config) {
        Object configuredFields = config.getProperties().get("fields");
        if (!(configuredFields instanceof List<?> raw) || raw.isEmpty()) {
            throw new IllegalArgumentException("DEDUPLICATE fields must not be empty");
        }
        List<String> fields = (List<String>) raw;
        Object keep = config.getProperties().getOrDefault("keep", "FIRST");
        if (!"FIRST".equalsIgnoreCase(keep.toString())) {
            throw new IllegalArgumentException(
                    "streaming DEDUPLICATE currently supports " + "keep=FIRST only");
        }
        int maxKeys = number(config.getProperties().get("maxKeys"), DEFAULT_MAX_DEDUPLICATE_KEYS);
        Set<List<Object>> seen = duplicateKeys.computeIfAbsent(index, ignored -> new HashSet<>());
        List<DataRecord> result = new ArrayList<>();
        for (DataRecord record : records) {
            List<Object> key = fields.stream().map(record::get).toList();
            if (seen.add(key)) {
                if (seen.size() > maxKeys) {
                    throw new IllegalArgumentException("ETL deduplicate key limit exceeded");
                }
                result.add(record);
            }
        }
        return result;
    }

    private int number(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        int result =
                value instanceof Number number
                        ? number.intValue()
                        : Integer.parseInt(value.toString());
        if (result <= 0) {
            throw new IllegalArgumentException("DEDUPLICATE maxKeys must be greater than 0");
        }
        return result;
    }
}
