package com.ifelio.ijob.plugin.etl.validator.impl;

import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.validator.AbstractValidator;
import com.ifelio.ijob.plugin.etl.validator.ValidationResult;
import com.ifelio.ijob.plugin.etl.validator.ValidatorConfig;
import com.ifelio.ijob.plugin.etl.validator.ValidatorType;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class RangeValidator extends AbstractValidator {
    @Override
    public String type() {
        return ValidatorType.RANGE.name();
    }

    @Override
    public List<ValidationResult> validate(List<DataRecord> records, ValidatorConfig config) {
        String field = getString(config, "field");
        BigDecimal min = decimal(config.getProperties().get("min"));
        BigDecimal max = decimal(config.getProperties().get("max"));
        if (min == null && max == null) {
            throw new IllegalArgumentException("RANGE requires min or max");
        }
        List<ValidationResult> result = new ArrayList<>();
        for (DataRecord record : records) {
            Object value = record.get(field);
            boolean valid = value != null;
            if (valid) {
                try {
                    BigDecimal number = new BigDecimal(value.toString());
                    valid =
                            (min == null || number.compareTo(min) >= 0)
                                    && (max == null || number.compareTo(max) <= 0);
                } catch (NumberFormatException exception) {
                    valid = false;
                }
            }
            result.add(
                    valid
                            ? ValidationResult.success(record)
                            : ValidationResult.fail(
                                    record, field, "value is outside the allowed range"));
        }
        return result;
    }

    private BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }
}
