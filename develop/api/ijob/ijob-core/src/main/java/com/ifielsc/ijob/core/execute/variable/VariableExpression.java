package com.ifielsc.ijob.core.execute.variable;

import lombok.Data;

@Data
public class VariableExpression {
    private String name;

    private String defaultValue;

    private boolean required;
}
