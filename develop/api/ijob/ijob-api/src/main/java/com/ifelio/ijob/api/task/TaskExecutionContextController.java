package com.ifelio.ijob.api.task;

import com.ifelio.ijob.api.security.ApiAccess;
import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.auth.AccountPermission;
import com.ifelio.ijob.contract.task.TaskExecutionContextDTO;
import com.ifelio.ijob.core.task.TaskExecutionContextService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskExecutionContextController {
    private final TaskExecutionContextService service;

    public TaskExecutionContextController(TaskExecutionContextService service) {
        this.service = service;
    }

    @ApiAccess(AccountPermission.DEFINITION_READ)
    @GetMapping("/api/tasks/{id}/execution-context")
    public ApiResponse<TaskExecutionContextDTO> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }
}
