// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.validator;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;

import java.util.ArrayList;
import java.util.List;

public class ValidatorChain {
    private final List<ValidatorConfig> configs;
    private final ValidatorFactory factory;

    public ValidatorChain(List<ValidatorConfig> configs, ValidatorFactory factory) {
        this.configs = configs == null ? List.of() : List.copyOf(configs);
        this.factory = factory;
    }

    public List<ValidationResult> validate(List<DataRecord> records) {
        return validate(records, null);
    }

    public List<ValidationResult> validate(List<DataRecord> records, EtlContext context) {
        List<ValidationResult> result = records.stream().map(ValidationResult::success).toList();
        for (ValidatorConfig config : configs) {
            List<ValidationResult> failed =
                    result.stream().filter(item -> !item.isSuccess()).toList();
            List<DataRecord> current =
                    result.stream()
                            .filter(ValidationResult::isSuccess)
                            .map(ValidationResult::getRecord)
                            .toList();
            if (current.isEmpty()) {
                break;
            }
            List<ValidationResult> next =
                    factory.get(config.getType()).validate(current, config, context);
            if (next.size() != current.size()) {
                throw new IllegalStateException("ETL validator returned an invalid result count");
            }
            if (config.getOnFailure() == ValidationFailurePolicy.FAIL_TASK
                    && next.stream().anyMatch(item -> !item.isSuccess())) {
                if (context != null) {
                    long invalid =
                            failed.size() + next.stream().filter(item -> !item.isSuccess()).count();
                    context.setValidateErrorCount(context.getValidateErrorCount() + invalid);
                }
                throw new IllegalArgumentException("ETL validation failed: " + config.getRuleId());
            }
            List<ValidationResult> combined = new ArrayList<>(failed.size() + next.size());
            combined.addAll(failed);
            combined.addAll(next);
            result = combined;
        }
        return result;
    }
}
