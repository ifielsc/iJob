package com.ifelio.ijob.core.trigger;

import com.ifelio.ijob.contract.runtime.ManualTriggerRequest;
import com.ifelio.ijob.core.execute.task.TaskRunnerFactory;
import com.ifelio.ijob.core.execute.variable.VariableSnapshotService;
import com.ifelio.ijob.core.task.definition.TaskAdmissionResolver;
import com.ifelio.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifelio.ijob.infra.entity.runtime.Trigger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualTaskTriggerService {
    private final TaskAdmissionResolver taskAdmissionResolver;
    private final TriggerRuntimeService triggerRuntimeService;
    private final TriggerCommandDispatcher triggerCommandDispatcher;
    private final VariableSnapshotService variableSnapshotService;
    private final TaskRunnerFactory taskRunnerFactory;

    @Autowired
    public ManualTaskTriggerService(
            TaskAdmissionResolver taskAdmissionResolver,
            TriggerRuntimeService triggerRuntimeService,
            TriggerCommandDispatcher triggerCommandDispatcher,
            VariableSnapshotService variableSnapshotService,
            TaskRunnerFactory taskRunnerFactory) {
        this.taskAdmissionResolver = taskAdmissionResolver;
        this.triggerRuntimeService = triggerRuntimeService;
        this.triggerCommandDispatcher = triggerCommandDispatcher;
        this.variableSnapshotService = variableSnapshotService;
        this.taskRunnerFactory = taskRunnerFactory;
    }

    public ManualTaskTriggerService(
            TaskAdmissionResolver taskAdmissionResolver,
            TriggerRuntimeService triggerRuntimeService,
            TriggerCommandDispatcher triggerCommandDispatcher,
            VariableSnapshotService variableSnapshotService) {
        this(
                taskAdmissionResolver,
                triggerRuntimeService,
                triggerCommandDispatcher,
                variableSnapshotService,
                null);
    }

    @Transactional
    public Long trigger(Long taskId) {
        return trigger(taskId, null);
    }

    @Transactional
    public Long trigger(Long taskId, ManualTriggerRequest request) {
        TaskAdmissionSnapshot snapshot = taskAdmissionResolver.resolve(taskId);
        if (taskRunnerFactory != null) {
            try {
                taskRunnerFactory.getRunner(snapshot.executionSpec().getPluginType());
            } catch (com.ifelio.ijob.core.execute.task.TaskRunnerUnavailableException exception) {
                throw new com.ifelio.ijob.common.exception.BusinessException(
                        com.ifelio.ijob.common.response.ResultCode.CONFLICT,
                        com.ifelio.ijob.core.error.CoreMessage.PLUGIN_IS_DISABLED_VALUE,
                        snapshot.executionSpec().getPluginType());
            }
        }
        Trigger trigger =
                triggerRuntimeService.createManualTaskTrigger(
                        snapshot,
                        variableSnapshotService.serializeUserVariables(
                                request == null ? null : request.getVariables(),
                                request == null ? null : request.getParameterForm()),
                        request == null ? null : request.getRequestId());
        triggerCommandDispatcher.dispatchAfterCommit(trigger.getId());
        return trigger.getId();
    }
}
