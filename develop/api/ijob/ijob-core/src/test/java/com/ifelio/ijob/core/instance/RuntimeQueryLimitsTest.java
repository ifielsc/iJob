package com.ifelio.ijob.core.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.pagehelper.PageHelper;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.infra.entity.runtime.DagGatewayDecision;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.mapper.runtime.DagGatewayDecisionMapper;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class RuntimeQueryLimitsTest {
    @ParameterizedTest
    @ValueSource(ints = {999, 1000, 1001, 2000, 2001, 10000})
    void largeTreesAreBoundedAndOracleIdBatchesStayBelow1000(int count) {
        List<Instance> instances =
                java.util.stream.IntStream.range(0, count)
                        .mapToObj(
                                index -> {
                                    Instance instance = new Instance();
                                    instance.setId((long) index + 1);
                                    instance.setRootInstanceId(1L);
                                    instance.setTargetType(TargetType.JOB);
                                    return instance;
                                })
                        .toList();
        var mapper =
                (InstanceMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class[] {InstanceMapper.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("selectSummaryById"))
                                        return instances.getFirst();
                                    assertEquals(2001, PageHelper.getLocalPage().getPageSize());
                                    assertFalse(PageHelper.getLocalPage().isCount());
                                    return instances.subList(0, Math.min(2001, count));
                                });
        List<Integer> batches = new ArrayList<>();
        var decisions =
                (DagGatewayDecisionMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class[] {DagGatewayDecisionMapper.class},
                                (proxy, method, args) -> {
                                    int size = ((List<?>) args[0]).size();
                                    batches.add(size);
                                    assertTrue(size <= 500);
                                    return List.of();
                                });
        var tree = new RuntimeQueryService(null, mapper, null, decisions).getRunTree(1L, false);
        assertEquals(Math.min(count, 2000), tree.getNodes().size());
        assertEquals(count > 2000, tree.isTruncated());
        assertEquals(Math.min(count, 2000), batches.stream().mapToInt(Integer::intValue).sum());
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void queryFailureDoesNotLeavePaginationOnReusedThread() {
        Instance root = new Instance();
        root.setId(1L);
        var mapper =
                (InstanceMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class[] {InstanceMapper.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("selectSummaryById")) return root;
                                    throw new IllegalStateException();
                                });
        assertThrows(
                IllegalStateException.class,
                () ->
                        new RuntimeQueryService(
                                        null,
                                        mapper,
                                        null,
                                        (DagGatewayDecisionMapper)
                                                Proxy.newProxyInstance(
                                                        getClass().getClassLoader(),
                                                        new Class<?>[] {
                                                            DagGatewayDecisionMapper.class
                                                        },
                                                        (proxy, method, args) -> {
                                                            throw new AssertionError(
                                                                    "Unexpected decision query");
                                                        }))
                                .getRunTree(1L, true));
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void gatewayDecisionsHaveAnIndependentBudget() {
        Instance root = new Instance();
        root.setId(1L);
        root.setTargetType(TargetType.JOB);
        var mapper =
                (InstanceMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class[] {InstanceMapper.class},
                                (proxy, method, args) ->
                                        method.getName().equals("selectSummaryById")
                                                ? root
                                                : List.of(root));
        var decisions =
                (DagGatewayDecisionMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class[] {DagGatewayDecisionMapper.class},
                                (proxy, method, args) -> {
                                    assertEquals(2001, PageHelper.getLocalPage().getPageSize());
                                    return Collections.nCopies(2001, new DagGatewayDecision());
                                });
        var tree = new RuntimeQueryService(null, mapper, null, decisions).getRunTree(1L, false);
        assertTrue(tree.isGatewayDecisionsTruncated());
        assertEquals(2000, tree.getGatewayDecisions().size());
    }
}
