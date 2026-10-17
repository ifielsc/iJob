package com.ifelio.ijob.api.task;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.runtime.ManualTriggerRequest;
import com.ifelio.ijob.core.trigger.ManualTaskTriggerService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/tasks")
public class TaskExecutionController {
    private final ManualTaskTriggerService manualTaskTriggerService;

    public TaskExecutionController(ManualTaskTriggerService manualTaskTriggerService) {
        this.manualTaskTriggerService = manualTaskTriggerService;
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.EXECUTION_CONTROL)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "TASK_EXECUTION_TRIGGER",
            target = "TASK_EXECUTION",
            fields = "",
            accepted = true)
    @PostMapping("/{id}/manual-trigger")
    public ResponseEntity<ApiResponse<String>> trigger(
            @PathVariable Long id, @RequestBody(required = false) ManualTriggerRequest request) {
        Long triggerId = manualTaskTriggerService.trigger(id, request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/triggers/" + triggerId))
                .body(ApiResponse.success(triggerId.toString()));
    }
}
