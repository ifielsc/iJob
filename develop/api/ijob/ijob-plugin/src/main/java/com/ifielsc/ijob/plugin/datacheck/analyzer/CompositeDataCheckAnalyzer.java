package com.ifielsc.ijob.plugin.datacheck.analyzer;

import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckItemResult;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompositeDataCheckAnalyzer implements DataCheckAnalyzer {
    private final List<DataCheckAnalyzer> analyzers;

    public CompositeDataCheckAnalyzer(List<DataCheckAnalyzer> analyzers) {
        this.analyzers = analyzers;
    }

    @Override
    public boolean supports(DataCheckItemConfig config) {
        return analyzers.stream().anyMatch(a -> a.supports(config));
    }

    @Override
    public DataCheckItemResult analyze(DataCheckItemConfig config, Object actual) {
        return analyzers.stream()
                .filter(a -> a.supports(config))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "No analyzer for type:" + config.getType()))
                .analyze(config, actual);
    }
}
