// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.target;

import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.execute.JobExecuteService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobTargetRunner implements TargetRunner {
    private final JobExecuteService jobExecuteService;

    @Override
    public TargetType supportType() {
        return TargetType.JOB;
    }

    @Override
    public void run(Long instanceId) {
        jobExecuteService.execute(instanceId);
    }
}
