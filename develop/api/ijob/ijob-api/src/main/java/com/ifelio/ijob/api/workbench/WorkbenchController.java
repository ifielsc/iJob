package com.ifelio.ijob.api.workbench;

import com.ifelio.ijob.api.security.ApiAccess;
import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.auth.AccountPermission;
import com.ifelio.ijob.contract.workbench.WorkbenchDTO;
import com.ifelio.ijob.contract.workbench.WorkbenchQuery;
import com.ifelio.ijob.core.auth.AuthService;
import com.ifelio.ijob.core.workbench.WorkbenchService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkbenchController {
    private final WorkbenchService service;
    private final AuthService authService;

    public WorkbenchController(WorkbenchService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @ApiAccess(AccountPermission.SUMMARY_READ)
    @GetMapping("/api/workbench")
    public ApiResponse<WorkbenchDTO> get(WorkbenchQuery query) {
        return ApiResponse.success(service.query(query, authService.currentUser()));
    }
}
