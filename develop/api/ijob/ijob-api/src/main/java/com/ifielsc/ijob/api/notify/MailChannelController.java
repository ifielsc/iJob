package com.ifielsc.ijob.api.notify;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.api.security.AuditOperation;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.common.BatchDeleteRequest;
import com.ifielsc.ijob.contract.notify.MailChannelDTO;
import com.ifielsc.ijob.contract.notify.MailChannelPageQuery;
import com.ifielsc.ijob.contract.notify.MailChannelRequest;
import com.ifielsc.ijob.contract.notify.MailConnectionDTO;
import com.ifielsc.ijob.core.notify.channel.email.MailChannelService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/mail-channels")
public class MailChannelController {
    private final MailChannelService service;

    public MailChannelController(MailChannelService service) {
        this.service = service;
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(action = "MAIL_CHANNEL_DELETE", target = "MAIL_CHANNEL", fields = "definition")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(
            action = "MAIL_CHANNEL_DELETE_BATCH",
            target = "MAIL_CHANNEL",
            fields = "definition")
    @DeleteMapping("/batch")
    public ApiResponse<Void> deleteBatch(@RequestBody BatchDeleteRequest request) {
        service.deleteBatch(request);
        return ApiResponse.success();
    }

    @ApiAccess(AccountPermission.SENSITIVE_READ)
    @GetMapping
    public ApiResponse<PageResult<MailChannelDTO>> queryPage(MailChannelPageQuery query) {
        return ApiResponse.success(service.queryPage(query));
    }

    @ApiAccess(AccountPermission.SENSITIVE_READ)
    @GetMapping("/{id}")
    public ApiResponse<MailChannelDTO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(service.getDetail(id));
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(action = "MAIL_CHANNEL_CREATE", target = "MAIL_CHANNEL", fields = "definition")
    @PostMapping
    public ResponseEntity<ApiResponse<String>> create(@RequestBody MailChannelRequest request) {
        Long id = service.create(request);
        return ResponseEntity.created(URI.create("/api/mail-channels/" + id))
                .body(ApiResponse.success(id.toString()));
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(action = "MAIL_CHANNEL_UPDATE", target = "MAIL_CHANNEL", fields = "definition")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id, @RequestBody MailChannelRequest request) {
        service.update(id, request);
        return ApiResponse.success();
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(
            action = "MAIL_CHANNEL_TEST_CONNECTION",
            target = "MAIL_CHANNEL",
            fields = "connection",
            external = true)
    @PostMapping("/{id}/test-connection")
    public ApiResponse<MailConnectionDTO> testConnection(@PathVariable Long id) {
        return ApiResponse.success(service.testConnection(id));
    }
}
