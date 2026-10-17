package com.ifelio.ijob.core.execute.target;

import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.execute.TaskExecuteService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskTargetRunner implements TargetRunner {
    private final TaskExecuteService taskExecuteService;

    @Override
    public TargetType supportType() {
        return TargetType.TASK;
    }

    @Override
    public void run(Long instanceId) {
        taskExecuteService.execute(instanceId);
    }
}
