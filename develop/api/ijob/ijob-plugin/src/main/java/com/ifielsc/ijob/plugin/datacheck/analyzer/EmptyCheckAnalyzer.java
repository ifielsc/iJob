package com.ifielsc.ijob.plugin.datacheck.analyzer;

import com.ifielsc.ijob.plugin.datacheck.config.CheckType;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckItemResult;

import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class EmptyCheckAnalyzer extends AbstractDataCheckAnalyzer {
    @Override
    public boolean supports(DataCheckItemConfig config) {
        return config.getType() == CheckType.EMPTY;
    }

    @Override
    public DataCheckItemResult analyze(DataCheckItemConfig config, Object actual) {
        boolean empty =
                Boolean.TRUE.equals(actual)
                        || actual == null
                        || actual instanceof Collection<?> values && values.isEmpty();
        return empty
                ? DataCheckItemResult.success(config.getName(), actual, true, config.getLevel())
                : DataCheckItemResult.failed(
                        config.getName(), actual, true, "result is not empty", config.getLevel());
    }
}
