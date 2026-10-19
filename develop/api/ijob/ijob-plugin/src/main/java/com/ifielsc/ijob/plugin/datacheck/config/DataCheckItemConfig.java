package com.ifielsc.ijob.plugin.datacheck.config;

import lombok.Data;

@Data
public class DataCheckItemConfig {
    private String name;

    private String sql;

    private CheckType type;

    private CompareType compareType;

    private Object expected;

    private Object minValue;

    private Object maxValue;

    private String variable;

    private CheckLevel level = CheckLevel.ERROR;
}
