package com.ifelio.ijob.plugin.etl.transformer.impl;

import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.transformer.AbstractTransformer;
import com.ifelio.ijob.plugin.etl.transformer.TransformConfig;
import com.ifelio.ijob.plugin.etl.transformer.TransformType;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrimTransformer extends AbstractTransformer {
    @Override
    public String type() {
        return TransformType.TRIM.name();
    }

    @Override
    public List<DataRecord> transform(List<DataRecord> records, TransformConfig config) {
        List<String> fields = getStringList(config, "fields");

        for (DataRecord record : records) {
            for (String field : fields) {
                Object value = record.get(field);

                if (value instanceof String str) {
                    record.put(field, str.trim());
                }
            }
        }

        return records;
    }
}
