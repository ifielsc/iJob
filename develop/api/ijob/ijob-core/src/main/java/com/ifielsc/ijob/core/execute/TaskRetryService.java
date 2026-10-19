// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskRetryService {
    private final InstanceService instanceService;
    private final TaskExecuteService taskExecuteService;

    public void retry(Long taskInstanceId) {
        Instance instance = instanceService.getInstance(taskInstanceId);
        if (instance == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Instance not found");
        }

        if (instance.getTargetType() != TargetType.TASK) {
            throw BusinessException.localized(ResultCode.CONFLICT, "Instance is not a task");
        }

        if (!instance.getStatus().isRetryable()) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Only failed or timed out task instances can be retried");
        }

        if (!instanceService.claimRetry(taskInstanceId)) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Task instance retry is already claimed");
        }
        taskExecuteService.execute(taskInstanceId);
    }
}
