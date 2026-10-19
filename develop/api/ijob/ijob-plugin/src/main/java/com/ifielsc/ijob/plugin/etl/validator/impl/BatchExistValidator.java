// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.validator.impl;

import com.ifielsc.ijob.plugin.common.jdbc.JdbcValueKey;
import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;
import com.ifielsc.ijob.plugin.etl.validator.AbstractValidator;
import com.ifielsc.ijob.plugin.etl.validator.ValidationResult;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class BatchExistValidator extends AbstractValidator {
    protected abstract Set<Object> queryExists(Set<Object> keys, ValidatorConfig config);

    protected Set<Object> queryExists(
            Set<Object> keys, ValidatorConfig config, EtlContext context) {
        return queryExists(keys, config);
    }

    protected List<ValidationResult> validateExist(
            List<DataRecord> records, ValidatorConfig config) {
        return validateExist(records, config, null);
    }

    protected List<ValidationResult> validateExist(
            List<DataRecord> records, ValidatorConfig config, EtlContext context) {
        String field = getString(config, "field");

        Set<Object> keys =
                records.stream()
                        .map(r -> r.get(field))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Set<JdbcValueKey> exists =
                queryExists(keys, config, context).stream()
                        .map(JdbcValueKey::of)
                        .collect(Collectors.toSet());

        List<ValidationResult> result = new ArrayList<>();

        for (DataRecord record : records) {
            Object value = record.get(field);

            if (exists.contains(JdbcValueKey.of(value))) {
                result.add(ValidationResult.success(record));

            } else {
                result.add(ValidationResult.fail(record, field, "value not exists"));
            }
        }

        return result;
    }
}
