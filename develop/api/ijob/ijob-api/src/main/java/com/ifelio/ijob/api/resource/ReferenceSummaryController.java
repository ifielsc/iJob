package com.ifelio.ijob.api.resource;

import com.ifelio.ijob.api.security.ApiAccess;
import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.auth.AccountPermission;
import com.ifelio.ijob.contract.resource.ReferenceSummaryDTO;
import com.ifelio.ijob.contract.resource.ReferenceSummaryRequest;
import com.ifelio.ijob.core.resource.ReferenceSummaryService;

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
