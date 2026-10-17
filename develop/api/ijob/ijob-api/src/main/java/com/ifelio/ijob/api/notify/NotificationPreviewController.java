package com.ifelio.ijob.api.notify;

import com.ifelio.ijob.api.security.ApiAccess;
import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.auth.AccountPermission;
import com.ifelio.ijob.contract.notify.NotifyPreviewDTO;
import com.ifelio.ijob.contract.notify.NotifyPreviewRequest;
import com.ifelio.ijob.core.notify.template.NotificationPreviewService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationPreviewController {
    private final NotificationPreviewService previews;

    public NotificationPreviewController(NotificationPreviewService previews) {
        this.previews = previews;
    }

    @ApiAccess(AccountPermission.SENSITIVE_READ)
    @PostMapping("/api/notification-preview")
    public ApiResponse<NotifyPreviewDTO> preview(@RequestBody NotifyPreviewRequest request) {
        return ApiResponse.success(previews.preview(request));
    }
}
