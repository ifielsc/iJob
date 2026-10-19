// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.job;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.common.BasePageQuery;
import com.ifielsc.ijob.contract.enums.DagConditionOperator;
import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.contract.job.JobDetailResponse;
import com.ifielsc.ijob.contract.job.JobExecutionContextDTO;
import com.ifielsc.ijob.contract.job.JobMetadataDTO;
import com.ifielsc.ijob.contract.job.JobValidationDTO;
import com.ifielsc.ijob.contract.job.JobValidationRequest;
import com.ifielsc.ijob.contract.job.JobVersionDTO;
import com.ifielsc.ijob.core.execute.condition.DagConditionValidator;
import com.ifielsc.ijob.core.execute.graph.JobGraphDefinitionValidator;
import com.ifielsc.ijob.core.job.JobDesignService;
import com.ifielsc.ijob.core.job.definition.JobDefinitionValidator;
import com.ifielsc.ijob.core.job.definition.JobNestingPolicy;

import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobDesignController {
    private final JobDesignService service;
    private final MessageSource messages;

    public JobDesignController(JobDesignService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @ApiAccess(AccountPermission.DEFINITION_READ)
    @GetMapping("/metadata")
    public ApiResponse<JobMetadataDTO> metadata() {
        return ApiResponse.success(
                new JobMetadataDTO(
                        JobGraphDefinitionValidator.MAX_NODE_COUNT,
                        JobGraphDefinitionValidator.MAX_EDGE_COUNT,
                        JobNestingPolicy.MAX_DEPTH,
                        DagConditionValidator.MAX_DEPTH,
                        DagConditionValidator.MAX_NODE_COUNT,
                        DagConditionValidator.MAX_PAYLOAD_BYTES,
                        DagConditionValidator.MAX_IN_VALUES,
                        JobGraphDefinitionValidator.MAX_ROUTE_PRIORITY,
                        JobDefinitionValidator.DEFAULT_ENABLED,
                        List.of("EXCLUSIVE"),
                        Arrays.stream(ExecuteCondition.values()).map(Enum::name).toList(),
                        Arrays.stream(FailStrategy.values()).map(Enum::name).toList(),
                        Arrays.stream(DagConditionOperator.values()).map(Enum::name).toList()));
    }

    @ApiAccess(AccountPermission.DEFINITION_READ)
    @GetMapping("/groups")
    public ApiResponse<PageResult<String>> groups(
            BasePageQuery query, @RequestParam(required = false) String prefix) {
        return ApiResponse.success(service.groups(query, prefix));
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @PostMapping("/validate")
    public ApiResponse<JobValidationDTO> validate(@RequestBody JobValidationRequest request) {
        var result = service.validate(request);
        var issues =
                result.issues().stream()
                        .map(
                                i ->
                                        new JobValidationDTO.Issue(
                                                i.path(),
                                                i.errorCode(),
                                                i.messageKey(),
                                                i.messageArgs(),
                                                messages.getMessage(
                                                        i.messageKey(),
                                                        i.messageArgs(),
                                                        "Validation failed",
                                                        org.springframework.context.i18n
                                                                .LocaleContextHolder.getLocale())))
                        .toList();
        return ApiResponse.success(
                new JobValidationDTO(result.valid(), issues, result.references()));
    }

    @ApiAccess(AccountPermission.DEFINITION_READ)
    @GetMapping("/{id}/versions")
    public ApiResponse<PageResult<JobVersionDTO>> versions(
            @PathVariable Long id, BasePageQuery query) {
        return ApiResponse.success(service.versions(id, query));
    }

    @ApiAccess(AccountPermission.SENSITIVE_READ)
    @GetMapping("/{id}/versions/{definitionId}")
    public ApiResponse<JobDetailResponse> version(
            @PathVariable Long id, @PathVariable Long definitionId) {
        return ApiResponse.success(service.detail(id, definitionId));
    }

    @ApiAccess(AccountPermission.DEFINITION_READ)
    @GetMapping("/{id}/execution-context")
    public ApiResponse<JobExecutionContextDTO> context(@PathVariable Long id) {
        return ApiResponse.success(service.context(id));
    }
}
