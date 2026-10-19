// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.analyzer;

import com.ifielsc.ijob.plugin.servermonitor.AlarmLevel;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckItem;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.MonitorItemType;
import com.ifielsc.ijob.plugin.servermonitor.rule.Threshold;

public abstract class AbstractMonitorAnalyzer implements MonitorAnalyzer {
    protected final ThresholdEvaluator evaluator;

    protected AbstractMonitorAnalyzer(ThresholdEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    protected void addResult(
            MonitorCheckResult result,
            MonitorItemType type,
            String name,
            Object current,
            Threshold threshold) {
        AlarmLevel level = evaluator.evaluate(convert(current), threshold);
        result.add(
                MonitorCheckItem.builder()
                        .type(type)
                        .name(name)
                        .current(current)
                        .threshold(threshold)
                        .level(level)
                        .message(buildMessage(name, level))
                        .messageKey("monitor." + level.name().toLowerCase(java.util.Locale.ROOT))
                        .messageArgs(new Object[] {name})
                        .build());
    }

    private Double convert(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private String buildMessage(String name, AlarmLevel level) {
        return switch (level) {
            case NORMAL -> name + " is normal";
            case WARNING -> name + " reached the warning threshold";
            case CRITICAL -> name + " reached the critical threshold";
        };
    }
}
