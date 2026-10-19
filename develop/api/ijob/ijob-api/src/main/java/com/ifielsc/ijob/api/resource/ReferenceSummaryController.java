// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.resource;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.resource.ReferenceSummaryDTO;
import com.ifielsc.ijob.contract.resource.ReferenceSummaryRequest;
import com.ifielsc.ijob.core.resource.ReferenceSummaryService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReferenceSummaryController {
    private final ReferenceSummaryService summaries;

    public ReferenceSummaryController(ReferenceSummaryService summaries) {
        this.summaries = summaries;
    }

    @ApiAccess(AccountPermission.SENSITIVE_READ)
    @PostMapping("/api/reference-summaries")
    public ApiResponse<List<ReferenceSummaryDTO>> resolve(
            @RequestBody ReferenceSummaryRequest request) {
        return ApiResponse.success(summaries.resolve(request));
    }
}
