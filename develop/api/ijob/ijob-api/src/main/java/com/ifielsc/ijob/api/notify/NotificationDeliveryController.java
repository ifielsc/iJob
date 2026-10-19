package com.ifielsc.ijob.api.notify;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.notify.NotificationDeliveryDTO;
import com.ifielsc.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifielsc.ijob.core.notify.NotificationDeliveryService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification-deliveries")
public class NotificationDeliveryController {
    private final NotificationDeliveryService service;

    public NotificationDeliveryController(NotificationDeliveryService service) {
        this.service = service;
    }

    @ApiAccess(AccountPermission.SENSITIVE_READ)
    @GetMapping
    public ApiResponse<PageResult<NotificationDeliveryDTO>> queryPage(
            NotifySendLogPageQuery query) {
        return ApiResponse.success(service.queryPage(query));
    }

    @ApiAccess(AccountPermission.SENSITIVE_READ)
    @GetMapping("/{id}")
    public ApiResponse<NotificationDeliveryDTO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(service.getDetail(id));
    }
}
