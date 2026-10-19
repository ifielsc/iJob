// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.apicall;

import lombok.Data;

@Data
public class SuccessCondition {
    /**
     * JSONPath
     *
     * <p>$.code
     */
    private String expression;

    /** EQ NE GT LT */
    private String operator;

    private String expected;
}
