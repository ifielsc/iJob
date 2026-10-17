package com.ifelio.ijob.core.execute.target;

import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.execute.JobExecuteService;

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
