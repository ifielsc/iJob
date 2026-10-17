package com.ifelio.ijob.api.runtime;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.runtime.RuntimeCommandDTO;
import com.ifelio.ijob.core.execute.RuntimeCommandService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime-commands")
public class RuntimeCommandController {
    private final RuntimeCommandService commandService;

    public RuntimeCommandController(RuntimeCommandService commandService) {
        this.commandService = commandService;
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SUMMARY_READ)
    @GetMapping("/{requestId}")
    public ApiResponse<RuntimeCommandDTO> get(@PathVariable String requestId) {
        return ApiResponse.success(commandService.get(requestId));
    }
}
