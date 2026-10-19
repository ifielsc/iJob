// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.concurrency;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.concurrency.ConcurrencyResourceUpsertRequest;
import com.ifielsc.ijob.contract.concurrency.PluginConcurrencyPolicyUpdateRequest;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.infra.coordination.RuntimeMutationGuard;
import com.ifielsc.ijob.infra.entity.concurrency.ConcurrencyResource;
import com.ifielsc.ijob.infra.entity.concurrency.PluginConcurrencyPolicy;
import com.ifielsc.ijob.infra.mapper.coordination.RuntimeGuardMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class ConcurrencyPolicyServiceTest {
    private final AtomicLong epoch = new AtomicLong(100);
    private PluginConcurrencyPolicy plugin;
    private final ConcurrencyResource resource = new ConcurrencyResource();

    @Test
    void staleResourceWriteMustNotOverwrite() throws Exception {
        resource.setId(1L);
        resource.setCode("pool");
        resource.setName("Pool");
        resource.setMaxConcurrency(8);
        resource.setEnabled(true);
        setVersion(resource, "setVersion", 10L);
        var request = new ConcurrencyResourceUpsertRequest();
        request.setCode("pool");
        request.setName("Stale overwrite");
        request.setMaxConcurrency(8);
        request.setEnabled(true);
        setVersion(request, "setExpectedVersion", 9L);
        assertThrows(BusinessException.class, () -> service().updateResource(1L, request));
    }

    @Test
    void pluginRecreationMustNotReuseDeletedRevision() throws Exception {
        var service = service();
        var request = new PluginConcurrencyPolicyUpdateRequest();
        request.setExpectedVersion(0L);
        request.setMaxConcurrency(8);
        Long first = service.putPluginPolicy(PluginType.SQL, request).getVersion();
        service.deletePluginPolicy(PluginType.SQL, first);
        Long second = service.putPluginPolicy(PluginType.SQL, request).getVersion();
        assertNotEquals(first, second);
        request.setExpectedVersion(first);
        assertThrows(
                BusinessException.class, () -> service.putPluginPolicy(PluginType.SQL, request));
    }

    private static void setVersion(Object value, String method, Long version) throws Exception {
        try {
            value.getClass().getMethod(method, Long.class).invoke(value, version);
        } catch (NoSuchMethodException ignored) {
            /* Regression also runs against the old contract. */
        }
    }

    private ConcurrencyPolicyService service() throws Exception {
        var constructor = ConcurrencyPolicyService.class.getConstructors()[0];
        Object[] arguments = new Object[constructor.getParameterCount()];
        var types = constructor.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (types[i] == RuntimeMutationGuard.class)
                arguments[i] =
                        new RuntimeMutationGuard(
                                (RuntimeGuardMapper) mapper(RuntimeGuardMapper.class));
            else if (types[i] == AppProperties.class) arguments[i] = new AppProperties();
            else if (types[i] == LockService.class)
                arguments[i] =
                        new LockService(
                                null,
                                null,
                                null,
                                new AppProperties(),
                                java.util.Optional.empty(),
                                new ConcurrencyRequirementResolver(null, null));
            else if (types[i].isInterface()) arguments[i] = mapper(types[i]);
        }
        return (ConcurrencyPolicyService) constructor.newInstance(arguments);
    }

    private Object mapper(Class<?> type) {
        return Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("selectRevision")) return epoch.incrementAndGet();
                    if (type.getSimpleName().equals("ConcurrencyResourceMapper")) {
                        if (name.equals("selectById") || name.equals("selectByCode"))
                            return resource;
                    }
                    if (type.getSimpleName().equals("PluginConcurrencyPolicyMapper")) {
                        if (name.startsWith("selectByPlugin")) return plugin;
                        if (name.equals("insert")) {
                            plugin = (PluginConcurrencyPolicy) args[0];
                            return 1;
                        }
                        if (name.equals("delete")) {
                            plugin = null;
                            return 1;
                        }
                    }
                    if (name.startsWith("count")) return 0;
                    if (method.getReturnType() == int.class) return 1;
                    if (method.getReturnType() == List.class) return List.of();
                    return null;
                });
    }
}
