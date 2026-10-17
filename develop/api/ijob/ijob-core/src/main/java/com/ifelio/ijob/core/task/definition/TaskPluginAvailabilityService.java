package com.ifelio.ijob.core.task.definition;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.error.CoreMessage;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskPluginAvailabilityService {
    private final Map<PluginType, TaskPluginAvailability> providers =
            new EnumMap<>(PluginType.class);

    public TaskPluginAvailabilityService(List<TaskPluginAvailability> candidates) {
        for (TaskPluginAvailability candidate : candidates) {
            for (PluginType type : PluginType.values()) {
                if (!candidate.supports(type)) {
                    continue;
                }
                TaskPluginAvailability existing = providers.putIfAbsent(type, candidate);
                if (existing != null) {
                    throw new IllegalStateException("duplicate task plugin availability: " + type);
                }
            }
        }
    }

    public boolean isEnabled(PluginType type) {
        TaskPluginAvailability provider = providers.get(type);
        return provider == null || provider.isEnabled(type);
    }

    public void requireEnabledForPublication(PluginType type) {
        if (!isEnabled(type)) {
            throw new BusinessException(
                    ResultCode.CONFLICT, CoreMessage.PLUGIN_IS_DISABLED_VALUE, type);
        }
    }
}
