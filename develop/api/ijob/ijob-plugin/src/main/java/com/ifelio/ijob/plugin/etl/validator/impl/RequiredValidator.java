package com.ifelio.ijob.plugin.etl.validator.impl;

import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.validator.AbstractValidator;
import com.ifelio.ijob.plugin.etl.validator.ValidationResult;
import com.ifelio.ijob.plugin.etl.validator.ValidatorConfig;
import com.ifelio.ijob.plugin.etl.validator.ValidatorType;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RequiredValidator extends AbstractValidator {
    @Override
    public String type() {
        return ValidatorType.REQUIRED.name();
    }

    @Override
    public List<ValidationResult> validate(List<DataRecord> records, ValidatorConfig config) {
        List<String> fields = getStringList(config, "fields");

        List<ValidationResult> result = new ArrayList<>();

        for (DataRecord record : records) {
            boolean success = true;

            for (String field : fields) {
                Object value = record.get(field);

                if (value == null || value.toString().trim().isEmpty()) {
                    result.add(ValidationResult.fail(record, field, "required field missing"));

                    success = false;

                    break;
                }
            }

            if (success) {
                result.add(ValidationResult.success(record));
            }
        }

        return result;
    }
}
