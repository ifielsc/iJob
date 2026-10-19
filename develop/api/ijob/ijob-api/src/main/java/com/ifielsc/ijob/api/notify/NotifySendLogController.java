package com.ifielsc.ijob.api.notify;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.notify.NotifySendLogDTO;
import com.ifielsc.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifielsc.ijob.core.notify.NotifySendLogService;

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

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.SENSITIVE_READ)
    @GetMapping
    public ApiResponse<PageResult<NotifySendLogDTO>> queryPage(NotifySendLogPageQuery query) {
        return ApiResponse.success(notifySendLogService.queryPage(query));
    }
}
