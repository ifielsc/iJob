// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Propagates cooperative stop signals through the persisted instance tree. The root terminal status
 * is owned by the caller; active descendants are persisted as STOPPED.
 */
@Service
@RequiredArgsConstructor
public class ExecutionTreeControl {
    private final ExecutionManager executionManager;
    private final InstanceService instanceService;

    public void requestStop(Long rootInstanceId) {
        request(rootInstanceId, false);
    }

    public void requestTimeout(Long rootInstanceId) {
        request(rootInstanceId, true);
    }

    public void requestAbortDescendants(Long rootInstanceId) {
        stopDescendants(rootInstanceId, new HashSet<>());
    }

    public boolean hasActiveExecutionInTree(Long rootInstanceId) {
        return hasActiveExecutionInTree(rootInstanceId, new HashSet<>());
    }

    public boolean requestPause(Long rootInstanceId) {
        ExecutionHandle rootHandle = executionManager.getHandle(rootInstanceId);
        if (rootHandle == null) {
            return false;
        }
        boolean rootHandleChanged = !rootHandle.isPauseRequested();
        executionManager.pause(rootInstanceId);
        List<Long> changedHandleIds = new ArrayList<>();
        List<Long> changedStateIds = new ArrayList<>();
        try {
            pauseDescendants(rootInstanceId, new HashSet<>(), changedHandleIds, changedStateIds);
            return true;
        } catch (RuntimeException exception) {
            rollbackPauseRequest(
                    rootInstanceId,
                    rootHandleChanged,
                    changedHandleIds,
                    changedStateIds,
                    exception);
            throw exception;
        }
    }

    public boolean requestResume(Long rootInstanceId) {
        if (!executionManager.hasHandle(rootInstanceId)) {
            return false;
        }
        List<Long> changedHandleIds = new ArrayList<>();
        List<Long> changedStateIds = new ArrayList<>();
        try {
            resumeDescendants(rootInstanceId, new HashSet<>(), changedHandleIds, changedStateIds);
            if (executionManager.resume(rootInstanceId)) {
                return true;
            }
            rollbackResumeRequest(changedHandleIds, changedStateIds, null);
            return false;
        } catch (RuntimeException exception) {
            rollbackResumeRequest(changedHandleIds, changedStateIds, exception);
            throw exception;
        }
    }

    private void request(Long rootInstanceId, boolean timeout) {
        if (timeout) {
            executionManager.timeout(rootInstanceId);
        } else {
            executionManager.stop(rootInstanceId);
        }
        stopDescendants(rootInstanceId, new HashSet<>());
    }

    private boolean hasActiveExecutionInTree(Long instanceId, Set<Long> visited) {
        if (instanceId == null || !visited.add(instanceId)) {
            return false;
        }
        if (executionManager.hasHandle(instanceId)) {
            return true;
        }
        for (Instance child : instanceService.listChildren(instanceId)) {
            if (hasActiveExecutionInTree(child.getId(), visited)) {
                return true;
            }
        }
        return false;
    }

    private void stopDescendants(Long parentId, Set<Long> visited) {
        if (parentId == null || !visited.add(parentId)) {
            return;
        }
        for (Instance child : instanceService.listChildren(parentId)) {
            Long childId = child.getId();
            executionManager.stop(childId);
            stopDescendants(childId, visited);
            if (!child.getStatus().isFinished()) {
                instanceService.stop(childId);
            }
        }
    }

    private void pauseDescendants(
            Long parentId,
            Set<Long> visited,
            List<Long> changedHandleIds,
            List<Long> changedStateIds) {
        if (parentId == null || !visited.add(parentId)) {
            return;
        }
        for (Instance child : instanceService.listChildren(parentId)) {
            Long childId = child.getId();
            ExecutionHandle handle = executionManager.getHandle(childId);
            if (handle != null && !handle.isPauseRequested()) {
                changedHandleIds.add(childId);
            }
            executionManager.pause(childId);
            if (child.getStatus() == InstanceStatus.RUNNING
                    && instanceService.pauseIfRunning(childId)) {
                changedStateIds.add(childId);
            }
            pauseDescendants(childId, visited, changedHandleIds, changedStateIds);
        }
    }

    private void resumeDescendants(
            Long parentId,
            Set<Long> visited,
            List<Long> changedHandleIds,
            List<Long> changedStateIds) {
        if (parentId == null || !visited.add(parentId)) {
            return;
        }
        for (Instance child : instanceService.listChildren(parentId)) {
            Long childId = child.getId();
            resumeDescendants(childId, visited, changedHandleIds, changedStateIds);
            ExecutionHandle handle = executionManager.getHandle(childId);
            if (handle != null && handle.isPauseRequested()) {
                changedHandleIds.add(childId);
            }
            executionManager.resume(childId);
            if (child.getStatus() == InstanceStatus.PAUSED
                    && instanceService.resumeIfPaused(childId)) {
                changedStateIds.add(childId);
            }
        }
    }

    private void rollbackPauseRequest(
            Long rootInstanceId,
            boolean rootHandleChanged,
            List<Long> changedHandleIds,
            List<Long> changedStateIds,
            RuntimeException originalException) {
        rollbackIds(changedStateIds, instanceService::resumeIfPaused, originalException);
        rollbackIds(changedHandleIds, executionManager::resume, originalException);
        if (rootHandleChanged) {
            executionManager.resume(rootInstanceId);
        }
    }

    private void rollbackResumeRequest(
            List<Long> changedHandleIds,
            List<Long> changedStateIds,
            RuntimeException originalException) {
        rollbackIds(changedStateIds, instanceService::pauseIfRunning, originalException);
        rollbackIds(changedHandleIds, executionManager::pause, originalException);
    }

    private void rollbackIds(
            List<Long> changedIds,
            java.util.function.Function<Long, Boolean> rollback,
            RuntimeException originalException) {
        for (int index = changedIds.size() - 1; index >= 0; index--) {
            try {
                rollback.apply(changedIds.get(index));
            } catch (RuntimeException rollbackException) {
                if (originalException != null) {
                    originalException.addSuppressed(rollbackException);
                }
            }
        }
    }
}
