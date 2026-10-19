// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.analyzer;

import com.ifielsc.ijob.plugin.servermonitor.AlarmLevel;
import com.ifielsc.ijob.plugin.servermonitor.rule.Threshold;

import org.springframework.stereotype.Component;

@Component
public class ThresholdEvaluator {
    public AlarmLevel evaluate(Double value, Threshold threshold) {
        if (value == null || threshold == null) {
            return AlarmLevel.NORMAL;
        }

        if (threshold.getCritical() != null && value >= threshold.getCritical()) {
            return AlarmLevel.CRITICAL;
        }

        if (threshold.getWarning() != null && value >= threshold.getWarning()) {
            return AlarmLevel.WARNING;
        }

        return AlarmLevel.NORMAL;
    }
}
