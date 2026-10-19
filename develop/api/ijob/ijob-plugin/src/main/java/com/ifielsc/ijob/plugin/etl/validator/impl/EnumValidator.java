// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.validator.impl;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.validator.AbstractValidator;
import com.ifielsc.ijob.plugin.etl.validator.ValidationResult;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorConfig;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorType;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class EnumValidator extends AbstractValidator {
    @Override
    public String type() {
        return ValidatorType.ENUM.name();
    }

    @Override
    public List<ValidationResult> validate(List<DataRecord> records, ValidatorConfig config) {
        String field = getString(config, "field");
        Set<String> allowed = new HashSet<>(getStringList(config, "values"));
        List<ValidationResult> result = new ArrayList<>();
        for (DataRecord record : records) {
            Object value = record.get(field);
            boolean valid = value != null && allowed.contains(value.toString());
            result.add(
                    valid
                            ? ValidationResult.success(record)
                            : ValidationResult.fail(record, field, "value is not allowed"));
        }
        return result;
    }
}
