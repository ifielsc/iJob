package com.ifelio.ijob.api.event;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.enums.EventWaitStatus;
import com.ifelio.ijob.contract.event.EventWaitDTO;
import com.ifelio.ijob.contract.event.EventWaitDecisionRequest;
import com.ifelio.ijob.contract.event.EventWaitPageQuery;
import com.ifelio.ijob.core.auth.AuthSession;
import com.ifelio.ijob.core.event.EventWaitQueryService;
import com.ifelio.ijob.core.event.EventWaitResolutionService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event-waits")
public class EventWaitController {
    private final EventWaitResolutionService resolutionService;
    private final EventWaitQueryService queryService;
    private final AuthSession authSession;

    public EventWaitController(
            EventWaitResolutionService resolutionService,
            EventWaitQueryService queryService,
            AuthSession authSession) {
        this.resolutionService = resolutionService;
        this.queryService = queryService;
        this.authSession = authSession;
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.APPROVAL_DECIDE)
    @GetMapping("/{id}")
    public ApiResponse<EventWaitDTO> get(@PathVariable Long id) {
        return ApiResponse.success(queryService.get(id, operatorId()));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.APPROVAL_DECIDE)
    @GetMapping
    public ApiResponse<PageResult<EventWaitDTO>> approvals(EventWaitPageQuery query) {
        return ApiResponse.success(queryService.queryApprovals(query, operatorId()));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.APPROVAL_DECIDE)
    @GetMapping("/by-instance/{instanceId}")
    public ApiResponse<EventWaitDTO> getByInstance(@PathVariable Long instanceId) {
        return ApiResponse.success(queryService.getByInstance(instanceId, operatorId()));
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.APPROVAL_DECIDE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "EVENT_WAIT_APPROVE",
            target = "EVENT_WAIT",
            fields = "")
    @PostMapping("/{id}/approve")
    public ApiResponse<EventWaitDTO> approve(
            @PathVariable Long id,
            @RequestBody(required = false) EventWaitDecisionRequest request) {
        return decide(id, EventWaitStatus.APPROVED, request);
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.APPROVAL_DECIDE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "EVENT_WAIT_REJECT",
            target = "EVENT_WAIT",
            fields = "")
    @PostMapping("/{id}/reject")
    public ApiResponse<EventWaitDTO> reject(
            @PathVariable Long id,
            @RequestBody(required = false) EventWaitDecisionRequest request) {
        return decide(id, EventWaitStatus.REJECTED, request);
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.APPROVAL_DECIDE)
    @com.ifelio.ijob.api.security.AuditOperation(
            action = "EVENT_WAIT_WITHDRAW",
            target = "EVENT_WAIT",
            fields = "")
    @PostMapping("/{id}/withdraw")
    public ApiResponse<EventWaitDTO> withdraw(
            @PathVariable Long id,
            @RequestBody(required = false) EventWaitDecisionRequest request) {
        return decide(id, EventWaitStatus.WITHDRAWN, request);
    }

    private ApiResponse<EventWaitDTO> decide(
            Long id, EventWaitStatus status, EventWaitDecisionRequest request) {
        String comment = request == null ? null : request.getComment();
        return ApiResponse.success(
                queryService.toDto(resolutionService.decide(id, status, operatorId(), comment)));
    }

    private String operatorId() {
        return authSession.currentUser().getLoginId();
    }
}
