package com.ifielsc.ijob.core.trigger;

import com.ifielsc.ijob.contract.runtime.ManualTriggerRequest;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.core.job.definition.JobAdmissionResolver;
import com.ifielsc.ijob.core.job.definition.JobAdmissionSnapshot;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManualJobTriggerService {
    private final JobAdmissionResolver jobAdmissionResolver;
    private final TriggerRuntimeService triggerRuntimeService;
    private final TriggerCommandDispatcher triggerCommandDispatcher;
    private final VariableSnapshotService variableSnapshotService;

    @Transactional
    public Long trigger(Long jobId) {
        return trigger(jobId, null);
    }

    @Transactional
    public Long trigger(Long jobId, ManualTriggerRequest request) {
        JobAdmissionSnapshot snapshot = jobAdmissionResolver.resolve(jobId);
        Trigger trigger =
                triggerRuntimeService.createManualJobTrigger(
                        snapshot,
                        variableSnapshotService.serializeUserVariables(
                                request == null ? null : request.getVariables(),
                                request == null ? null : request.getParameterForm()),
                        request == null ? null : request.getRequestId());
        triggerCommandDispatcher.dispatchAfterCommit(trigger.getId());
        return trigger.getId();
    }
}
