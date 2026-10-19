package com.ifielsc.ijob.api.notify;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.notify.NotifyBindingCreateRequest;
import com.ifielsc.ijob.contract.notify.NotifyBindingDTO;
import com.ifielsc.ijob.contract.notify.NotifyBindingUpdateRequest;
import com.ifielsc.ijob.core.notify.NotifyBindingService;

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
@RequestMapping("/api/notify-bindings")
public class NotifyBindingController {
    private final NotifyBindingService notifyBindingService;

    public NotifyBindingController(NotifyBindingService notifyBindingService) {
        this.notifyBindingService = notifyBindingService;
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping
    public ApiResponse<List<NotifyBindingDTO>> listDetailsByTarget(
            @RequestParam TargetType targetType, @RequestParam Long targetId) {
        return ApiResponse.success(notifyBindingService.listDetailsByTarget(targetType, targetId));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping("/{id}")
    public ApiResponse<NotifyBindingDTO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(notifyBindingService.getDetail(id));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "NOTIFY_BINDING_CREATE",
            target = "NOTIFY_BINDING",
            fields = "definition")
    @PostMapping
    public ResponseEntity<ApiResponse<String>> create(
            @RequestBody NotifyBindingCreateRequest request) {
        Long id = notifyBindingService.create(request);
        return ResponseEntity.created(URI.create("/api/notify-bindings/" + id))
                .body(ApiResponse.success(id.toString()));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "NOTIFY_BINDING_UPDATE",
            target = "NOTIFY_BINDING",
            fields = "definition")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id, @RequestBody NotifyBindingUpdateRequest request) {
        notifyBindingService.update(id, request);
        return ApiResponse.success();
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "NOTIFY_BINDING_DELETE",
            target = "NOTIFY_BINDING",
            fields = "definition")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        notifyBindingService.delete(id);
        return ApiResponse.success();
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "NOTIFY_BINDING_DELETE_BATCH",
            target = "NOTIFY_BINDING",
            fields = "definition")
    @DeleteMapping("/batch")
    public ApiResponse<Void> deleteBatch(@RequestBody BatchDeleteRequest request) {
        notifyBindingService.deleteBatch(request);
        return ApiResponse.success();
    }
}
