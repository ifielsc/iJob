package com.ifelio.ijob.plugin.datacheck.handler;

import com.ifelio.ijob.plugin.datacheck.config.HandlerConfig;
import com.ifelio.ijob.plugin.datacheck.model.DataCheckContext;
import com.ifelio.ijob.plugin.datacheck.model.DataCheckResult;

public interface DataCheckHandler {
    boolean supports(HandlerConfig config);

    void handle(HandlerConfig config, DataCheckResult result, DataCheckContext context);
}
