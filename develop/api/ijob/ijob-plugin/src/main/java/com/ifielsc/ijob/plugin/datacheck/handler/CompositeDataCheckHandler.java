package com.ifielsc.ijob.plugin.datacheck.handler;

import com.ifielsc.ijob.plugin.datacheck.config.HandlerConfig;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckContext;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckResult;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompositeDataCheckHandler implements DataCheckHandler {
    private final List<DataCheckHandler> handlers;

    public CompositeDataCheckHandler(List<DataCheckHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    public boolean supports(HandlerConfig config) {
        return handlers.stream().anyMatch(handler -> handler.supports(config));
    }

    @Override
    public void handle(HandlerConfig config, DataCheckResult result, DataCheckContext context) {
        handlers.stream()
                .filter(candidate -> candidate.supports(config))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("data check handler is not supported"))
                .handle(config, result, context);
    }
}
