// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.api.resource;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.api.security.AuditOperation;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.resource.FileBrowseDTO;
import com.ifielsc.ijob.core.auth.AuthService;
import com.ifielsc.ijob.core.resource.FileBrowseService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/file-browser")
public class FileBrowseController {
    private final FileBrowseService browser;
    private final AuthService auth;

    public FileBrowseController(FileBrowseService browser, AuthService auth) {
        this.browser = browser;
        this.auth = auth;
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @GetMapping("/roots")
    public ApiResponse<List<FileBrowseDTO.Root>> roots() {
        return ApiResponse.success(browser.roots());
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @AuditOperation(
            action = "FILE_BROWSE_OPEN",
            target = "RESOURCE",
            fields = "resourceId",
            external = true)
    @PostMapping("/sessions")
    public ApiResponse<FileBrowseDTO.Session> open(@RequestBody FileBrowseDTO.Request request) {
        return ApiResponse.success(browser.open(request, auth.currentUser().getLoginId()));
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @GetMapping("/sessions/{id}")
    public ApiResponse<FileBrowseDTO.Page> list(
            @PathVariable String id,
            @RequestParam(defaultValue = "") String path,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.success(
                browser.list(
                        id,
                        auth.currentUser().getLoginId(),
                        path,
                        search,
                        page,
                        pageSize,
                        refresh));
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @DeleteMapping("/sessions/{id}")
    @AuditOperation(action = "FILE_BROWSE_CLOSE", target = "RESOURCE", external = true)
    public ApiResponse<Void> close(@PathVariable String id) {
        browser.close(id, auth.currentUser().getLoginId());
        return ApiResponse.success();
    }
}
