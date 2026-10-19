package com.ifielsc.ijob.plugin.datacheck.analyzer;

import com.ifielsc.ijob.plugin.datacheck.config.CompareType;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckItemResult;

import java.math.BigDecimal;

public abstract class AbstractDataCheckAnalyzer implements DataCheckAnalyzer {
    protected boolean matches(Object actual, DataCheckItemConfig config) {
        CompareType type =
                config.getCompareType() == null ? CompareType.EQ : config.getCompareType();
        return switch (type) {
            case EQ -> compare(actual, config.getExpected()) == 0;
            case NE -> compare(actual, config.getExpected()) != 0;
            case GT -> compare(actual, config.getExpected()) > 0;
            case GE -> compare(actual, config.getExpected()) >= 0;
            case LT -> compare(actual, config.getExpected()) < 0;
            case LE -> compare(actual, config.getExpected()) <= 0;
            case BETWEEN ->
                    compare(actual, config.getMinValue()) >= 0
                            && compare(actual, config.getMaxValue()) <= 0;
        };
    }

    protected Object expected(DataCheckItemConfig config) {
        return config.getCompareType() == CompareType.BETWEEN
                ? config.getMinValue() + "~" + config.getMaxValue()
                : config.getExpected();
    }

    protected DataCheckItemResult result(
            DataCheckItemConfig config, Object actual, boolean success, String failureMessage) {
        return success
                ? DataCheckItemResult.success(
                        config.getName(), actual, expected(config), config.getLevel())
                : DataCheckItemResult.failed(
                        config.getName(),
                        actual,
                        expected(config),
                        failureMessage,
                        config.getLevel());
    }

    private int compare(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected ? 0 : actual == null ? -1 : 1;
        }
        if (actual instanceof Number || expected instanceof Number) {
            try {
                return new BigDecimal(actual.toString())
                        .compareTo(new BigDecimal(expected.toString()));
            } catch (NumberFormatException ignored) {
                // Preserve textual comparison for genuinely non-numeric values.
            }
        }
        return actual.toString().compareTo(expected.toString());
    }
}
