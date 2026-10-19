// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck.handler;

import com.ifielsc.ijob.plugin.datacheck.config.HandlerConfig;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckContext;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckResult;

public interface DataCheckHandler {
    boolean supports(HandlerConfig config);

    void handle(HandlerConfig config, DataCheckResult result, DataCheckContext context);
}
