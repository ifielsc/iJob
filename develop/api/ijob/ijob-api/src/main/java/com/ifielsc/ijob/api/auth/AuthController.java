// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.auth;

import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.CurrentUserResponse;
import com.ifielsc.ijob.contract.auth.LoginRequest;
import com.ifielsc.ijob.contract.auth.LoginResponse;
import com.ifielsc.ijob.core.auth.AuthService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @com.ifielsc.ijob.api.security.ApiAccess(publicEndpoint = true)
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @com.ifielsc.ijob.api.security.ApiAccess(selfService = true)
    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> currentUser() {
        return ApiResponse.success(authService.currentUser());
    }

    @com.ifielsc.ijob.api.security.ApiAccess(selfService = true)
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success();
    }
}
