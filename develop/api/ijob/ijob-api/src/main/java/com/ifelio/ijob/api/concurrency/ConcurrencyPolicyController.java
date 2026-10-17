package com.ifelio.ijob.api.concurrency;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.concurrency.ConcurrencyMetadataDTO;
import com.ifelio.ijob.contract.concurrency.ConcurrencyResourceDTO;
import com.ifelio.ijob.contract.concurrency.ConcurrencyResourceUpsertRequest;
import com.ifelio.ijob.contract.concurrency.PluginConcurrencyPolicyDTO;
import com.ifelio.ijob.contract.concurrency.PluginConcurrencyPolicyUpdateRequest;
import com.ifelio.ijob.contract.concurrency.TaskConcurrencyPolicyDTO;
import com.ifelio.ijob.contract.concurrency.TaskConcurrencyPolicyUpdateRequest;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.concurrency.ConcurrencyPolicyService;

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
@RequestMapping("/api/concurrency")
public class ConcurrencyPolicyController {
    private final ConcurrencyPolicyService service;

    public ConcurrencyPolicyController(ConcurrencyPolicyService service) {
        this.service = service;
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/metadata")
    public ApiResponse<ConcurrencyMetadataDTO> metadata() {
        return ApiResponse.success(service.metadata());
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/resources/page")
    public ApiResponse<com.ifelio.ijob.common.response.PageResult<ConcurrencyResourceDTO>>
            resourcePage(com.ifelio.ijob.contract.concurrency.ConcurrencyResourcePageQuery query) {
        return ApiResponse.success(service.queryResources(query));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/resources/by-code")
    public ApiResponse<ConcurrencyResourceDTO> resourceByCode(@RequestParam String code) {
        return ApiResponse.success(service.getResourceByCode(code));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/resources/by-ids")
    public ApiResponse<List<ConcurrencyResourceDTO>> resourcesByIds(
            @RequestParam(required = false) List<Long> ids) {
        return ApiResponse.success(service.listResourcesByIds(ids));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/resources/{id}")
    public ApiResponse<ConcurrencyResourceDTO> resource(@PathVariable Long id) {
        return ApiResponse.success(service.getResource(id));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "CONCURRENCY_POLICY_CREATE_RESOURCE",
            target = "CONCURRENCY_POLICY",
            fields = "definition")
    @PostMapping("/resources")
    public ResponseEntity<ApiResponse<String>> createResource(
            @RequestBody ConcurrencyResourceUpsertRequest request) {
        Long id = service.createResource(request);
        return ResponseEntity.created(URI.create("/api/concurrency/resources/" + id))
                .body(ApiResponse.success(id.toString()));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "CONCURRENCY_POLICY_UPDATE_RESOURCE",
            target = "CONCURRENCY_POLICY",
            fields = "definition")
    @PutMapping("/resources/{id}")
    public ApiResponse<Void> updateResource(
            @PathVariable Long id, @RequestBody ConcurrencyResourceUpsertRequest request) {
        service.updateResource(id, request);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "CONCURRENCY_POLICY_DELETE_RESOURCE",
            target = "CONCURRENCY_POLICY",
            fields = "definition")
    @DeleteMapping("/resources/{id}")
    public ApiResponse<Void> deleteResource(
            @PathVariable Long id, @RequestParam Long expectedVersion) {
        service.deleteResource(id, expectedVersion);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/plugin-policies")
    public ApiResponse<List<PluginConcurrencyPolicyDTO>> pluginPolicies() {
        return ApiResponse.success(service.listPluginPolicies());
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/plugin-policies/{type}")
    public ApiResponse<PluginConcurrencyPolicyDTO> pluginPolicy(@PathVariable PluginType type) {
        return ApiResponse.success(service.getPluginPolicy(type));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "CONCURRENCY_POLICY_PUT_PLUGIN_POLICY",
            target = "CONCURRENCY_POLICY",
            fields = "definition")
    @PutMapping("/plugin-policies/{type}")
    public ApiResponse<PluginConcurrencyPolicyDTO> putPluginPolicy(
            @PathVariable PluginType type,
            @RequestBody PluginConcurrencyPolicyUpdateRequest request) {
        return ApiResponse.success(service.putPluginPolicy(type, request));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "CONCURRENCY_POLICY_DELETE_PLUGIN_POLICY",
            target = "CONCURRENCY_POLICY",
            fields = "definition")
    @DeleteMapping("/plugin-policies/{type}")
    public ApiResponse<Void> deletePluginPolicy(
            @PathVariable PluginType type, @RequestParam Long expectedVersion) {
        service.deletePluginPolicy(type, expectedVersion);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskConcurrencyPolicyDTO> taskPolicy(@PathVariable Long taskId) {
        return ApiResponse.success(service.getTaskPolicy(taskId));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "CONCURRENCY_POLICY_PUT_TASK_POLICY",
            target = "CONCURRENCY_POLICY",
            fields = "definition")
    @PutMapping("/tasks/{taskId}")
    public ApiResponse<TaskConcurrencyPolicyDTO> putTaskPolicy(
            @PathVariable Long taskId, @RequestBody TaskConcurrencyPolicyUpdateRequest request) {
        return ApiResponse.success(service.putTaskPolicy(taskId, request));
    }
}
