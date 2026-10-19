// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.resource;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.api.security.AuditOperation;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.resource.SshHostKeyDTO;
import com.ifielsc.ijob.contract.resource.SshHostKeyProbeRequest;
import com.ifielsc.ijob.core.resource.factory.ssh.SshHostKeyProbeService;

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
