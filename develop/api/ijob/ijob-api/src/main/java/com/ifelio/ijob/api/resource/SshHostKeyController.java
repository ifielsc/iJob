package com.ifelio.ijob.api.resource;

import com.ifelio.ijob.api.security.ApiAccess;
import com.ifelio.ijob.api.security.AuditOperation;
import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.auth.AccountPermission;
import com.ifelio.ijob.contract.resource.SshHostKeyDTO;
import com.ifelio.ijob.contract.resource.SshHostKeyProbeRequest;
import com.ifelio.ijob.core.resource.factory.ssh.SshHostKeyProbeService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SshHostKeyController {
    private final SshHostKeyProbeService service;

    public SshHostKeyController(SshHostKeyProbeService service) {
        this.service = service;
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(
            action = "SSH_HOST_KEY_PROBE",
            target = "RESOURCE",
            fields = "hostIdentity",
            external = true)
    @PostMapping("/api/resources/ssh/host-key")
    public ApiResponse<SshHostKeyDTO> probe(@RequestBody SshHostKeyProbeRequest request) {
        return ApiResponse.success(service.probe(request));
    }
}
