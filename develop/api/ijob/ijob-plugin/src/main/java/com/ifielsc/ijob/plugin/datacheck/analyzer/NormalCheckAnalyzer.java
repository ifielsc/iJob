package com.ifielsc.ijob.plugin.datacheck.analyzer;

import com.ifielsc.ijob.plugin.datacheck.config.CheckType;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckItemResult;

import org.springframework.stereotype.Component;

@Component
public class NormalCheckAnalyzer extends AbstractDataCheckAnalyzer {
    @Override
    public boolean supports(DataCheckItemConfig config) {
        return config.getType() == CheckType.NORMAL;
    }

    @Override
    public DataCheckItemResult analyze(DataCheckItemConfig config, Object actual) {
        return result(config, actual, matches(actual, config), "value check failed");
    }
}
