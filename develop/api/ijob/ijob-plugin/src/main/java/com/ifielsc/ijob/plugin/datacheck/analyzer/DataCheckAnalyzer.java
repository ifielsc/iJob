// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck.analyzer;

import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckItemResult;

public interface DataCheckAnalyzer {
    boolean supports(DataCheckItemConfig config);

    /** Analyzes the SQL query result supplied as {@code actual}. */
    DataCheckItemResult analyze(DataCheckItemConfig config, Object actual);
}
