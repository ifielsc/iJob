package com.ifelio.ijob.api.auth;

import com.ifelio.ijob.common.response.ApiResponse;
import com.ifelio.ijob.contract.auth.CurrentUserResponse;
import com.ifelio.ijob.contract.auth.LoginRequest;
import com.ifelio.ijob.contract.auth.LoginResponse;
import com.ifelio.ijob.core.auth.AuthService;

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

    @com.ifelio.ijob.api.security.ApiAccess(publicEndpoint = true)
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @com.ifelio.ijob.api.security.ApiAccess(selfService = true)
    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> currentUser() {
        return ApiResponse.success(authService.currentUser());
    }

    @com.ifelio.ijob.api.security.ApiAccess(selfService = true)
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success();
    }
}
