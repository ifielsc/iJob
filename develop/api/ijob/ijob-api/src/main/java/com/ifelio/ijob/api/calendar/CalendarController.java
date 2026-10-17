package com.ifelio.ijob.api.calendar;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.calendar.CalendarDetailDTO;
import com.ifelio.ijob.contract.calendar.CalendarListDTO;
import com.ifelio.ijob.contract.calendar.CalendarPageQuery;
import com.ifelio.ijob.contract.calendar.CalendarPreviewDayDTO;
import com.ifelio.ijob.contract.calendar.CalendarUpsertRequest;
import com.ifelio.ijob.contract.common.BatchDeleteRequest;
import com.ifelio.ijob.core.calendar.CalendarService;

import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/calendars")
public class CalendarController {
    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping
    public ApiResponse<PageResult<CalendarListDTO>> queryPage(CalendarPageQuery query) {
        return ApiResponse.success(calendarService.queryPage(query));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/context")
    public ApiResponse<com.ifelio.ijob.contract.calendar.CalendarContextDTO> context() {
        return ApiResponse.success(calendarService.context());
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/{id}")
    public ApiResponse<CalendarDetailDTO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(calendarService.getDetail(id));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.DEFINITION_READ)
    @GetMapping("/{id}/preview")
    public ApiResponse<List<CalendarPreviewDayDTO>> preview(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) Integer days) {
        return ApiResponse.success(calendarService.preview(id, from, days));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "CALENDAR_CREATE",
            target = "CALENDAR",
            fields = "definition")
    @PostMapping
    public ResponseEntity<ApiResponse<String>> create(@RequestBody CalendarUpsertRequest request) {
        Long id = calendarService.create(request);
        return ResponseEntity.created(URI.create("/api/calendars/" + id))
                .body(ApiResponse.success(id.toString()));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "CALENDAR_UPDATE",
            target = "CALENDAR",
            fields = "definition")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id, @RequestBody CalendarUpsertRequest request) {
        calendarService.update(id, request);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "CALENDAR_DELETE",
            target = "CALENDAR",
            fields = "definition")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        calendarService.delete(id);
        return ApiResponse.success();
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.CONFIGURATION_WRITE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "CALENDAR_DELETE_BATCH",
            target = "CALENDAR",
            fields = "definition")
    @DeleteMapping("/batch")
    public ApiResponse<Void> deleteBatch(@RequestBody BatchDeleteRequest request) {
        calendarService.deleteBatch(request);
        return ApiResponse.success();
    }
}
