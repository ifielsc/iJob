package com.ifelio.ijob.api.notify;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.notify.NotifySendLogDTO;
import com.ifelio.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifelio.ijob.core.notify.NotifySendLogService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notify-send-logs")
public class NotifySendLogController {
    private final NotifySendLogService notifySendLogService;

    public NotifySendLogController(NotifySendLogService notifySendLogService) {
        this.notifySendLogService = notifySendLogService;
    }

    @com.ifelio.ijob.api.security.ApiAccess(
            com.ifelio.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping
    public ApiResponse<PageResult<NotifySendLogDTO>> queryPage(NotifySendLogPageQuery query) {
        return ApiResponse.success(notifySendLogService.queryPage(query));
    }
}
