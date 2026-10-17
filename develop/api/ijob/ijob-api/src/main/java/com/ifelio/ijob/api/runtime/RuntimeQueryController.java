package com.ifelio.ijob.api.runtime;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.runtime.InstanceDTO;
import com.ifelio.ijob.contract.runtime.InstanceLogDTO;
import com.ifelio.ijob.contract.runtime.InstanceLogSnapshotDTO;
import com.ifelio.ijob.contract.runtime.InstancePageQuery;
import com.ifelio.ijob.contract.runtime.RunTreeDTO;
import com.ifelio.ijob.contract.runtime.TriggerDTO;
import com.ifelio.ijob.contract.runtime.TriggerPageQuery;
import com.ifelio.ijob.core.instance.RuntimeQueryService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RuntimeQueryController {
    private final RuntimeQueryService runtimeQueryService;
    private final RuntimeMessageLocalizer localizer;
    private final com.ifelio.ijob.core.instance.RunGraphQueryService graphs;

    public RuntimeQueryController(
            RuntimeQueryService runtimeQueryService,
            RuntimeMessageLocalizer localizer,
            com.ifelio.ijob.core.instance.RunGraphQueryService graphs) {
        this.runtimeQueryService = runtimeQueryService;
        this.localizer = localizer;
        this.graphs = graphs;
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SUMMARY_READ)
    @GetMapping("/api/triggers/{id}")
    public ApiResponse<TriggerDTO> trigger(@PathVariable Long id) {
        return ApiResponse.success(localizer.localize(runtimeQueryService.getTrigger(id)));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SUMMARY_READ)
    @GetMapping("/api/triggers")
    public ApiResponse<PageResult<TriggerDTO>> triggers(TriggerPageQuery query) {
        PageResult<TriggerDTO> page = runtimeQueryService.queryTriggers(query);
        if (page.getList() != null) {
            page.getList().forEach(localizer::localize);
        }
        return ApiResponse.success(page);
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SUMMARY_READ)
    @GetMapping("/api/instances")
    public ApiResponse<PageResult<InstanceDTO>> instances(InstancePageQuery query) {
        PageResult<InstanceDTO> page = runtimeQueryService.queryInstances(query);
        if (page.getList() != null) {
            page.getList().forEach(localizer::localize);
        }
        return ApiResponse.success(page);
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SUMMARY_READ)
    @GetMapping("/api/instances/{id}")
    public ApiResponse<InstanceDTO> instance(@PathVariable Long id) {
        return ApiResponse.success(localizer.localize(runtimeQueryService.getInstance(id)));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SUMMARY_READ)
    @GetMapping("/api/instances/{id}/tree")
    public ApiResponse<RunTreeDTO> tree(
            @PathVariable Long id, @RequestParam(defaultValue = "false") boolean includeVariables) {
        RunTreeDTO tree = runtimeQueryService.getRunTree(id, includeVariables);
        if (tree.getNodes() != null) {
            tree.getNodes().forEach(localizer::localize);
        }
        return ApiResponse.success(tree);
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SUMMARY_READ)
    @GetMapping("/api/instances/{id}/graph")
    public ApiResponse<com.ifelio.ijob.contract.runtime.RunGraphDTO> graph(@PathVariable Long id) {
        return ApiResponse.success(graphs.get(id));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    public ApiResponse<List<InstanceLogDTO>> logs(Long id, Long afterId, Integer limit) {
        return ApiResponse.success(runtimeQueryService.listLogs(id, afterId, limit));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping("/api/instances/{id}/logs")
    public ApiResponse<List<InstanceLogDTO>> logs(
            @PathVariable Long id,
            @RequestParam(required = false) Long afterId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "self") String scope) {
        return ApiResponse.success(runtimeQueryService.listLogs(id, afterId, limit, scope));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping("/api/instances/{id}/logs/snapshot")
    public ApiResponse<InstanceLogSnapshotDTO> logSnapshot(
            @PathVariable Long id,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "self") String scope) {
        return ApiResponse.success(runtimeQueryService.getLogSnapshot(id, limit, scope));
    }
}
