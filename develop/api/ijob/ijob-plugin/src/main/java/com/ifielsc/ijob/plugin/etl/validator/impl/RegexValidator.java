package com.ifielsc.ijob.plugin.etl.validator.impl;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.validator.AbstractValidator;
import com.ifielsc.ijob.plugin.etl.validator.ValidationResult;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorConfig;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorType;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class RegexValidator extends AbstractValidator {
    @Override
    public String type() {
        return ValidatorType.REGEX.name();
    }

    @Override
    public List<ValidationResult> validate(List<DataRecord> records, ValidatorConfig config) {
        String field = getString(config, "field");

        String pattern = getString(config, "pattern");

        Pattern regex = Pattern.compile(pattern);

        List<ValidationResult> result = new ArrayList<>();

        for (DataRecord record : records) {
            Object value = record.get(field);

            if (value == null || !regex.matcher(value.toString()).matches()) {
                result.add(ValidationResult.fail(record, field, "regex not match"));

            } else {
                result.add(ValidationResult.success(record));
            }
        }

        return result;
    }
}
