// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.target;

import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.execute.TaskExecuteService;

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
