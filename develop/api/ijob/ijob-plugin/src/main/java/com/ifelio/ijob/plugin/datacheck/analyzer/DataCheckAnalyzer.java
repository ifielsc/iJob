package com.ifelio.ijob.plugin.datacheck.analyzer;

import com.ifelio.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifelio.ijob.plugin.datacheck.model.DataCheckItemResult;

public interface DataCheckAnalyzer {
    boolean supports(DataCheckItemConfig config);

    /** Analyzes the SQL query result supplied as {@code actual}. */
    DataCheckItemResult analyze(DataCheckItemConfig config, Object actual);
}
