// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.auth;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.auth.AuditDTO;
import com.ifielsc.ijob.contract.auth.AuditPageQuery;
import com.ifielsc.ijob.core.auth.AuditService;

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
