package com.ifelio.ijob.plugin.datacheck.handler;

import com.ifelio.ijob.plugin.datacheck.config.HandlerConfig;
import com.ifelio.ijob.plugin.datacheck.model.DataCheckContext;
import com.ifelio.ijob.plugin.datacheck.model.DataCheckResult;

import org.springframework.stereotype.Component;

@Component
public class VariableDataCheckHandler implements DataCheckHandler {
    private static final String TYPE = "VARIABLE";

    @Override
    public boolean supports(HandlerConfig config) {
        return TYPE.equalsIgnoreCase(config.getType());
    }

    @Override
    public void handle(HandlerConfig config, DataCheckResult result, DataCheckContext context) {
        context.putVariable("data_check_success", result.isSuccess());

        context.putVariable("data_check_total", result.getTotal());

        context.putVariable("data_check_failed", result.getFailedCount());

        context.putVariable("data_check_result", result);

        result.getItems()
                .forEach(
                        item -> {
                            context.putVariable(item.getName(), item);
                        });
    }
}
