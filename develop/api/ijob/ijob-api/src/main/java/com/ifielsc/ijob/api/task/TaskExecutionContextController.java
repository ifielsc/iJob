package com.ifielsc.ijob.api.task;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.task.TaskExecutionContextDTO;
import com.ifielsc.ijob.core.task.TaskExecutionContextService;

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
