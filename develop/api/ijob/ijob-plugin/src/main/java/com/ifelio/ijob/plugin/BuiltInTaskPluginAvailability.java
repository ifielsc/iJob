package com.ifelio.ijob.plugin;

import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.task.definition.TaskPluginAvailability;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BuiltInTaskPluginAvailability implements TaskPluginAvailability {
    private final PluginAvailabilityProperties properties;

    @Override
    public boolean supports(PluginType type) {
        return type != null;
    }

    @Override
    public boolean isEnabled(PluginType type) {
        return switch (type) {
            case EVENT -> properties.getEvent().isEnabled();
            case SCRIPT -> properties.getScript().isEnabled();
            case SQL -> properties.getSql().isEnabled();
            case FILE_TRANSFER -> properties.getFileTransfer().isEnabled();
            case API_CALL -> properties.getApiCall().isEnabled();
            case PUSH -> properties.getPush().isEnabled();
            case DATA_CHECK -> properties.getDataCheck().isEnabled();
            case SERVER_MONITOR -> properties.getServerMonitor().isEnabled();
            case ETL -> properties.getEtl().isEnabled();
            case BATCH_ETL -> properties.getBatchEtl().isEnabled();
            case LOCAL_JAR -> properties.getLocalJar().isEnabled();
        };
    }
}
