// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.validator;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidationResult {
    private boolean success;

    private String field;

    private String message;

    private DataRecord record;

    public static ValidationResult success(DataRecord record) {
        return builder().success(true).record(record).build();
    }

    public static ValidationResult fail(DataRecord record, String field, String message) {
        return builder().success(false).record(record).field(field).message(message).build();
    }
}
