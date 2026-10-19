// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.task;

import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.instance.InstanceLogService;
import com.ifielsc.ijob.core.instance.InstanceService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@Slf4j
public class TaskContext {
    private final Long instanceId;
    private final ExecutionHandle handle;

    private final InstanceService instanceService;
    private final InstanceLogService instanceLogService;

    private final VariableResolver variableResolver;
    private final Map<String, Object> inputVariables;
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private final Map<String, Object> outputVariables =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private final List<AutoCloseable> resources = new CopyOnWriteArrayList<>();
    private final Object resourceLock = new Object();
    private final Object cancellationLock = new Object();
    private CancellationReason cancellationReason;
    private final List<java.util.function.Consumer<CancellationReason>> cancellationListeners =
            new java.util.ArrayList<>();

    private final Set<String> publishedEvents = ConcurrentHashMap.newKeySet();
    private volatile Long inboundEventId;
    private volatile String inboundEventCode;

    public TaskContext(
            Long instanceId,
            ExecutionHandle handle,
            InstanceService instanceService,
            InstanceLogService instanceLogService,
            VariableResolver variableResolver) {
        this(instanceId, handle, instanceService, instanceLogService, variableResolver, Map.of());
    }

    public TaskContext(
            Long instanceId,
            ExecutionHandle handle,
            InstanceService instanceService,
            InstanceLogService instanceLogService,
            VariableResolver variableResolver,
            Map<String, Object> inputVariables) {
        this.instanceId = instanceId;
        this.handle = handle;
        this.instanceService = instanceService;
        this.instanceLogService = instanceLogService;
        this.variableResolver = variableResolver;
        this.inputVariables = inputVariables == null ? Map.of() : Map.copyOf(inputVariables);
        variables.putAll(this.inputVariables);
    }

    public String resolve(String text) {
        return variableResolver.resolve(text, variables);
    }

    public String resolveScript(String text) {
        return variableResolver.resolveScript(text, variables);
    }

    public Object resolveObject(Object value) {
        return variableResolver.resolveObject(value, variables);
    }

    public Map<String, Object> resolveObjectMap(Map<String, Object> value) {
        return variableResolver.resolveObjectMap(value, variables);
    }

    public void checkStop() {
        handle.checkStop();
    }

    public void checkPause() {
        handle.checkPause();
    }

    public void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new StopException();
        }
    }

    public void checkExecution() {
        checkStop();
        checkPause();
        checkInterrupted();
    }

    public void progress(Integer progress) {
        instanceService.updateProgress(instanceId, progress);
    }

    public void logInfo(String msg) {
        instanceLogService.info(instanceId, msg);
    }

    public void logWarn(String msg) {
        instanceLogService.warn(instanceId, msg);
    }

    public void logError(String msg) {
        instanceLogService.error(instanceId, msg);
    }

    public void registerResource(AutoCloseable resource) {
        if (resource != null) {
            synchronized (resourceLock) {
                resources.add(resource);
            }
        }
    }

    public void unregisterResource(AutoCloseable resource) {
        synchronized (resourceLock) {
            resources.remove(resource);
        }
    }

    /** Register an attempt-scoped, nonblocking cancellation hook. Closing only unregisters it. */
    public AutoCloseable onCancellation(java.util.function.Consumer<CancellationReason> listener) {
        synchronized (cancellationLock) {
            if (cancellationReason != null) listener.accept(cancellationReason);
            else cancellationListeners.add(listener);
        }
        return () -> {
            synchronized (cancellationLock) {
                cancellationListeners.remove(listener);
            }
        };
    }

    public void cancel(CancellationReason reason) {
        List<java.util.function.Consumer<CancellationReason>> listeners;
        synchronized (cancellationLock) {
            if (cancellationReason != null) return;
            cancellationReason = java.util.Objects.requireNonNull(reason);
            listeners = List.copyOf(cancellationListeners);
            cancellationListeners.clear();
        }
        for (var listener : listeners) {
            try {
                listener.accept(reason);
            } catch (RuntimeException failure) {
                log.warn(
                        "Cancellation listener failed, instanceId={}",
                        instanceId,
                        com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(failure));
            }
        }
    }

    public void cleanupResources() {
        List<AutoCloseable> snapshot;
        synchronized (resourceLock) {
            snapshot = List.copyOf(resources);
            resources.clear();
        }
        for (AutoCloseable resource : snapshot.reversed()) {
            try {
                resource.close();
            } catch (Exception exception) {
                log.warn(
                        "task resource cleanup failed, instanceId={}",
                        instanceId,
                        com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            }
        }
    }

    // variables
    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public boolean containsVariable(String key) {
        return variables.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, Class<T> type) {
        Object value = variables.get(key);

        if (value == null) {
            return null;
        }

        return (T) value;
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public void putVariable(String key, Object value) {
        validateOutputName(key);
        if (value == null) {
            removeVariable(key);
            return;
        }
        variables.put(key, value);
        outputVariables.put(key, value);
    }

    public void removeVariable(String key) {
        validateOutputName(key);
        variables.remove(key);
        outputVariables.put(key, null);
    }

    public void clearVariables() {
        variables.clear();
    }

    /**
     * Drops outputs from a failed attempt and restores immutable task inputs. This method must only
     * be called after the previous runner has completed.
     */
    public void resetAttemptVariables() {
        variables.clear();
        variables.putAll(inputVariables);
        outputVariables.clear();
    }

    public Map<String, Object> getOutputVariables() {
        synchronized (outputVariables) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(outputVariables));
        }
    }

    private void validateOutputName(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("Variable name must contain 1 to 128 characters");
        }
        if (key.startsWith("ijob.")) {
            throw new IllegalArgumentException("Variable names starting with ijob. are reserved");
        }
    }

    // publishedEvents
    public Set<String> getEvents() {
        return Collections.unmodifiableSet(publishedEvents);
    }

    public boolean containsEvent(String eventCode) {
        return publishedEvents.contains(eventCode);
    }

    public boolean hasEvents() {
        return !publishedEvents.isEmpty();
    }

    public void addEvent(String eventCode) {
        publishedEvents.add(eventCode);
    }

    public void seedInboundEvent(Long eventId, String eventCode) {
        inboundEventId = eventId;
        inboundEventCode = eventCode;
        if (eventCode != null) {
            addEvent(eventCode);
        }
    }

    public void removeEvent(String eventCode) {
        publishedEvents.remove(eventCode);
    }

    public void clearEvents() {
        publishedEvents.clear();
    }
}
