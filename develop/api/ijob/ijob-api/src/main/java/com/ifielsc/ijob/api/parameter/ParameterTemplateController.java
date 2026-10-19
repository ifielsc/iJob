package com.ifielsc.ijob.api.parameter;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.api.security.AuditOperation;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.parameter.ParameterForm;
import com.ifielsc.ijob.contract.parameter.ParameterTemplateDTO;
import com.ifielsc.ijob.contract.parameter.ParameterTemplateQuery;
import com.ifielsc.ijob.contract.parameter.ParameterTemplateRequest;
import com.ifielsc.ijob.core.parameter.ParameterTemplateService;

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
@RequestMapping("/api/parameter-templates")
public class ParameterTemplateController {
    private final ParameterTemplateService service;

    public ParameterTemplateController(ParameterTemplateService service) {
        this.service = service;
    }

    @ApiAccess(AccountPermission.DEFINITION_READ)
    @GetMapping
    public ApiResponse<PageResult<ParameterTemplateDTO>> queryPage(ParameterTemplateQuery query) {
        return ApiResponse.success(service.queryPage(query));
    }

    @ApiAccess(AccountPermission.DEFINITION_READ)
    @GetMapping("/{id}")
    public ApiResponse<ParameterTemplateDTO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(service.getDetail(id));
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(
            action = "PARAMETER_TEMPLATE_CREATE",
            target = "PARAMETER_TEMPLATE",
            fields = "definition")
    @PostMapping
    public ResponseEntity<ApiResponse<String>> create(
            @RequestBody ParameterTemplateRequest request) {
        Long id = service.create(request);
        return ResponseEntity.created(URI.create("/api/parameter-templates/" + id))
                .body(ApiResponse.success(id.toString()));
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(
            action = "PARAMETER_TEMPLATE_UPDATE",
            target = "PARAMETER_TEMPLATE",
            fields = "definition")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id, @RequestBody ParameterTemplateRequest request) {
        service.update(id, request);
        return ApiResponse.success();
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(
            action = "PARAMETER_TEMPLATE_DELETE",
            target = "PARAMETER_TEMPLATE",
            fields = "definition")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(
            action = "PARAMETER_TEMPLATE_DELETE_BATCH",
            target = "PARAMETER_TEMPLATE",
            fields = "definition")
    @DeleteMapping("/batch")
    public ApiResponse<Void> deleteBatch(@RequestBody BatchDeleteRequest request) {
        service.deleteBatch(request);
        return ApiResponse.success();
    }

    @ApiAccess(AccountPermission.DEFINITION_READ)
    @GetMapping("/bindings/{type}/{targetId}")
    public ApiResponse<ParameterTemplateDTO> getBinding(
            @PathVariable String type, @PathVariable Long targetId) {
        return ApiResponse.success(service.getBinding(type, targetId));
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(
            action = "PARAMETER_TEMPLATE_BIND",
            target = "PARAMETER_BINDING",
            fields = "binding")
    @PutMapping("/bindings/{type}/{targetId}")
    public ApiResponse<Void> bind(
            @PathVariable String type,
            @PathVariable Long targetId,
            @RequestBody ParameterForm request) {
        service.bind(type, targetId, request.getTemplateId());
        return ApiResponse.success();
    }
}
