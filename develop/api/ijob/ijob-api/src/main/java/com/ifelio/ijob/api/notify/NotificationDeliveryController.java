package com.ifelio.ijob.api.notify;

import com.ifelio.ijob.api.security.ApiAccess;
import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.contract.auth.AccountPermission;
import com.ifelio.ijob.contract.notify.NotificationDeliveryDTO;
import com.ifelio.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifelio.ijob.core.notify.NotificationDeliveryService;

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
