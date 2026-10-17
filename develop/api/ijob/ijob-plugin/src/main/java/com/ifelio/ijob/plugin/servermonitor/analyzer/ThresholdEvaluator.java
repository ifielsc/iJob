package com.ifelio.ijob.plugin.servermonitor.analyzer;

import com.ifelio.ijob.plugin.servermonitor.AlarmLevel;
import com.ifelio.ijob.plugin.servermonitor.rule.Threshold;

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
