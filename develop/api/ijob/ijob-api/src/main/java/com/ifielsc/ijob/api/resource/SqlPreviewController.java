// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.api.resource;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.api.security.AuditOperation;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.resource.SqlPreviewDTO;
import com.ifielsc.ijob.core.auth.AuthService;
import com.ifielsc.ijob.core.resource.SqlPreview;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql-preview")
public class SqlPreviewController {
    private final SqlPreview previews;
    private final AuthService auth;

    public SqlPreviewController(SqlPreview previews, AuthService auth) {
        this.previews = previews;
        this.auth = auth;
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(
            action = "SQL_PREVIEW",
            target = "RESOURCE",
            fields = "resourceId",
            external = true)
    @PostMapping
    public ApiResponse<SqlPreviewDTO.Result> query(@RequestBody SqlPreviewDTO.Request request) {
        var result = previews.query(request, auth.currentUser().getLoginId());
        org.slf4j.LoggerFactory.getLogger(SqlPreviewController.class)
                .info(
                        "SQL preview resourceId={} status={} durationMs={} traceId={}",
                        request.resourceId(),
                        result.status(),
                        result.elapsedMillis(),
                        result.traceId());
        return ApiResponse.success(result);
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @PostMapping("/{requestId}/cancel")
    @AuditOperation(action = "SQL_PREVIEW_CANCEL", target = "RESOURCE", external = true)
    public ApiResponse<Void> cancel(@PathVariable String requestId) {
        previews.cancel(requestId, auth.currentUser().getLoginId());
        return ApiResponse.success();
    }
}
