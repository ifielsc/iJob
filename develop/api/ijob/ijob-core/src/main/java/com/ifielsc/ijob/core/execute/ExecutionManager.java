// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.common.runtime.RuntimeFenceListener;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ExecutionManager implements RuntimeFenceListener {
    private final ConcurrentMap<Long, ExecutionHandle> handleMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> triggerByInstance = new ConcurrentHashMap<>();
    private final Set<Long> cancelledTriggers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean runtimeFenced = new AtomicBoolean();

    public ExecutionHandle register(Long instanceId) {
        return register(instanceId, null);
    }

    public ExecutionHandle register(Long instanceId, Long triggerId) {
        ExecutionHandle handle = new ExecutionHandle(instanceId);
        handleMap.put(instanceId, handle);
        if (triggerId != null) {
            triggerByInstance.put(instanceId, triggerId);
        }
        if (runtimeFenced.get()) handle.requestFence();
        else if (triggerId != null && cancelledTriggers.contains(triggerId)) handle.requestStop();
        return handle;
    }

    public void unregister(Long instanceId) {
        handleMap.remove(instanceId);
        triggerByInstance.remove(instanceId);
    }

    public ExecutionHandle getHandle(Long instanceId) {
        return handleMap.get(instanceId);
    }

    public boolean hasHandle(Long instanceId) {
        return getHandle(instanceId) != null;
    }

    public boolean stop(Long instanceId) {
        ExecutionHandle handle = getHandle(instanceId);
        if (handle != null) {
            handle.requestStop();
            return true;
        }
        return false;
    }

    public boolean timeout(Long instanceId) {
        ExecutionHandle handle = getHandle(instanceId);
        if (handle != null) {
            handle.requestTimeout();
            return true;
        }
        return false;
    }

    public boolean pause(Long instanceId) {
        ExecutionHandle handle = getHandle(instanceId);
        if (handle != null) {
            handle.requestPause();
            return true;
        }
        return false;
    }

    public boolean resume(Long instanceId) {
        ExecutionHandle handle = getHandle(instanceId);
        if (handle != null) {
            handle.resume();
            return true;
        }
        return false;
    }

    public void cancelTrigger(Long triggerId) {
        if (triggerId == null) {
            return;
        }
        cancelledTriggers.add(triggerId);
        triggerByInstance.forEach(
                (instanceId, mappedTriggerId) -> {
                    if (triggerId.equals(mappedTriggerId)) {
                        stop(instanceId);
                    }
                });
    }

    public void clearTriggerCancellation(Long triggerId) {
        if (triggerId != null) {
            cancelledTriggers.remove(triggerId);
        }
    }

    @Override
    public void onRuntimeFenced(String reason) {
        runtimeFenced.set(true);
        handleMap.values().forEach(ExecutionHandle::requestFence);
    }
}
