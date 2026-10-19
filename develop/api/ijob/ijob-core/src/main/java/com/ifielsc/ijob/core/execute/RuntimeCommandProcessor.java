// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeRole;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.observability.CoordinationObserver;
import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.RuntimeCommandType;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.infra.coordination.command.DatabaseRuntimeCommandQueue;
import com.ifielsc.ijob.infra.coordination.command.RuntimeCommandCandidate;
import com.ifielsc.ijob.infra.coordination.command.RuntimeCommandClaim;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifielsc.ijob.infra.coordination.work.ExecutionOwnershipLostException;
import com.ifielsc.ijob.infra.coordination.work.TriggerWorkClaim;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class RuntimeCommandProcessor {
    private final DatabaseRuntimeCommandQueue commandQueue;
    private final ExecutionFenceCoordinator fenceCoordinator;
    private final RuntimeService runtimeService;
    private final InstanceService instanceService;
    private final ExecutionManager executionManager;
    private final ApplicationRuntimeState runtimeState;
    private final CoordinationObserver coordinationObserver;
    private final AppProperties appProperties;

    RuntimeCommandProcessor(
            DatabaseRuntimeCommandQueue commandQueue,
            ExecutionFenceCoordinator fenceCoordinator,
            RuntimeService runtimeService,
            InstanceService instanceService,
            ExecutionManager executionManager,
            ApplicationRuntimeState runtimeState) {
        this(
                commandQueue,
                fenceCoordinator,
                runtimeService,
                instanceService,
                executionManager,
                runtimeState,
                new AppProperties(),
                Optional.empty());
    }

    @Autowired
    public RuntimeCommandProcessor(
            DatabaseRuntimeCommandQueue commandQueue,
            ExecutionFenceCoordinator fenceCoordinator,
            RuntimeService runtimeService,
            InstanceService instanceService,
            ExecutionManager executionManager,
            ApplicationRuntimeState runtimeState,
            AppProperties appProperties,
            Optional<CoordinationObserver> coordinationObserver) {
        this.commandQueue = commandQueue;
        this.fenceCoordinator = fenceCoordinator;
        this.runtimeService = runtimeService;
        this.instanceService = instanceService;
        this.executionManager = executionManager;
        this.runtimeState = runtimeState;
        this.appProperties = appProperties;
        this.coordinationObserver = coordinationObserver.orElse(CoordinationObserver.NOOP);
    }

    @Scheduled(fixedDelayString = "${app.runtime.command.scan-interval:250ms}")
    public void scan() {
        if (!fenceCoordinator.isEnforced()
                || !runtimeState.isReady()
                || !appProperties.getRuntime().getRoles().contains(RuntimeRole.WORKER)) {
            return;
        }
        List<TriggerWorkClaim> activeClaims = fenceCoordinator.activeClaims();
        if (activeClaims.isEmpty()) {
            return;
        }
        String leaseOwner = activeClaims.get(0).leaseOwner();
        for (RuntimeCommandCandidate candidate : commandQueue.listDispatchableOwned(leaseOwner)) {
            TriggerWorkClaim claim = fenceCoordinator.activeClaim(candidate.getTriggerId());
            if (claim == null
                    || claim.fenceToken() != candidate.getFenceToken()
                    || !claim.leaseOwner().equals(leaseOwner)) {
                continue;
            }
            RuntimeCommandClaim command = commandQueue.claim(candidate.getRequestId(), claim);
            if (command != null) {
                execute(command, claim);
            }
        }
    }

    public void process(TriggerWorkClaim workClaim) {
        if (workClaim == null) {
            return;
        }
        for (String requestId : commandQueue.listDispatchable(workClaim)) {
            RuntimeCommandClaim command = commandQueue.claim(requestId, workClaim);
            if (command != null) {
                execute(command, workClaim);
            }
        }
    }

    private void execute(RuntimeCommandClaim command, TriggerWorkClaim workClaim) {
        java.util.Map<String, String> correlation = new java.util.HashMap<>();
        correlation.put("traceId", "trigger-" + workClaim.triggerId());
        correlation.put("triggerId", String.valueOf(workClaim.triggerId()));
        correlation.put("rootInstanceId", String.valueOf(command.rootInstanceId()));
        correlation.put("instanceId", String.valueOf(command.instanceId()));
        correlation.put("commandId", command.requestId());
        correlation.put("fence", String.valueOf(workClaim.fenceToken()));
        try (var ignored = new com.ifielsc.ijob.common.diagnostic.DiagnosticContext(correlation)) {
            if (command.commandType() == RuntimeCommandType.RETRY) {
                instanceService.adoptRootForRetry(command.rootInstanceId(), workClaim);
                runtimeService.retryOwned(command.instanceId());
            } else {
                boolean adopted = adoptIfNecessary(command.rootInstanceId(), workClaim);
                if (adopted
                        && command.commandType() == RuntimeCommandType.RESUME
                        && status(command.instanceId()) == InstanceStatus.WAITING) {
                    log.info(
                            "resume command satisfied by owner takeover, requestId={}",
                            command.requestId());
                } else {
                    executeControl(command);
                }
            }
            if (!commandQueue.succeed(command, "Command applied by root execution owner")) {
                coordinationObserver.recordEvent("command", "fence-rejected");
                log.warn(
                        "runtime command completion lost ownership, requestId={}",
                        command.requestId());
            }
        } catch (ExecutionOwnershipLostException exception) {
            coordinationObserver.recordEvent("command", "fence-rejected");
            log.warn("runtime command owner fence was lost, requestId={}", command.requestId());
        } catch (BusinessException exception) {
            if (shouldDefer(command)) {
                commandQueue.defer(command, exception.getMessage());
            } else {
                commandQueue.reject(command, exception.getMessage());
            }
        } catch (RuntimeException exception) {
            commandQueue.fail(command, exception.getMessage());
            log.error(
                    "runtime command execution failed, requestId={}",
                    command.requestId(),
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
        }
    }

    private void executeControl(RuntimeCommandClaim command) {
        switch (command.commandType()) {
            case STOP -> runtimeService.stop(command.instanceId());
            case PAUSE -> runtimeService.pause(command.instanceId());
            case RESUME -> runtimeService.resume(command.instanceId());
            case RETRY -> throw new IllegalStateException("Retry must use owned execution path");
        }
    }

    private boolean shouldDefer(RuntimeCommandClaim command) {
        if (command.commandType() != RuntimeCommandType.PAUSE
                && command.commandType() != RuntimeCommandType.RESUME) {
            return false;
        }
        Instance instance = instanceService.getInstance(command.instanceId());
        if (instance == null || executionManager.hasHandle(command.instanceId())) {
            return false;
        }
        return command.commandType() == RuntimeCommandType.PAUSE
                ? instance.getStatus() == InstanceStatus.WAITING
                        || instance.getStatus() == InstanceStatus.RUNNING
                : instance.getStatus() == InstanceStatus.PAUSED;
    }

    private boolean adoptIfNecessary(Long rootInstanceId, TriggerWorkClaim workClaim) {
        Instance root = instanceService.getInstance(rootInstanceId);
        if (root == null
                || root.getExecutionFence() == null
                || root.getExecutionFence() == workClaim.fenceToken()) {
            return false;
        }
        instanceService.adoptRoot(rootInstanceId, workClaim);
        coordinationObserver.recordEvent("command", "takeover");
        return true;
    }

    private InstanceStatus status(Long instanceId) {
        Instance instance = instanceService.getInstance(instanceId);
        return instance == null ? null : instance.getStatus();
    }
}
