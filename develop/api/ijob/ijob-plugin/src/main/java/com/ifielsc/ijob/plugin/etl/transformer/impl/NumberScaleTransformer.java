package com.ifielsc.ijob.plugin.etl.transformer.impl;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.transformer.AbstractTransformer;
import com.ifielsc.ijob.plugin.etl.transformer.TransformConfig;
import com.ifielsc.ijob.plugin.etl.transformer.TransformType;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class NumberScaleTransformer extends AbstractTransformer {
    @Override
    public String type() {
        return TransformType.NUMBER_SCALE.name();
    }

    @Override
    public List<DataRecord> transform(List<DataRecord> records, TransformConfig config) {
        String field = getString(config, "field");
        String target = getString(config, "targetField");
        if (target == null || target.isBlank()) {
            target = field;
        }
        BigDecimal multiply = decimal(config, "multiply", BigDecimal.ONE);
        BigDecimal divide = decimal(config, "divide", BigDecimal.ONE);
        BigDecimal add = decimal(config, "add", BigDecimal.ZERO);
        int scale = integer(config, "scale", -1);
        RoundingMode rounding = RoundingMode.valueOf(string(config, "roundingMode", "HALF_UP"));
        if (divide.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("NUMBER_SCALE divide must not be zero");
        }
        for (DataRecord record : records) {
            Object value = record.get(field);
            if (value == null) {
                continue;
            }
            BigDecimal result =
                    new BigDecimal(value.toString())
                            .multiply(multiply)
                            .divide(divide, scale < 0 ? 16 : scale, rounding)
                            .add(add);
            if (scale >= 0) {
                result = result.setScale(scale, rounding);
            } else {
                result = result.stripTrailingZeros();
            }
            record.put(target, result);
        }
        return records;
    }

    private BigDecimal decimal(TransformConfig config, String key, BigDecimal fallback) {
        Object value = get(config, key);
        return value == null ? fallback : new BigDecimal(value.toString());
    }

    private int integer(TransformConfig config, String key, int fallback) {
        Object value = get(config, key);
        return value == null ? fallback : Integer.parseInt(value.toString());
    }

    private String string(TransformConfig config, String key, String fallback) {
        String value = getString(config, key);
        return value == null ? fallback : value;
    }
}
