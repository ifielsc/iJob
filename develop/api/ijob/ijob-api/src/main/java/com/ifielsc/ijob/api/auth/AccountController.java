// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.auth;

import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.api.security.AuditOperation;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.contract.auth.AccountAccessRequest;
import com.ifielsc.ijob.contract.auth.AccountCreateRequest;
import com.ifielsc.ijob.contract.auth.AccountDTO;
import com.ifielsc.ijob.contract.auth.AccountPermission;
import com.ifielsc.ijob.contract.auth.AccountProfileRequest;
import com.ifielsc.ijob.contract.auth.AccountRole;
import com.ifielsc.ijob.contract.auth.ApproverDTO;
import com.ifielsc.ijob.contract.auth.PasswordChangeRequest;
import com.ifielsc.ijob.contract.auth.PasswordResetRequest;
import com.ifielsc.ijob.contract.auth.RoleDTO;
import com.ifielsc.ijob.core.auth.AccountRolePolicy;
import com.ifielsc.ijob.core.auth.AccountService;
import com.ifielsc.ijob.core.auth.AuthService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
public class AccountController {
    private final AccountService accounts;
    private final AuthService auth;

    public AccountController(AccountService accounts, AuthService auth) {
        this.accounts = accounts;
        this.auth = auth;
    }

    @ApiAccess(AccountPermission.USER_MANAGE)
    @GetMapping("/api/users")
    public ApiResponse<PageResult<AccountDTO>> query(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) AccountRole roleCode,
            @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.success(accounts.query(pageNum, pageSize, name, roleCode, enabled));
    }

    @ApiAccess(AccountPermission.USER_MANAGE)
    @GetMapping("/api/users/{id}")
    public ApiResponse<AccountDTO> getDetail(@PathVariable Long id) {
        return ApiResponse.success(accounts.getDetail(id));
    }

    @ApiAccess(AccountPermission.USER_MANAGE)
    @AuditOperation(
            action = "ACCOUNT_CREATE",
            target = "ACCOUNT",
            fields = "username,displayName,roleCode,enabled,password")
    @PostMapping("/api/users")
    public ApiResponse<AccountDTO> create(@RequestBody AccountCreateRequest request) {
        return ApiResponse.success(accounts.create(request, auth.currentUser()));
    }

    @ApiAccess(AccountPermission.USER_MANAGE)
    @AuditOperation(action = "ACCOUNT_PROFILE", target = "ACCOUNT", fields = "displayName")
    @PutMapping("/api/users/{id}/profile")
    public ApiResponse<AccountDTO> profile(
            @PathVariable Long id, @RequestBody AccountProfileRequest request) {
        return ApiResponse.success(accounts.profile(id, request));
    }

    @ApiAccess(AccountPermission.USER_MANAGE)
    @AuditOperation(action = "ACCOUNT_ACCESS", target = "ACCOUNT", fields = "roleCode,enabled")
    @PutMapping("/api/users/{id}/access")
    public ApiResponse<AccountDTO> access(
            @PathVariable Long id, @RequestBody AccountAccessRequest request) {
        return ApiResponse.success(accounts.access(id, request, auth.currentUser()));
    }

    @ApiAccess(AccountPermission.USER_MANAGE)
    @AuditOperation(action = "ACCOUNT_RESET_PASSWORD", target = "ACCOUNT", fields = "password")
    @PostMapping("/api/users/{id}/reset-password")
    public ApiResponse<Void> reset(
            @PathVariable Long id, @RequestBody PasswordResetRequest request) {
        accounts.reset(id, request);
        return ApiResponse.success();
    }

    @ApiAccess(AccountPermission.USER_MANAGE)
    @AuditOperation(action = "ACCOUNT_DELETE", target = "ACCOUNT", fields = "account")
    @DeleteMapping("/api/users/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Long expectedRevision) {
        accounts.delete(id, expectedRevision, auth.currentUser());
        return ApiResponse.success();
    }

    @ApiAccess(selfService = true)
    @AuditOperation(action = "ACCOUNT_CHANGE_PASSWORD", target = "ACCOUNT", fields = "password")
    @PostMapping("/api/auth/change-password")
    public ApiResponse<Void> changePassword(@RequestBody PasswordChangeRequest request) {
        accounts.changePassword(request, auth.currentUser());
        return ApiResponse.success();
    }

    @ApiAccess(AccountPermission.USER_MANAGE)
    @GetMapping("/api/roles")
    public ApiResponse<List<RoleDTO>> roles() {
        return ApiResponse.success(
                Arrays.stream(AccountRole.values())
                        .map(role -> new RoleDTO(role, AccountRolePolicy.permissions(role)))
                        .toList());
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @GetMapping("/api/users/approvers")
    public ApiResponse<List<ApproverDTO>> approvers(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name) {
        return ApiResponse.success(accounts.approvers(pageNum, pageSize, name));
    }

    @ApiAccess(AccountPermission.CONFIGURATION_WRITE)
    @PostMapping("/api/users/approvers/resolve")
    public ApiResponse<List<com.ifielsc.ijob.contract.auth.ApproverResolutionDTO>> resolveApprovers(
            @RequestBody List<String> loginIds) {
        return ApiResponse.success(accounts.resolveApprovers(loginIds));
    }
}
