package com.ifelio.ijob.plugin.etl.transformer.impl;

import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.transformer.AbstractTransformer;
import com.ifelio.ijob.plugin.etl.transformer.TransformConfig;
import com.ifelio.ijob.plugin.etl.transformer.TransformType;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ReplaceTransformer extends AbstractTransformer {
    @Override
    public String type() {
        return TransformType.REPLACE.name();
    }

    @Override
    public List<DataRecord> transform(List<DataRecord> records, TransformConfig config) {
        String field = getString(config, "field");

        Map<String, Object> replace = getStringObjectMap(config, "replace");

        for (DataRecord record : records) {
            Object value = record.get(field);

            if (value != null && replace.containsKey(value.toString())) {
                record.put(field, replace.get(value.toString()));
            }
        }

        return records;
    }
}
