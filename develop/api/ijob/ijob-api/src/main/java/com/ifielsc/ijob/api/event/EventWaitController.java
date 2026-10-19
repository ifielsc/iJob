package com.ifielsc.ijob.api.event;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.enums.EventWaitStatus;
import com.ifielsc.ijob.contract.event.EventWaitDTO;
import com.ifielsc.ijob.contract.event.EventWaitDecisionRequest;
import com.ifielsc.ijob.contract.event.EventWaitPageQuery;
import com.ifielsc.ijob.core.auth.AuthSession;
import com.ifielsc.ijob.core.event.EventWaitQueryService;
import com.ifielsc.ijob.core.event.EventWaitResolutionService;

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

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.APPROVAL_DECIDE)
    @GetMapping("/{id}")
    public ApiResponse<EventWaitDTO> get(@PathVariable Long id) {
        return ApiResponse.success(queryService.get(id, operatorId()));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.APPROVAL_DECIDE)
    @GetMapping
    public ApiResponse<PageResult<EventWaitDTO>> approvals(EventWaitPageQuery query) {
        return ApiResponse.success(queryService.queryApprovals(query, operatorId()));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.APPROVAL_DECIDE)
    @GetMapping("/by-instance/{instanceId}")
    public ApiResponse<EventWaitDTO> getByInstance(@PathVariable Long instanceId) {
        return ApiResponse.success(queryService.getByInstance(instanceId, operatorId()));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.APPROVAL_DECIDE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "EVENT_WAIT_APPROVE",
            target = "EVENT_WAIT",
            fields = "")
    @PostMapping("/{id}/approve")
    public ApiResponse<EventWaitDTO> approve(
            @PathVariable Long id,
            @RequestBody(required = false) EventWaitDecisionRequest request) {
        return decide(id, EventWaitStatus.APPROVED, request);
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.APPROVAL_DECIDE)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "EVENT_WAIT_REJECT",
            target = "EVENT_WAIT",
            fields = "")
    @PostMapping("/{id}/reject")
    public ApiResponse<EventWaitDTO> reject(
            @PathVariable Long id,
            @RequestBody(required = false) EventWaitDecisionRequest request) {
        return decide(id, EventWaitStatus.REJECTED, request);
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.APPROVAL_DECIDE)
    @com.ifielsc.ijob.api.security.AuditOperation(
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
