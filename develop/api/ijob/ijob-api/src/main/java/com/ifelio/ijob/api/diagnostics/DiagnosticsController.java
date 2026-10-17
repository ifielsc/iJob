package com.ifelio.ijob.api.diagnostics;

import com.ifelio.ijob.api.security.ApiAccess;
import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.auth.AccountPermission;
import com.ifelio.ijob.contract.diagnostics.DiagnosticCategory;
import com.ifelio.ijob.contract.diagnostics.DiagnosticsDTO;
import com.ifelio.ijob.core.diagnostics.DiagnosticsService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DiagnosticsController {
    private final DiagnosticsService service;

    public DiagnosticsController(DiagnosticsService service) {
        this.service = service;
    }

    @ApiAccess(AccountPermission.SUMMARY_READ)
    @GetMapping("/api/diagnostics")
    public ApiResponse<DiagnosticsDTO> get(
            @RequestParam(required = false) DiagnosticCategory category,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(service.query(category, limit));
    }
}
