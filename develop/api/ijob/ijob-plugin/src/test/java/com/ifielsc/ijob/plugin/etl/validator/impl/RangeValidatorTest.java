// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.validator.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.validator.ValidationResult;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorConfig;

import org.junit.jupiter.api.Test;

import java.util.List;

class RangeValidatorTest {
    @Test
    void shouldReportNonNumericRecordAsInvalid() {
        ValidatorConfig config = new ValidatorConfig();
        config.getProperties().put("field", "amount");
        config.getProperties().put("min", 1);
        config.getProperties().put("max", 10);
        DataRecord invalid = new DataRecord();
        invalid.put("amount", "not-a-number");
        DataRecord valid = new DataRecord();
        valid.put("amount", "5");

        List<ValidationResult> results =
                new RangeValidator().validate(List.of(invalid, valid), config);

        assertFalse(results.get(0).isSuccess());
        assertTrue(results.get(1).isSuccess());
    }
}
