package com.ifielsc.ijob.api.notify;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.notify.NotifyPreviewDTO;
import com.ifielsc.ijob.contract.notify.NotifyPreviewRequest;
import com.ifielsc.ijob.core.notify.template.NotificationPreviewService;

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
