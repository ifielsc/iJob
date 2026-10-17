package com.ifelio.ijob.core.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.runtime.InstanceDTO;
import com.ifelio.ijob.contract.runtime.RunTreeDTO;
import com.ifelio.ijob.contract.runtime.TriggerDTO;
import com.ifelio.ijob.infra.entity.runtime.DagGatewayDecision;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.entity.runtime.Trigger;
import com.ifelio.ijob.infra.mapper.runtime.DagGatewayDecisionMapper;
import com.ifelio.ijob.infra.mapper.runtime.InstanceLogMapper;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;
import com.ifelio.ijob.infra.mapper.runtime.TriggerMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

class RuntimeQueryServiceTest {
    @Test
    void snapshotResolvesRootTrimsLookaheadAndReturnsChronologicalRows() {
        Instance child = new Instance();
        child.setId(11L);
        child.setRootInstanceId(10L);
        var latest = new com.ifelio.ijob.infra.entity.runtime.InstanceLog();
        latest.setId(20L);
        var earlier = new com.ifelio.ijob.infra.entity.runtime.InstanceLog();
        earlier.setId(1001L);
        var omitted = new com.ifelio.ijob.infra.entity.runtime.InstanceLog();
        omitted.setId(9999L);
        InstanceLogMapper logs =
                (InstanceLogMapper)
                        Proxy.newProxyInstance(
                                InstanceLogMapper.class.getClassLoader(),
                                new Class<?>[] {InstanceLogMapper.class},
                                (proxy, method, args) -> {
                                    assertEquals("selectLatest", method.getName());
                                    assertEquals(List.of(10L, true, 3), List.of(args));
                                    return List.of(latest, earlier, omitted);
                                });
        RuntimeQueryService service =
                new RuntimeQueryService(
                        proxy(TriggerMapper.class, "unused", null),
                        instanceMapper(child),
                        logs,
                        proxy(DagGatewayDecisionMapper.class, "unused", null));
        var snapshot = service.getLogSnapshot(11L, 2, "run");
        assertEquals(List.of(1001L, 20L), snapshot.rows().stream().map(r -> r.getId()).toList());
        org.junit.jupiter.api.Assertions.assertTrue(snapshot.truncated());
        org.junit.jupiter.api.Assertions.assertThrows(
                com.ifelio.ijob.common.exception.BusinessException.class,
                () -> service.getLogSnapshot(11L, 0, "run"));
        org.junit.jupiter.api.Assertions.assertThrows(
                com.ifelio.ijob.common.exception.BusinessException.class,
                () -> service.getLogSnapshot(11L, 2, "unknown"));
    }

    @Test
    void snapshotCapsRequestedSizeAndKeepsSelfScope() {
        Instance child = new Instance();
        child.setId(11L);
        child.setRootInstanceId(10L);
        InstanceLogMapper logs =
                (InstanceLogMapper)
                        Proxy.newProxyInstance(
                                InstanceLogMapper.class.getClassLoader(),
                                new Class<?>[] {InstanceLogMapper.class},
                                (proxy, method, args) -> {
                                    assertEquals("selectLatest", method.getName());
                                    assertEquals(List.of(11L, false, 5001), List.of(args));
                                    return List.of();
                                });
        var service =
                new RuntimeQueryService(
                        proxy(TriggerMapper.class, "unused", null),
                        instanceMapper(child),
                        logs,
                        proxy(DagGatewayDecisionMapper.class, "unused", null));
        var snapshot = service.getLogSnapshot(11L, Integer.MAX_VALUE, "self");
        assertEquals(List.of(), snapshot.rows());
        org.junit.jupiter.api.Assertions.assertFalse(snapshot.truncated());
    }

    @Test
    void missingDecisionMapperCannotSilentlyHideGatewayHistory() {
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class, () -> new RuntimeQueryService(null, null, null, null));
        assertEquals(1, RuntimeQueryService.class.getConstructors().length);
    }

    @Test
    void detailsShouldExposeSnapshotIdsWithContractNames() {
        Trigger trigger = new Trigger();
        trigger.setId(1L);
        trigger.setTargetType(TargetType.JOB);
        trigger.setDefinitionId(101L);
        Instance root = new Instance();
        root.setId(2L);
        root.setTargetName("Snapshot name before rename");
        root.setTargetType(TargetType.JOB);
        root.setDefinitionId(101L);
        root.setDefinitionNodeId(201L);
        root.setTaskDefinitionId(301L);
        RuntimeQueryService service =
                new RuntimeQueryService(
                        proxy(TriggerMapper.class, "selectById", trigger),
                        instanceMapper(root),
                        proxy(InstanceLogMapper.class, "unused", null),
                        proxy(DagGatewayDecisionMapper.class, "selectByJobInstanceIds", List.of()));

        TriggerDTO triggerDto = service.getTrigger(1L);
        InstanceDTO instanceDto = service.getInstance(2L);

        assertEquals(101L, triggerDto.getJobDefinitionId());
        assertNull(triggerDto.getTaskDefinitionId());
        assertEquals(2L, triggerDto.getRootInstanceId());
        assertEquals(101L, instanceDto.getJobDefinitionId());
        assertEquals("Snapshot name before rename", instanceDto.getTargetName());
        assertEquals(201L, instanceDto.getDefinitionNodeId());
        assertEquals(301L, instanceDto.getTaskDefinitionId());
    }

    @Test
    void treeShouldResolveRootCollectNestedJobDecisionsAndHideVariablesByDefault() {
        Instance root = new Instance();
        root.setId(10L);
        root.setRootInstanceId(10L);
        root.setTargetType(TargetType.JOB);
        root.setInputVariables("{\"secret\":true}");
        Instance nested = new Instance();
        nested.setId(11L);
        nested.setRootInstanceId(10L);
        nested.setParentInstanceId(10L);
        nested.setTargetType(TargetType.JOB);
        DagGatewayDecision decision = new DagGatewayDecision();
        decision.setId(21L);
        decision.setJobInstanceId(11L);
        InstanceMapper instances =
                (InstanceMapper)
                        Proxy.newProxyInstance(
                                InstanceMapper.class.getClassLoader(),
                                new Class<?>[] {InstanceMapper.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "selectSummaryById" -> nested;
                                            case "selectSummaryTreeByRootId",
                                                            "selectTreeByRootId" ->
                                                    List.of(root, nested);
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        DagGatewayDecisionMapper decisions =
                (DagGatewayDecisionMapper)
                        Proxy.newProxyInstance(
                                DagGatewayDecisionMapper.class.getClassLoader(),
                                new Class<?>[] {DagGatewayDecisionMapper.class},
                                (proxy, method, args) ->
                                        method.getName().equals("selectByJobInstanceIds")
                                                ? List.of(decision)
                                                : null);
        RuntimeQueryService service =
                new RuntimeQueryService(
                        proxy(TriggerMapper.class, "unused", null),
                        instances,
                        proxy(InstanceLogMapper.class, "unused", null),
                        decisions);

        RunTreeDTO tree = service.getRunTree(11L, false);

        assertEquals(10L, tree.getRootInstanceId());
        assertEquals(2, tree.getNodes().size());
        assertNull(tree.getNodes().getFirst().getInputVariables());
        assertEquals(21L, tree.getGatewayDecisions().getFirst().getId());
    }

    @Test
    void runLogsResolveRootAndBoundCursorPageWithoutExposingOtherRuns() {
        Instance child = new Instance();
        child.setId(11L);
        child.setRootInstanceId(10L);
        InstanceLogMapper logs =
                (InstanceLogMapper)
                        Proxy.newProxyInstance(
                                InstanceLogMapper.class.getClassLoader(),
                                new Class<?>[] {InstanceLogMapper.class},
                                (proxy, method, args) -> {
                                    assertEquals("selectRunAfterId", method.getName());
                                    assertEquals(List.of(10L, 20L, 500), List.of(args));
                                    return List.of();
                                });
        RuntimeQueryService service =
                new RuntimeQueryService(
                        proxy(TriggerMapper.class, "unused", null),
                        instanceMapper(child),
                        logs,
                        proxy(DagGatewayDecisionMapper.class, "unused", null));
        assertEquals(List.of(), service.listLogs(11L, 20L, 1000, "run"));
        org.junit.jupiter.api.Assertions.assertThrows(
                com.ifelio.ijob.common.exception.BusinessException.class,
                () -> service.listLogs(11L, -1L, 20, "run"));
        org.junit.jupiter.api.Assertions.assertThrows(
                com.ifelio.ijob.common.exception.BusinessException.class,
                () -> service.listLogs(11L, 0L, 20, "unknown"));
    }

    private static InstanceMapper instanceMapper(Instance instance) {
        return (InstanceMapper)
                Proxy.newProxyInstance(
                        InstanceMapper.class.getClassLoader(),
                        new Class<?>[] {InstanceMapper.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "selectSummaryById" -> instance;
                                    case "selectRootIdByTriggerId" -> instance.getId();
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, String methodName, Object result) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            if (methodName.equals(method.getName())) {
                                return result;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
    }
}
