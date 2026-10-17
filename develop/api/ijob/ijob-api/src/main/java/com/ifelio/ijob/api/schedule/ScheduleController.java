package com.ifelio.ijob.api.schedule;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.common.BatchDeleteRequest;
import com.ifelio.ijob.contract.schedule.ScheduleDTO;
import com.ifelio.ijob.contract.schedule.ScheduleEnabledRequest;
import com.ifelio.ijob.contract.schedule.ScheduleMetadataDTO;
import com.ifelio.ijob.contract.schedule.SchedulePageQuery;
import com.ifelio.ijob.contract.schedule.SchedulePreviewDTO;
import com.ifelio.ijob.contract.schedule.SchedulePreviewRequest;
import com.ifelio.ijob.contract.schedule.ScheduleUpsertRequest;
import com.ifelio.ijob.core.schedule.ScheduleService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {
    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/metadata")
    public ApiResponse<ScheduleMetadataDTO> metadata() {
        return ApiResponse.success(scheduleService.metadata());
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping
    public ApiResponse<PageResult<ScheduleDTO>> queryPage(SchedulePageQuery query) {
        return ApiResponse.success(scheduleService.queryPage(query));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/{id}")
    public ApiResponse<ScheduleDTO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(scheduleService.getDetail(id));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "SCHEDULE_CREATE",
            target = "SCHEDULE",
            fields = "definition")
    @PostMapping
    public ResponseEntity<ApiResponse<String>> create(@RequestBody ScheduleUpsertRequest request) {
        Long id = scheduleService.create(request);
        return ResponseEntity.created(URI.create("/api/schedules/" + id))
                .body(ApiResponse.success(id.toString()));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "SCHEDULE_UPDATE",
            target = "SCHEDULE",
            fields = "definition")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id, @RequestBody ScheduleUpsertRequest request) {
        scheduleService.update(id, request);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "SCHEDULE_UPDATE_ENABLED",
            target = "SCHEDULE",
            fields = "definition")
    @PatchMapping("/{id}/enabled")
    public ApiResponse<Void> updateEnabled(
            @PathVariable Long id, @RequestBody ScheduleEnabledRequest request) {
        scheduleService.updateEnabled(id, request);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "SCHEDULE_DELETE",
            target = "SCHEDULE",
            fields = "definition")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "SCHEDULE_DELETE_BATCH",
            target = "SCHEDULE",
            fields = "definition")
    @DeleteMapping("/batch")
    public ApiResponse<Void> deleteBatch(@RequestBody BatchDeleteRequest request) {
        scheduleService.deleteBatch(request);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @PostMapping("/preview")
    public ApiResponse<SchedulePreviewDTO> preview(@RequestBody SchedulePreviewRequest request) {
        return ApiResponse.success(scheduleService.preview(request));
    }
}
