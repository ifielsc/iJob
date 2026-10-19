package com.ifielsc.ijob.api.resource;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.resource.ResourceConnectionTestDTO;
import com.ifielsc.ijob.contract.resource.ResourceDTO;
import com.ifielsc.ijob.contract.resource.ResourceListDTO;
import com.ifielsc.ijob.contract.resource.ResourcePageQuery;
import com.ifielsc.ijob.contract.resource.ResourceUpsertRequest;
import com.ifielsc.ijob.core.resource.ResourceRuntimeService;
import com.ifielsc.ijob.core.resource.ResourceService;

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
@RequestMapping("/api/resources")
public class ResourceController {
    private final ResourceService resourceService;
    private final ResourceRuntimeService runtimeService;

    public ResourceController(
            ResourceService resourceService, ResourceRuntimeService runtimeService) {
        this.resourceService = resourceService;
        this.runtimeService = runtimeService;
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping
    public ApiResponse<PageResult<ResourceListDTO>> queryPage(ResourcePageQuery query) {
        return ApiResponse.success(resourceService.queryPage(query));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping("/{id}")
    public ApiResponse<ResourceDTO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(resourceService.getDetail(id));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "RESOURCE_TEST_CONNECTION",
            target = "RESOURCE",
            fields = "definition",
            external = true)
    @PostMapping("/{id}/connection-test")
    public ApiResponse<ResourceConnectionTestDTO> testConnection(@PathVariable Long id) {
        return ApiResponse.success(runtimeService.testConnection(id));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "RESOURCE_CREATE",
            target = "RESOURCE",
            fields = "definition")
    @PostMapping
    public ResponseEntity<ApiResponse<String>> create(@RequestBody ResourceUpsertRequest request) {
        Long id = resourceService.create(request);
        return ResponseEntity.created(URI.create("/api/resources/" + id))
                .body(ApiResponse.success(id.toString()));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "RESOURCE_UPDATE",
            target = "RESOURCE",
            fields = "definition")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id, @RequestBody ResourceUpsertRequest request) {
        resourceService.update(id, request);
        return ApiResponse.success();
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "RESOURCE_DELETE",
            target = "RESOURCE",
            fields = "definition")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        resourceService.delete(id);
        return ApiResponse.success();
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "RESOURCE_DELETE_BATCH",
            target = "RESOURCE",
            fields = "definition")
    @DeleteMapping("/batch")
    public ApiResponse<Void> deleteBatch(@RequestBody BatchDeleteRequest request) {
        resourceService.deleteBatch(request);
        return ApiResponse.success();
    }
}
