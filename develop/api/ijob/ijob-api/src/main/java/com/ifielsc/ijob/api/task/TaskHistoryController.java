// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.task;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.common.BasePageQuery;
import com.ifielsc.ijob.contract.task.TaskDetailDTO;
import com.ifielsc.ijob.contract.task.TaskVersionDTO;
import com.ifielsc.ijob.core.task.TaskHistoryService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskHistoryController {
    private final TaskHistoryService service;

    public TaskHistoryController(TaskHistoryService service) {
        this.service = service;
    }

    @ApiAccess(AccountPermission.DEFINITION_READ)
    @GetMapping("/{id}/versions")
    public ApiResponse<PageResult<TaskVersionDTO>> versions(
            @PathVariable Long id, BasePageQuery query) {
        return ApiResponse.success(service.versions(id, query));
    }

    @ApiAccess(AccountPermission.SENSITIVE_READ)
    @GetMapping("/{id}/versions/{definitionId}")
    public ApiResponse<TaskDetailDTO> version(
            @PathVariable Long id, @PathVariable Long definitionId) {
        return ApiResponse.success(service.detail(id, definitionId));
    }
}
