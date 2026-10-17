package com.ifelio.ijob.api.notify;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.common.BatchDeleteRequest;
import com.ifelio.ijob.contract.notify.NotifyConfigDTO;
import com.ifelio.ijob.contract.notify.NotifyConfigListDTO;
import com.ifelio.ijob.contract.notify.NotifyConfigPageQuery;
import com.ifelio.ijob.contract.notify.NotifyConfigUpsertRequest;
import com.ifelio.ijob.contract.notify.NotifyTestSendDTO;
import com.ifelio.ijob.contract.notify.NotifyTestSendRequest;
import com.ifelio.ijob.core.auth.AuthSession;
import com.ifelio.ijob.core.notify.NotifyConfigService;
import com.ifelio.ijob.core.notify.NotifyTestSendService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/notify-configs")
public class NotifyConfigController {
    private final NotifyConfigService notifyConfigService;
    private final NotifyTestSendService notifyTestSendService;
    private final AuthSession authSession;

    public NotifyConfigController(
            NotifyConfigService notifyConfigService,
            NotifyTestSendService notifyTestSendService,
            AuthSession authSession) {
        this.notifyConfigService = notifyConfigService;
        this.notifyTestSendService = notifyTestSendService;
        this.authSession = authSession;
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping
    public ApiResponse<PageResult<NotifyConfigListDTO>> queryPage(NotifyConfigPageQuery query) {
        return ApiResponse.success(notifyConfigService.queryPage(query));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping("/{id}")
    public ApiResponse<NotifyConfigDTO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(notifyConfigService.getDetail(id));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "NOTIFY_CONFIG_CREATE",
            target = "NOTIFY_CONFIG",
            fields = "definition")
    @PostMapping
    public ResponseEntity<ApiResponse<String>> create(
            @RequestBody NotifyConfigUpsertRequest request) {
        Long id = notifyConfigService.create(request);
        return ResponseEntity.created(URI.create("/api/notify-configs/" + id))
                .body(ApiResponse.success(id.toString()));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "NOTIFY_CONFIG_UPDATE",
            target = "NOTIFY_CONFIG",
            fields = "definition")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id, @RequestBody NotifyConfigUpsertRequest request) {
        notifyConfigService.update(id, request);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "NOTIFY_CONFIG_DELETE",
            target = "NOTIFY_CONFIG",
            fields = "definition")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        notifyConfigService.delete(id);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "NOTIFY_CONFIG_DELETE_BATCH",
            target = "NOTIFY_CONFIG",
            fields = "definition")
    @DeleteMapping("/batch")
    public ApiResponse<Void> deleteBatch(@RequestBody BatchDeleteRequest request) {
        notifyConfigService.deleteBatch(request);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "NOTIFY_CONFIG_TEST_SEND",
            target = "NOTIFY_CONFIG",
            fields = "definition",
            external = true)
    @PostMapping("/{id}/test-send")
    public ApiResponse<NotifyTestSendDTO> testSend(
            @PathVariable Long id, @RequestBody NotifyTestSendRequest request) {
        return ApiResponse.success(
                notifyTestSendService.testSend(
                        id, request, authSession.currentUser().getLoginId()));
    }
}
