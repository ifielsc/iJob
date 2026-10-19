package com.ifielsc.ijob.plugin.etl.transformer.impl;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.transformer.AbstractTransformer;
import com.ifielsc.ijob.plugin.etl.transformer.TransformConfig;
import com.ifielsc.ijob.plugin.etl.transformer.TransformType;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class TypeConvertTransformer extends AbstractTransformer {
    @Override
    public String type() {
        return TransformType.TYPE_CONVERT.name();
    }

    @Override
    public List<DataRecord> transform(List<DataRecord> records, TransformConfig config) {
        String field = getString(config, "field");
        String targetType = getString(config, "target");
        for (DataRecord record : records) {
            Object value = record.get(field);
            if (value != null) {
                record.put(field, convert(value, targetType));
            }
        }
        return records;
    }

    private Object convert(Object value, String targetType) {
        String text = value.toString();
        return switch (targetType) {
            case "STRING" -> text;
            case "INTEGER" -> Integer.valueOf(text);
            case "LONG" -> Long.valueOf(text);
            case "DOUBLE" -> Double.valueOf(text);
            case "DECIMAL" -> new BigDecimal(text);
            case "BOOLEAN" -> booleanValue(text);
            case "DATE" -> LocalDate.parse(text);
            case "TIMESTAMP" -> LocalDateTime.parse(text);
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported TYPE_CONVERT target: " + targetType);
        };
    }

    private boolean booleanValue(String value) {
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean value: " + value);
    }
}
