// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck.analyzer;

import com.ifielsc.ijob.plugin.datacheck.config.CheckType;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckItemResult;

import org.springframework.stereotype.Component;

@Component
public class ExistsCheckAnalyzer extends AbstractDataCheckAnalyzer {
    @Override
    public boolean supports(DataCheckItemConfig config) {
        return config.getType() == CheckType.EXISTS;
    }

    @Override
    public DataCheckItemResult analyze(DataCheckItemConfig config, Object actual) {
        Object expected = config.getExpected() == null ? Boolean.TRUE : config.getExpected();
        boolean success =
                config.getCompareType() == com.ifielsc.ijob.plugin.datacheck.config.CompareType.NE
                        ? !expected.equals(actual)
                        : expected.equals(actual);
        return success
                ? DataCheckItemResult.success(config.getName(), actual, expected, config.getLevel())
                : DataCheckItemResult.failed(
                        config.getName(),
                        actual,
                        expected,
                        "exists check failed",
                        config.getLevel());
    }
}
