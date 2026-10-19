package com.ifielsc.ijob.api.diagnostics;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.diagnostics.DiagnosticCategory;
import com.ifielsc.ijob.contract.diagnostics.DiagnosticsDTO;
import com.ifielsc.ijob.core.diagnostics.DiagnosticsService;

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
