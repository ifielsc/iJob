// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.auth;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.AuthMode;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.auth.CurrentUserResponse;
import com.ifielsc.ijob.contract.auth.LoginRequest;
import com.ifielsc.ijob.contract.auth.LoginResponse;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@lombok.extern.slf4j.Slf4j
public class AuthService {
    private static final Pattern BCRYPT_HASH =
            Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final AppProperties appProperties;
    private final PasswordEncoder passwordEncoder;
    private final AuthSession authSession;
    private final DatabaseAuthentication databaseAuthentication;
    private final java.util.concurrent.Semaphore passwordChecks =
            new java.util.concurrent.Semaphore(2);

    public AuthService(
            AppProperties appProperties, PasswordEncoder passwordEncoder, AuthSession authSession) {
        this(appProperties, passwordEncoder, authSession, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            AppProperties appProperties,
            PasswordEncoder passwordEncoder,
            AuthSession authSession,
            DatabaseAuthentication databaseAuthentication) {
        this.appProperties = appProperties;
        this.passwordEncoder = passwordEncoder;
        this.authSession = authSession;
        this.databaseAuthentication = databaseAuthentication;
    }

    public LoginResponse login(LoginRequest request) {
        if (appProperties.getAuth().getMode() == AuthMode.DATABASE) {
            return databaseAuthentication.login(request);
        }
        AppProperties.Auth auth = requireConfiguredAuth();
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            if (databaseAuthentication != null) databaseAuthentication.staticFailure();
            throw new BusinessException(AuthError.CREDENTIALS_REQUIRED);
        }

        if (request.getUsername().length() > 128
                || request.getPassword().length() > 72
                || request.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        > 72) {
            log.warn("Authentication rejected, reason=field_limit");
            if (databaseAuthentication != null) databaseAuthentication.staticFailure();
            throw new BusinessException(AuthError.INVALID_CREDENTIALS);
        }
        if (!passwordChecks.tryAcquire()) {
            throw new BusinessException(
                    com.ifielsc.ijob.common.response.ResultCode.TOO_MANY_REQUESTS);
        }

        boolean passwordMatches;
        try {
            passwordMatches =
                    passwordEncoder.matches(request.getPassword(), auth.getPasswordHash());
        } finally {
            passwordChecks.release();
        }
        if (!auth.getUsername().equals(request.getUsername()) || !passwordMatches) {
            log.warn("Authentication rejected, reason=invalid_credentials");
            if (databaseAuthentication != null) databaseAuthentication.staticFailure();
            throw new BusinessException(AuthError.INVALID_CREDENTIALS);
        }
        log.info("Authentication succeeded");
        LoginResponse response = authSession.login(auth.getUsername());
        if (databaseAuthentication != null)
            databaseAuthentication.staticSuccess(auth.getUsername());
        return response;
    }

    public CurrentUserResponse currentUser() {
        if (databaseAuthentication != null) return databaseAuthentication.currentUser();
        return authSession.currentUser();
    }

    public void logout() {
        if (databaseAuthentication == null) authSession.logout();
        else databaseAuthentication.logout();
    }

    private AppProperties.Auth requireConfiguredAuth() {
        AppProperties.Auth auth = appProperties.getAuth();
        if (auth.getMode() != AuthMode.STATIC) {
            throw new BusinessException(AuthError.DISABLED);
        }
        if (isBlank(auth.getUsername())
                || isBlank(auth.getPasswordHash())
                || !BCRYPT_HASH.matcher(auth.getPasswordHash()).matches()) {
            throw new BusinessException(AuthError.MISCONFIGURED);
        }
        return auth;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
