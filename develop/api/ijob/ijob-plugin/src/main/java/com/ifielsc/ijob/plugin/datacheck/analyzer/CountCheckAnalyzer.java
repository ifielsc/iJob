// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck.analyzer;

import com.ifielsc.ijob.plugin.datacheck.config.CheckType;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckItemResult;

import org.springframework.stereotype.Component;

@Component
public class CountCheckAnalyzer extends AbstractDataCheckAnalyzer {
    @Override
    public boolean supports(DataCheckItemConfig config) {
        return config.getType() == CheckType.COUNT;
    }

    @Override
    public DataCheckItemResult analyze(DataCheckItemConfig config, Object actual) {
        return result(config, actual, matches(actual, config), "count check failed");
    }
}
