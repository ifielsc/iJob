package com.ifelio.ijob.plugin.etl.transformer.impl;

import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.transformer.AbstractTransformer;
import com.ifelio.ijob.plugin.etl.transformer.TransformConfig;
import com.ifelio.ijob.plugin.etl.transformer.TransformType;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FieldMappingTransformer extends AbstractTransformer {
    @Override
    public String type() {
        return TransformType.FIELD_MAPPING.name();
    }

    @Override
    public List<DataRecord> transform(List<DataRecord> records, TransformConfig config) {
        Map<String, Object> mapping = config.getProperties();

        for (DataRecord record : records) {
            Map<String, Object> newFields = new LinkedHashMap<>();

            mapping.forEach(
                    (target, source) -> {
                        newFields.put(target, record.get(source.toString()));
                    });

            record.setFields(newFields);
        }

        return records;
    }
}
