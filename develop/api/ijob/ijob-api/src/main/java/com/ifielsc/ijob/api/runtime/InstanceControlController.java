// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.runtime;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.enums.RuntimeCommandType;
import com.ifielsc.ijob.contract.runtime.RuntimeCommandDTO;
import com.ifielsc.ijob.core.auth.AuthSession;
import com.ifielsc.ijob.core.execute.RuntimeCommandService;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/instances/{id}")
public class InstanceControlController {
    private final RuntimeCommandService commandService;
    private final AuthSession authSession;

    public InstanceControlController(
            RuntimeCommandService commandService, AuthSession authSession) {
        this.commandService = commandService;
        this.authSession = authSession;
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.EXECUTION_CONTROL)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "INSTANCE_CONTROL_STOP",
            target = "INSTANCE_CONTROL",
            fields = "",
            accepted = true)
    @PostMapping("/stop")
    public ApiResponse<RuntimeCommandDTO> stop(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String requestId) {
        return submit(id, RuntimeCommandType.STOP, requestId);
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.EXECUTION_CONTROL)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "INSTANCE_CONTROL_PAUSE",
            target = "INSTANCE_CONTROL",
            fields = "",
            accepted = true)
    @PostMapping("/pause")
    public ApiResponse<RuntimeCommandDTO> pause(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String requestId) {
        return submit(id, RuntimeCommandType.PAUSE, requestId);
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.EXECUTION_CONTROL)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "INSTANCE_CONTROL_RESUME",
            target = "INSTANCE_CONTROL",
            fields = "",
            accepted = true)
    @PostMapping("/resume")
    public ApiResponse<RuntimeCommandDTO> resume(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String requestId) {
        return submit(id, RuntimeCommandType.RESUME, requestId);
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.EXECUTION_CONTROL)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "INSTANCE_CONTROL_RETRY",
            target = "INSTANCE_CONTROL",
            fields = "",
            accepted = true)
    @PostMapping("/retry")
    public ApiResponse<RuntimeCommandDTO> retry(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String requestId) {
        return submit(id, RuntimeCommandType.RETRY, requestId);
    }

    private ApiResponse<RuntimeCommandDTO> submit(
            Long instanceId, RuntimeCommandType commandType, String requestId) {
        return ApiResponse.success(
                commandService.submit(
                        instanceId,
                        commandType,
                        requestId,
                        authSession.currentUser().getLoginId()));
    }
}
