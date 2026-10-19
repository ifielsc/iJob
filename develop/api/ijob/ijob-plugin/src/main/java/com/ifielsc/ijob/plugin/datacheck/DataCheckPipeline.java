// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck;

import com.ifielsc.ijob.plugin.datacheck.analyzer.CompositeDataCheckAnalyzer;
import com.ifielsc.ijob.plugin.datacheck.config.CheckLevel;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckConfig;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.config.HandlerConfig;
import com.ifielsc.ijob.plugin.datacheck.executor.DataCheckSqlExecutor;
import com.ifielsc.ijob.plugin.datacheck.handler.CompositeDataCheckHandler;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckContext;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckItemResult;
import com.ifielsc.ijob.plugin.datacheck.model.DataCheckResult;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataCheckPipeline {
    private final DataCheckSqlExecutor sqlExecutor;
    private final CompositeDataCheckAnalyzer analyzer;
    private final CompositeDataCheckHandler handler;

    public DataCheckResult execute(DataCheckConfig config, DataCheckContext context) {
        List<DataCheckItemResult> results = new ArrayList<>();
        int checkCount = config.getChecks().size();
        context.getTaskContext()
                .logInfo(
                        "Data check started, checks="
                                + checkCount
                                + ", resourceId="
                                + config.getResourceId());
        for (int index = 0; index < checkCount; index++) {
            DataCheckItemConfig item = config.getChecks().get(index);
            context.checkExecution();
            Object actual =
                    sqlExecutor.execute(
                            config.getResourceId(),
                            item,
                            config.getQueryTimeoutSeconds(),
                            context.getTaskContext());
            context.checkExecution();
            DataCheckItemResult result = analyzer.analyze(item, actual);
            results.add(result);
            if (item.getVariable() != null) {
                context.putVariable(item.getVariable(), actual);
            }
            context.getTaskContext().progress(90 * (index + 1) / checkCount);
        }

        boolean taskSuccess =
                results.stream()
                        .noneMatch(
                                result ->
                                        !result.isSuccess()
                                                && result.getLevel() == CheckLevel.ERROR);
        DataCheckResult finalResult = DataCheckResult.from(List.copyOf(results), taskSuccess);
        int handlerCount = config.getHandlers().size();
        context.getTaskContext()
                .logInfo(
                        "Data check analysis completed, failed="
                                + finalResult.getFailedCount()
                                + ", handlers="
                                + handlerCount);
        for (int index = 0; index < handlerCount; index++) {
            HandlerConfig handlerConfig = config.getHandlers().get(index);
            context.checkExecution();
            handler.handle(handlerConfig, finalResult, context);
            context.checkExecution();
            context.getTaskContext().progress(90 + 5 * (index + 1) / handlerCount);
        }
        if (handlerCount == 0) {
            context.getTaskContext().progress(95);
        }
        context.getTaskContext().logInfo("Data check handlers completed, success=" + taskSuccess);
        return finalResult;
    }
}
