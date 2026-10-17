package com.ifelio.ijob.core.task.definition;

import com.ifelio.ijob.contract.enums.PluginType;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskPluginConfigSnapshotService {
    private final Map<PluginType, TaskPluginConfigSnapshotter> snapshotters =
            new EnumMap<>(PluginType.class);

    public TaskPluginConfigSnapshotService(List<TaskPluginConfigSnapshotter> candidates) {
        for (TaskPluginConfigSnapshotter candidate : candidates) {
            PluginType type = candidate.supportType();
            TaskPluginConfigSnapshotter existing = snapshotters.putIfAbsent(type, candidate);
            if (existing != null) {
                throw new IllegalStateException(
                        "duplicate task plugin config snapshotter: " + type);
            }
        }
    }

    public String snapshot(PluginType type, String pluginConfig) {
        TaskPluginConfigSnapshotter snapshotter = snapshotters.get(type);
        return snapshotter == null ? pluginConfig : snapshotter.snapshot(pluginConfig);
    }
}
