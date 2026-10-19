// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.validator.impl;

import com.ifielsc.ijob.plugin.common.jdbc.JdbcValueKey;
import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;
import com.ifielsc.ijob.plugin.etl.validator.AbstractValidator;
import com.ifielsc.ijob.plugin.etl.validator.ValidationResult;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorConfig;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SqlValidator extends AbstractValidator {
    private final SqlBatchExecutor executor;

    @Override
    public String type() {
        return ValidatorType.SQL.name();
    }

    @Override
    public List<ValidationResult> validate(List<DataRecord> records, ValidatorConfig config) {
        return validate(records, config, null);
    }

    @Override
    public List<ValidationResult> validate(
            List<DataRecord> records, ValidatorConfig config, EtlContext context) {
        SqlValidateMode mode = SqlValidateMode.valueOf(getString(config, "mode"));
        return switch (mode) {
            case GLOBAL -> validateGlobal(records, config, context);
            case FIELD -> validateField(records, config, context);
        };
    }

    private List<ValidationResult> validateGlobal(
            List<DataRecord> records, ValidatorConfig config, EtlContext context) {
        boolean pass =
                executor.queryGlobal(
                        getLong(config, "resourceId"),
                        getString(config, "sql"),
                        getInt(config, "queryTimeoutSeconds", 300),
                        context);
        return records.stream()
                .map(
                        record ->
                                pass
                                        ? ValidationResult.success(record)
                                        : ValidationResult.fail(
                                                record, null, "sql validation failed"))
                .toList();
    }

    private List<ValidationResult> validateField(
            List<DataRecord> records, ValidatorConfig config, EtlContext context) {
        String field = getString(config, "field");
        Set<Object> keys =
                records.stream().map(record -> record.get(field)).collect(Collectors.toSet());
        Set<JdbcValueKey> existing =
                executor
                        .queryIn(
                                getLong(config, "resourceId"),
                                getString(config, "sql"),
                                keys,
                                getInt(config, "queryTimeoutSeconds", 300),
                                getInt(config, "batchSize", 500),
                                context)
                        .stream()
                        .map(JdbcValueKey::of)
                        .collect(Collectors.toSet());
        List<ValidationResult> result = new ArrayList<>();
        for (DataRecord record : records) {
            Object value = record.get(field);
            result.add(
                    existing.contains(JdbcValueKey.of(value))
                            ? ValidationResult.success(record)
                            : ValidationResult.fail(record, field, "value does not exist"));
        }
        return result;
    }
}
