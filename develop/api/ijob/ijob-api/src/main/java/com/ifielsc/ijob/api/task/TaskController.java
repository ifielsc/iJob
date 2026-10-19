// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.task;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.common.BasePageQuery;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.task.TaskDetailDTO;
import com.ifielsc.ijob.contract.task.TaskListDTO;
import com.ifielsc.ijob.contract.task.TaskPageQuery;
import com.ifielsc.ijob.contract.task.TaskPublicationDTO;
import com.ifielsc.ijob.contract.task.TaskUpsertRequest;
import com.ifielsc.ijob.core.task.TaskService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping
    public ApiResponse<PageResult<TaskListDTO>> queryPage(TaskPageQuery query) {
        return ApiResponse.success(taskService.queryPage(query));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping("/{id}")
    public ApiResponse<TaskDetailDTO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(taskService.getDetail(id));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/groups")
    public ApiResponse<PageResult<String>> groups(
            BasePageQuery query, @RequestParam(defaultValue = "") String prefix) {
        return ApiResponse.success(taskService.queryGroups(query, prefix));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "TASK_CREATE",
            target = "TASK",
            fields = "definition")
    @PostMapping
    public ResponseEntity<ApiResponse<TaskPublicationDTO>> create(
            @RequestBody TaskUpsertRequest request) {
        TaskPublicationDTO publication = taskService.create(request);
        return ResponseEntity.created(URI.create("/api/tasks/" + publication.taskId()))
                .body(ApiResponse.success(publication));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "TASK_UPDATE",
            target = "TASK",
            fields = "definition")
    @PutMapping("/{id}")
    public ApiResponse<TaskPublicationDTO> update(
            @PathVariable Long id,
            @RequestParam Long expectedRevision,
            @RequestBody TaskUpsertRequest request) {
        return ApiResponse.success(taskService.update(id, expectedRevision, request));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "TASK_DELETE",
            target = "TASK",
            fields = "definition")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ApiResponse.success();
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "TASK_DELETE_BATCH",
            target = "TASK",
            fields = "definition")
    @DeleteMapping("/batch")
    public ApiResponse<Void> deleteBatch(@RequestBody BatchDeleteRequest request) {
        taskService.deleteBatch(request);
        return ApiResponse.success();
    }
}
