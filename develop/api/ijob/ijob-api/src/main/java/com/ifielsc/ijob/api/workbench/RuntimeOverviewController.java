// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.workbench;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.workbench.RuntimeOverviewDTO;
import com.ifielsc.ijob.core.workbench.RuntimeOverviewService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuntimeOverviewController {
    private final RuntimeOverviewService service;

    public RuntimeOverviewController(RuntimeOverviewService service) {
        this.service = service;
    }

    @ApiAccess(AccountPermission.SUMMARY_READ)
    @GetMapping("/api/workbench/runtime")
    public ApiResponse<RuntimeOverviewDTO> get() {
        return ApiResponse.success(service.query());
    }
}
