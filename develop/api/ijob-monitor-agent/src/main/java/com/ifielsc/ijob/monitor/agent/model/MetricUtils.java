package com.ifielsc.ijob.monitor.agent.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MetricUtils {

    private MetricUtils() {}

    public static double percent(double value) {

        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
