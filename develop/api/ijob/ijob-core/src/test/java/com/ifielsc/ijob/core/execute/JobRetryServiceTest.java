package com.ifielsc.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.execute.graph.JobExecutionNode;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlan;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlanBuilder;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.job.definition.JobExecutionSpec;
import com.ifielsc.ijob.core.runtime.definition.RuntimeDefinitionService;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class JobRetryServiceTest {
    @Test
    void retryShouldRecursivelyResetNestedFailureClosureFromSnapshots() {
        RecordingInstances instances = new RecordingInstances();
        instances.add(
                instance(1L, null, null, TargetType.JOB, 10L, 100L, InstanceStatus.FAILED, 1L));
        instances.add(instance(2L, 1L, 11L, TargetType.JOB, 20L, 200L, InstanceStatus.FAILED, 1L));
        instances.add(
                instance(3L, 1L, 12L, TargetType.TASK, 30L, 100L, InstanceStatus.STOPPED, 1L));
        instances.add(instance(4L, 2L, 21L, TargetType.TASK, 40L, 200L, InstanceStatus.FAILED, 1L));
        instances.add(
                instance(5L, 2L, 22L, TargetType.TASK, 50L, 200L, InstanceStatus.SUCCESS, 1L));
        RecordingDefinitions definitions = new RecordingDefinitions();
        RecordingPlans plans =
                new RecordingPlans(
                        Map.of(
                                100L,
                                        plan(
                                                100L,
                                                node(11L, 100L, TargetType.JOB),
                                                node(12L, 100L, TargetType.TASK)),
                                200L,
                                        chainedPlan(
                                                200L,
                                                node(21L, 200L, TargetType.TASK),
                                                node(22L, 200L, TargetType.TASK))));
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        ExecutionManager manager = new ExecutionManager();
        JobRetryService service =
                new JobRetryService(
                        instances,
                        definitions,
                        plans,
                        new JobRetryPlanner(),
                        dispatcher,
                        new ExecutionTreeControl(manager, instances));

        service.retryFailedNodes(1L);

        assertTrue(instances.claimed);
        assertEquals(List.of(4L, 5L, 2L, 3L), instances.resetIds);
        assertEquals(java.util.Set.of(11L, 12L), dispatcher.command.retryNodeIds());
        assertEquals(List.of(100L, 200L), definitions.loadedDefinitionIds);
    }

    private static JobExecutionPlan plan(Long definitionId, JobExecutionNode... nodes) {
        List<JobExecutionNode> list = List.of(nodes);
        Map<Long, JobExecutionNode> nodeMap = new LinkedHashMap<>();
        Map<Long, List<Long>> parentMap = new LinkedHashMap<>();
        Map<Long, List<Long>> childMap = new LinkedHashMap<>();
        Map<Long, Integer> indegree = new LinkedHashMap<>();
        for (JobExecutionNode node : list) {
            nodeMap.put(node.id(), node);
            parentMap.put(node.id(), List.of());
            childMap.put(node.id(), List.of());
            indegree.put(node.id(), 0);
        }
        return new JobExecutionPlan(
                definitionId, list, nodeMap, parentMap, childMap, indegree, list);
    }

    private static JobExecutionPlan chainedPlan(
            Long definitionId, JobExecutionNode first, JobExecutionNode second) {
        return new JobExecutionPlan(
                definitionId,
                List.of(first, second),
                Map.of(first.id(), first, second.id(), second),
                Map.of(first.id(), List.of(), second.id(), List.of(first.id())),
                Map.of(first.id(), List.of(second.id()), second.id(), List.of()),
                Map.of(first.id(), 0, second.id(), 1),
                List.of(first));
    }

    private static JobExecutionNode node(Long id, Long definitionId, TargetType type) {
        return new JobExecutionNode(
                id,
                definitionId,
                "node-" + id,
                "Node " + id,
                type,
                id + 1_000,
                ExecuteCondition.ALL_SUCCESS,
                FailStrategy.STOP);
    }

    private static Instance instance(
            Long id,
            Long parentId,
            Long nodeId,
            TargetType type,
            Long targetId,
            Long definitionId,
            InstanceStatus status,
            Long rootId) {
        Instance instance = new Instance();
        instance.setId(id);
        instance.setParentInstanceId(parentId);
        instance.setDefinitionNodeId(nodeId);
        instance.setTargetType(type);
        instance.setTargetId(targetId);
        instance.setDefinitionId(definitionId);
        instance.setStatus(status);
        instance.setRootInstanceId(rootId);
        return instance;
    }

    private static final class RecordingInstances extends InstanceService {
        private final Map<Long, Instance> instances = new LinkedHashMap<>();
        private final List<Long> resetIds = new ArrayList<>();
        private boolean claimed;

        private RecordingInstances() {
            super(null, null, null, null);
        }

        private void add(Instance instance) {
            instances.put(instance.getId(), instance);
        }

        @Override
        public Instance getInstance(Long id) {
            return instances.get(id);
        }

        @Override
        public List<Instance> listChildren(Long parentId) {
            return instances.values().stream()
                    .filter(instance -> parentId.equals(instance.getParentInstanceId()))
                    .toList();
        }

        @Override
        public boolean claimRetry(Long id) {
            claimed = true;
            instances.get(id).setStatus(InstanceStatus.WAITING);
            return true;
        }

        @Override
        public void resetJobBranch(Long id) {
            resetIds.add(id);
            instances.get(id).setStatus(InstanceStatus.WAITING);
        }
    }

    private static final class RecordingDefinitions extends RuntimeDefinitionService {
        private final List<Long> loadedDefinitionIds = new ArrayList<>();

        private RecordingDefinitions() {
            super(null, null);
        }

        @Override
        public JobExecutionSpec loadJob(Long jobId, Long definitionId) {
            loadedDefinitionIds.add(definitionId);
            return new JobExecutionSpec(jobId, definitionId, 1L, "job", 60);
        }
    }

    private static final class RecordingPlans extends JobExecutionPlanBuilder {
        private final Map<Long, JobExecutionPlan> plans;

        private RecordingPlans(Map<Long, JobExecutionPlan> plans) {
            super(null, null, null);
            this.plans = plans;
        }

        @Override
        public JobExecutionPlan build(Long definitionId) {
            return plans.get(definitionId);
        }
    }

    private static final class RecordingDispatcher extends JobRetryCommandDispatcher {
        private JobRetryCommand command;

        private RecordingDispatcher() {
            super(null);
        }

        @Override
        public void dispatchAfterCommit(JobRetryCommand selected) {
            command = selected;
        }
    }
}
