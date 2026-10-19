package com.ifielsc.ijob.api.workbench;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.workbench.WorkbenchDTO;
import com.ifielsc.ijob.contract.workbench.WorkbenchQuery;
import com.ifielsc.ijob.core.auth.AuthService;
import com.ifielsc.ijob.core.workbench.WorkbenchService;

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
