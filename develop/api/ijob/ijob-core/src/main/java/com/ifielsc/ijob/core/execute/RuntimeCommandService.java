package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.RuntimeCommandStatus;
import com.ifielsc.ijob.contract.enums.RuntimeCommandType;
import com.ifielsc.ijob.contract.runtime.RuntimeCommandDTO;
import com.ifielsc.ijob.infra.coordination.command.DatabaseRuntimeCommandQueue;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class RuntimeCommandService {
    private final DatabaseRuntimeCommandQueue commandQueue;
    private final ExecutionFenceCoordinator fenceCoordinator;
    private final RuntimeService runtimeService;
    private final ConcurrentMap<String, RuntimeCommandDTO> localResults = new ConcurrentHashMap<>();

    public RuntimeCommandDTO submit(
            Long instanceId, RuntimeCommandType commandType, String requestId, String requestedBy) {
        if (fenceCoordinator.isEnforced()) {
            return commandQueue.submit(requestId, instanceId, commandType, requestedBy);
        }
        return submitLocal(instanceId, commandType, requestId, requestedBy);
    }

    private synchronized RuntimeCommandDTO submitLocal(
            Long instanceId, RuntimeCommandType commandType, String requestId, String requestedBy) {
        String resolvedRequestId =
                requestId == null || requestId.isBlank()
                        ? UUID.randomUUID().toString()
                        : requestId.trim();
        RuntimeCommandDTO existing = localResults.get(resolvedRequestId);
        if (existing != null) {
            if (!instanceId.equals(existing.getInstanceId())
                    || commandType != existing.getCommandType()) {
                throw BusinessException.localized(
                        ResultCode.CONFLICT, "Idempotency key belongs to another runtime command");
            }
            return existing;
        }
        executeLocal(instanceId, commandType);
        RuntimeCommandDTO result = new RuntimeCommandDTO();
        LocalDateTime now = LocalDateTime.now();
        result.setRequestId(resolvedRequestId);
        result.setInstanceId(instanceId);
        result.setRootInstanceId(instanceId);
        result.setCommandType(commandType);
        result.setStatus(RuntimeCommandStatus.SUCCEEDED);
        result.setRequestedBy(requestedBy == null ? "unknown" : requestedBy);
        result.setResultMessage("Command executed in local runtime mode");
        result.setProcessedAt(now);
        result.setCreateTime(now);
        result.setUpdateTime(now);
        localResults.put(resolvedRequestId, result);
        return result;
    }

    public RuntimeCommandDTO get(String requestId) {
        if (fenceCoordinator.isEnforced()) {
            return commandQueue.get(requestId);
        }
        RuntimeCommandDTO result = localResults.get(requestId);
        if (result == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Runtime command not found");
        }
        return result;
    }

    private void executeLocal(Long instanceId, RuntimeCommandType commandType) {
        switch (commandType) {
            case STOP -> runtimeService.stop(instanceId);
            case PAUSE -> runtimeService.pause(instanceId);
            case RESUME -> runtimeService.resume(instanceId);
            case RETRY -> runtimeService.retry(instanceId);
        }
    }
}
