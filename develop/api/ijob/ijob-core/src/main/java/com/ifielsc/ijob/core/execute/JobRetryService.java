package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.execute.condition.GatewayDecisionService;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlan;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlanBuilder;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.job.definition.JobExecutionSpec;
import com.ifielsc.ijob.core.runtime.definition.RuntimeDefinitionService;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class JobRetryService {
    private final InstanceService instanceService;
    private final RuntimeDefinitionService runtimeDefinitionService;
    private final JobExecutionPlanBuilder jobExecutionPlanBuilder;
    private final JobRetryPlanner jobRetryPlanner;
    private final JobRetryCommandDispatcher commandDispatcher;
    private final ExecutionTreeControl executionTreeControl;
    private final JobExecuteService jobExecuteService;
    private final TransactionTemplate transactionTemplate;
    private final GatewayDecisionService gatewayDecisionService;

    @Autowired
    public JobRetryService(
            InstanceService instanceService,
            RuntimeDefinitionService runtimeDefinitionService,
            JobExecutionPlanBuilder jobExecutionPlanBuilder,
            JobRetryPlanner jobRetryPlanner,
            JobRetryCommandDispatcher commandDispatcher,
            ExecutionTreeControl executionTreeControl,
            JobExecuteService jobExecuteService,
            TransactionTemplate transactionTemplate,
            GatewayDecisionService gatewayDecisionService) {
        this.instanceService = instanceService;
        this.runtimeDefinitionService = runtimeDefinitionService;
        this.jobExecutionPlanBuilder = jobExecutionPlanBuilder;
        this.jobRetryPlanner = jobRetryPlanner;
        this.commandDispatcher = commandDispatcher;
        this.executionTreeControl = executionTreeControl;
        this.jobExecuteService = jobExecuteService;
        this.transactionTemplate = transactionTemplate;
        this.gatewayDecisionService = gatewayDecisionService;
    }

    public JobRetryService(
            InstanceService instanceService,
            RuntimeDefinitionService runtimeDefinitionService,
            JobExecutionPlanBuilder jobExecutionPlanBuilder,
            JobRetryPlanner jobRetryPlanner,
            JobRetryCommandDispatcher commandDispatcher,
            ExecutionTreeControl executionTreeControl) {
        this(
                instanceService,
                runtimeDefinitionService,
                jobExecutionPlanBuilder,
                jobRetryPlanner,
                commandDispatcher,
                executionTreeControl,
                null,
                null,
                null);
    }

    @Transactional
    public void retryFailedNodes(Long instanceId) {
        JobRetryCommand command = prepareRetry(instanceId);
        commandDispatcher.dispatchAfterCommit(command);
    }

    public void retryFailedNodesOwned(Long instanceId) {
        if (transactionTemplate == null || jobExecuteService == null) {
            throw new IllegalStateException("Owned job retry is not configured");
        }
        JobRetryCommand command = transactionTemplate.execute(status -> prepareRetry(instanceId));
        if (command == null) {
            throw new IllegalStateException("Owned job retry preparation returned no command");
        }
        jobExecuteService.executeRetry(
                command.instanceId(), command.spec(), command.plan(), command.retryNodeIds());
    }

    private JobRetryCommand prepareRetry(Long instanceId) {
        Instance instance = instanceService.getInstance(instanceId);
        if (instance == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Instance not found");
        }

        if (instance.getTargetType() != TargetType.JOB) {
            throw BusinessException.localized(ResultCode.CONFLICT, "Not job instance");
        }

        requireRootInstance(instance);

        if (!instance.getStatus().isRetryable()) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Only failed or timed out job instances can be retried");
        }
        if (executionTreeControl.hasActiveExecutionInTree(instanceId)) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Job instance tree is still active in this process");
        }
        requireTerminalDescendants(instanceId, new HashSet<>());

        JobExecutionSpec spec =
                runtimeDefinitionService.loadJob(
                        instance.getTargetId(), instance.getDefinitionId());
        JobExecutionPlan plan = jobExecutionPlanBuilder.build(spec.jobDefinitionId());
        List<Instance> nodeInstances = instanceService.listChildren(instanceId);
        JobRetryPlan retryPlan =
                jobRetryPlanner.plan(
                        instance.getStatus(), plan, nodeInstances, failedGatewayNodes(instanceId));
        if (!instanceService.claimRetry(instanceId)) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Job instance retry is already claimed");
        }
        for (Long nodeInstanceId : retryPlan.resetInstanceIds()) {
            resetSelectedBranch(nodeInstanceId, new HashSet<>());
        }
        return new JobRetryCommand(instanceId, spec, plan, retryPlan.retryNodeIds());
    }

    private void resetSelectedBranch(Long instanceId, Set<Long> visited) {
        if (!visited.add(instanceId)) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Job retry instance tree contains a cycle");
        }
        Instance selected = requireInstance(instanceId);
        if (selected.getTargetType() == TargetType.JOB) {
            JobExecutionSpec nestedSpec =
                    runtimeDefinitionService.loadJob(
                            selected.getTargetId(), selected.getDefinitionId());
            JobExecutionPlan nestedPlan =
                    jobExecutionPlanBuilder.build(nestedSpec.jobDefinitionId());
            List<Instance> children = instanceService.listChildren(instanceId);
            boolean hasFailedBranch =
                    children.stream()
                            .anyMatch(
                                    child ->
                                            child.getStatus() != null
                                                    && (child.getStatus().isRetryable()
                                                            || child.getStatus()
                                                                    == InstanceStatus.STOPPED));
            Set<Long> failedGateways = failedGatewayNodes(instanceId);
            hasFailedBranch = hasFailedBranch || !failedGateways.isEmpty();
            JobRetryPlan childRetryPlan =
                    selected.getStatus().isRetryable() && hasFailedBranch
                            ? jobRetryPlanner.plan(
                                    selected.getStatus(), nestedPlan, children, failedGateways)
                            : jobRetryPlanner.planWhole(nestedPlan, children);
            for (Long childInstanceId : childRetryPlan.resetInstanceIds()) {
                resetSelectedBranch(childInstanceId, visited);
            }
        }
        instanceService.resetJobBranch(instanceId);
    }

    private Set<Long> failedGatewayNodes(Long jobInstanceId) {
        return gatewayDecisionService == null
                ? Set.of()
                : gatewayDecisionService.failedGatewayNodeIds(jobInstanceId);
    }

    private void requireTerminalDescendants(Long parentInstanceId, Set<Long> visited) {
        if (!visited.add(parentInstanceId)) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Job instance tree contains a cycle");
        }
        for (Instance child : instanceService.listChildren(parentInstanceId)) {
            if (child.getStatus() == null || !child.getStatus().isFinished()) {
                throw BusinessException.localized(
                        ResultCode.CONFLICT, "Job instance tree still contains active nodes");
            }
            requireTerminalDescendants(child.getId(), visited);
        }
    }

    private Instance requireInstance(Long instanceId) {
        Instance instance = instanceService.getInstance(instanceId);
        if (instance == null) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Job retry instance snapshot is missing");
        }
        return instance;
    }

    private void requireRootInstance(Instance instance) {
        if (instance.getParentInstanceId() != null
                || instance.getRootInstanceId() == null
                || !instance.getId().equals(instance.getRootInstanceId())) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Only root job instances can be retried");
        }
    }
}
