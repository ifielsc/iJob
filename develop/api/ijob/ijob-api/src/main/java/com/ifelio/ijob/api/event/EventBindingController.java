package com.ifelio.ijob.api.event;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.common.BatchDeleteRequest;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.event.EventBindingCreateRequest;
import com.ifelio.ijob.contract.event.EventBindingDTO;
import com.ifelio.ijob.contract.event.EventBindingUpdateRequest;
import com.ifelio.ijob.core.event.EventBindingService;

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
import java.util.List;

@RestController
@RequestMapping("/api/event-bindings")
public class EventBindingController {
    private final EventBindingService eventBindingService;

    public EventBindingController(EventBindingService eventBindingService) {
        this.eventBindingService = eventBindingService;
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping
    public ApiResponse<List<EventBindingDTO>> listDetailsByTarget(
            @RequestParam TargetType targetType, @RequestParam Long targetId) {
        return ApiResponse.success(eventBindingService.listDetailsByTarget(targetType, targetId));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping("/{id}")
    public ApiResponse<EventBindingDTO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(eventBindingService.getDetail(id));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "EVENT_BINDING_CREATE",
            target = "EVENT_BINDING",
            fields = "definition")
    @PostMapping
    public ResponseEntity<ApiResponse<String>> create(
            @RequestBody EventBindingCreateRequest request) {
        Long id = eventBindingService.create(request);
        return ResponseEntity.created(URI.create("/api/event-bindings/" + id))
                .body(ApiResponse.success(id.toString()));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "EVENT_BINDING_UPDATE",
            target = "EVENT_BINDING",
            fields = "definition")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id, @RequestBody EventBindingUpdateRequest request) {
        eventBindingService.update(id, request);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "EVENT_BINDING_DELETE",
            target = "EVENT_BINDING",
            fields = "definition")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        eventBindingService.delete(id);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "EVENT_BINDING_DELETE_BATCH",
            target = "EVENT_BINDING",
            fields = "definition")
    @DeleteMapping("/batch")
    public ApiResponse<Void> deleteBatch(@RequestBody BatchDeleteRequest request) {
        eventBindingService.deleteBatch(request);
        return ApiResponse.success();
    }
}
