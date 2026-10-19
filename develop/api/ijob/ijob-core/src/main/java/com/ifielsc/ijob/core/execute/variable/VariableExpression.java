// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.variable;

import lombok.Data;

@Data
public class VariableExpression {
    private String name;

    private String defaultValue;

    private boolean required;
}
