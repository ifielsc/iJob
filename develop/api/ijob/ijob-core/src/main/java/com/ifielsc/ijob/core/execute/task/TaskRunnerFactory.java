package com.ifielsc.ijob.core.execute.task;

import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.task.definition.TaskPluginAvailabilityService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskRunnerFactory {
    private final Map<PluginType, TaskRunner> runnerMap = new EnumMap<>(PluginType.class);
    private final TaskPluginAvailabilityService availabilityService;

    public TaskRunnerFactory(List<TaskRunner> runners) {
        this(runners, new TaskPluginAvailabilityService(List.of()));
    }

    @Autowired
    public TaskRunnerFactory(
            List<TaskRunner> runners, TaskPluginAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
        for (TaskRunner runner : runners) {
            PluginType pluginType = runner.supportType();
            TaskRunner existing = runnerMap.putIfAbsent(pluginType, runner);
            if (existing != null) {
                throw new IllegalStateException("duplicate task runner type: " + pluginType);
            }
        }
    }

    public TaskRunner getRunner(PluginType pluginType) {
        if (!availabilityService.isEnabled(pluginType)) {
            throw new TaskRunnerUnavailableException("Plugin is disabled: " + pluginType);
        }
        TaskRunner runner = runnerMap.get(pluginType);
        if (runner == null) {
            throw new TaskRunnerUnavailableException("unsupported plugin type: " + pluginType);
        }
        return runner;
    }
}
