// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.validator;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;

import java.util.List;

public interface Validator {
    String type();

    List<ValidationResult> validate(List<DataRecord> records, ValidatorConfig config);

    default List<ValidationResult> validate(
            List<DataRecord> records, ValidatorConfig config, EtlContext context) {
        return validate(records, config);
    }
}
