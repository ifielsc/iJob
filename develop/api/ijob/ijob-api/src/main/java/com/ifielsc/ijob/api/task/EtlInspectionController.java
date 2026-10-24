// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.task;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.task.EtlInspectionDTO;
import com.ifielsc.ijob.core.etl.EtlInspection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EtlInspectionController {
    private final EtlInspection inspection;

    public EtlInspectionController(EtlInspection inspection) {
        this.inspection = inspection;
    }

    @ApiAccess(AccountPermission.SENSITIVE_READ)
    @GetMapping("/api/tasks/{id}/etl-progress")
    public ApiResponse<EtlInspectionDTO.Progress> progress(@PathVariable Long id) {
        return ApiResponse.success(inspection.progress(id));
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @GetMapping("/api/tasks/batch-etl/source-tables")
    public ApiResponse<EtlInspectionDTO.Tables> tables(
            @RequestParam Long resourceId,
            @RequestParam(defaultValue = "") String schema,
            @RequestParam(defaultValue = "false") boolean includeViews) {
        return ApiResponse.success(inspection.tables(resourceId, schema, includeViews));
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "BATCH_ETL_METADATA_CHECK",
            target = "TASK",
            fields = "metadata",
            external = true)
    @PostMapping("/api/tasks/batch-etl/check-tables")
    public ApiResponse<EtlInspectionDTO.Checks> check(
            @RequestBody EtlInspectionDTO.CheckRequest request) {
        return ApiResponse.success(inspection.check(request.pluginConfig()));
    }
}
