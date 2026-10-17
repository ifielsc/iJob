package com.ifelio.ijob.monitor.agent.collector;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

public abstract class AbstractMetricCollector<T> implements MetricCollector<T> {

    protected final SystemInfo systemInfo;

    protected final HardwareAbstractionLayer hardware;

    protected final OperatingSystem operatingSystem;

    protected AbstractMetricCollector(
            SystemInfo systemInfo,
            HardwareAbstractionLayer hardware,
            OperatingSystem operatingSystem) {

        this.systemInfo = systemInfo;
        this.hardware = hardware;
        this.operatingSystem = operatingSystem;
    }
}
