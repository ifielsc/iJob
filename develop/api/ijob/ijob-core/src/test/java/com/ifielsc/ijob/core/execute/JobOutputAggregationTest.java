package com.ifielsc.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.core.execute.graph.JobExecutionNode;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlan;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class JobOutputAggregationTest {
    @Test
    void shouldOnlyAggregateSuccessfulTerminalNodeOutputs() {
        JobExecutionNode first = node(1L);
        JobExecutionNode terminal = node(2L);
        JobExecutionPlan plan =
                new JobExecutionPlan(
                        10L,
                        List.of(first, terminal),
                        Map.of(1L, first, 2L, terminal),
                        Map.of(1L, List.of(), 2L, List.of(1L)),
                        Map.of(1L, List.of(2L), 2L, List.of()),
                        Map.of(1L, 0, 2L, 1),
                        List.of(first));
        Map<Long, Instance> instances =
                Map.of(
                        1L, instance("{\"intermediate\":1}"),
                        2L, instance("{\"final\":2}"));
        JobExecuteService service =
                new JobExecuteService(
                        null,
                        null,
                        new VariableSnapshotService(new ObjectMapper()),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        assertEquals("{\"final\":2}", service.aggregateJobOutputs(plan, instances));
    }

    private static JobExecutionNode node(Long id) {
        return new JobExecutionNode(
                id, 100L + id, "node-" + id, "Node " + id, null, 200L + id, null, null);
    }

    private static Instance instance(String outputVariables) {
        Instance instance = new Instance();
        instance.setStatus(InstanceStatus.SUCCESS);
        instance.setOutputVariables(outputVariables);
        return instance;
    }
}
