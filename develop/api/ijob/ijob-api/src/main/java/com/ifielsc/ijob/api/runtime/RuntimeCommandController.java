package com.ifielsc.ijob.api.runtime;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.runtime.RuntimeCommandDTO;
import com.ifielsc.ijob.core.execute.RuntimeCommandService;

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

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.SUMMARY_READ)
    @GetMapping("/{requestId}")
    public ApiResponse<RuntimeCommandDTO> get(@PathVariable String requestId) {
        return ApiResponse.success(commandService.get(requestId));
    }
}
