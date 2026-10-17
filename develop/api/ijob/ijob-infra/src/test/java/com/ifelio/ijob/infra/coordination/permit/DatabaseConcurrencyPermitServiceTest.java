package com.ifelio.ijob.infra.coordination.permit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifelio.ijob.infra.coordination.work.TriggerWorkClaim;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.mapper.coordination.ConcurrencyPermitMapper;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DatabaseConcurrencyPermitServiceTest {

    @Test
    void partialCompositeAcquisitionShouldReleaseAlreadyClaimedPermits() {
        Map<String, String> tokenScopes = new HashMap<>();
        List<String> releasedScopes = new ArrayList<>();
        ConcurrencyPermitMapper permitMapper =
                proxy(
                        ConcurrencyPermitMapper.class,
                        (method, arguments) -> {
                            return switch (method) {
                                case "insertSlotIfAbsent" -> 1;
                                case "claimSlot" -> {
                                    String scope = (String) arguments[0];
                                    if ("RESOURCE:20".equals(scope)) yield 0;
                                    tokenScopes.put((String) arguments[4], scope);
                                    yield 1;
                                }
                                case "selectByToken" ->
                                        permit(
                                                tokenScopes.get(arguments[0]),
                                                (String) arguments[0]);
                                case "release" -> {
                                    releasedScopes.add(tokenScopes.get(arguments[0]));
                                    yield 1;
                                }
                                default -> throw new UnsupportedOperationException(method);
                            };
                        });
        Instance instance = new Instance();
        instance.setId(100L);
        instance.setTriggerId(7L);
        InstanceMapper instanceMapper =
                proxy(
                        InstanceMapper.class,
                        (method, arguments) -> {
                            if ("selectById".equals(method)) return instance;
                            throw new UnsupportedOperationException(method);
                        });
        AppProperties properties = new AppProperties();
        ExecutionFenceCoordinator coordinator = new ExecutionFenceCoordinator(properties);
        coordinator.activate(
                new TriggerWorkClaim(
                        7L, "worker", "claim", 11L, 1, 3, LocalDateTime.now().plusMinutes(1)));
        DatabaseConcurrencyPermitService service =
                new DatabaseConcurrencyPermitService(
                        permitMapper, instanceMapper, coordinator, properties);

        List<DistributedPermit> result =
                service.tryAcquireAll(
                        100L,
                        List.of(
                                new PermitRequirement("PLUGIN:SQL", 1),
                                new PermitRequirement("RESOURCE:20", 1),
                                new PermitRequirement("TASK:1", 1)));

        assertNull(result);
        assertEquals(List.of("PLUGIN:SQL"), releasedScopes);
    }

    private DistributedPermit permit(String scope, String token) {
        return new DistributedPermit(
                scope,
                1,
                100L,
                100L,
                7L,
                "worker",
                token,
                1L,
                11L,
                LocalDateTime.now().plusMinutes(1));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, arguments) -> invocation.call(method.getName(), arguments));
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(String method, Object[] arguments);
    }
}
