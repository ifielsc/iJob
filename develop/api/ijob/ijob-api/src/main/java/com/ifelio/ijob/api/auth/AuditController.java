package com.ifelio.ijob.api.auth;

import com.ifelio.ijob.api.security.ApiAccess;
import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.auth.AccountPermission;
import com.ifelio.ijob.contract.auth.AuditDTO;
import com.ifelio.ijob.contract.auth.AuditPageQuery;
import com.ifelio.ijob.core.auth.AuditService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @ApiAccess(AccountPermission.AUDIT_READ)
    @GetMapping("/api/audits")
    public ApiResponse<PageResult<AuditDTO>> query(AuditPageQuery query) {
        return ApiResponse.success(audit.query(query));
    }

    @ApiAccess(AccountPermission.AUDIT_READ)
    @GetMapping("/api/audits/{id}")
    public ApiResponse<AuditDTO> get(@PathVariable Long id) {
        return ApiResponse.success(audit.get(id));
    }
}
