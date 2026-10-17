package com.ifelio.ijob.core.auth;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.auth.AccountRole;
import com.ifelio.ijob.contract.auth.CurrentUserResponse;
import com.ifelio.ijob.contract.auth.LoginRequest;
import com.ifelio.ijob.contract.auth.LoginResponse;
import com.ifelio.ijob.infra.entity.auth.AuthUser;
import com.ifelio.ijob.infra.mapper.auth.AuthUserMapper;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Semaphore;

@Service
public class DatabaseAuthentication {
    private final AppProperties properties;
    private final AuthUserMapper users;
    private final PasswordEncoder encoder;
    private final AuthSession sessions;
    private final AuditService audit;
    private final Semaphore checks = new Semaphore(2);
    private final String dummyHash;

    public DatabaseAuthentication(
            AppProperties properties,
            AuthUserMapper users,
            PasswordEncoder encoder,
            AuthSession sessions,
            AuditService audit) {
        this.properties = properties;
        this.users = users;
        this.encoder = encoder;
        this.sessions = sessions;
        this.audit = audit;
        dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request) {
        if (request == null
                || request.getUsername() == null
                || request.getUsername().isBlank()
                || request.getPassword() == null
                || request.getPassword().isBlank()) {
            staticFailure();
            throw new BusinessException(AuthError.CREDENTIALS_REQUIRED);
        }
        if (request.getUsername().length() > 128
                || request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            staticFailure();
            throw new BusinessException(AuthError.INVALID_CREDENTIALS);
        }
        if (!checks.tryAcquire()) throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        AuthUser user;
        boolean matches;
        try {
            user = users.selectByUsername(request.getUsername());
            matches =
                    encoder.matches(
                            request.getPassword(),
                            user == null ? dummyHash : user.getPasswordHash());
        } finally {
            checks.release();
        }
        if (user == null || !matches || !Boolean.TRUE.equals(user.getEnabled())) {
            staticFailure();
            throw new BusinessException(AuthError.INVALID_CREDENTIALS);
        }
        var event = audit.prepare(principal(user), "AUTH_LOGIN", "SESSION", null, null);
        LoginResponse response =
                sessions.loginAccount(
                        user.getLoginId(), user.getUsername(), user.getSecurityVersion());
        try {
            AuthUser latest = users.selectByLoginId(user.getLoginId());
            if (latest == null
                    || !Boolean.TRUE.equals(latest.getEnabled())
                    || !Objects.equals(latest.getSecurityVersion(), user.getSecurityVersion())) {
                throw new BusinessException(AuthError.INVALID_CREDENTIALS);
            }
            audit.independent(event, "SUCCESS", null);
            return response;
        } catch (RuntimeException failure) {
            try {
                sessions.logout();
            } catch (RuntimeException ignored) {
                /* No credential is returned to the caller. */
            }
            throw failure;
        }
    }

    public CurrentUserResponse currentUser() {
        CurrentUserResponse session = sessions.currentUser();
        if (properties.getAuth().getMode() != AppProperties.AuthMode.DATABASE) {
            if (properties.getAuth().getMode() != AppProperties.AuthMode.STATIC
                    || !Objects.equals(properties.getAuth().getUsername(), session.getLoginId()))
                throw new BusinessException(AuthError.INVALID_CREDENTIALS);
            return new CurrentUserResponse(
                    session.getLoginId(),
                    session.getUsername(),
                    session.getUsername(),
                    AccountRole.ADMIN,
                    staticPermissions(),
                    false);
        }
        AuthUser user = users.selectByLoginId(session.getLoginId());
        if (user == null
                || !Boolean.TRUE.equals(user.getEnabled())
                || !Objects.equals(user.getSecurityVersion(), sessions.securityVersion())) {
            throw new BusinessException(AuthError.INVALID_CREDENTIALS);
        }
        return principal(user);
    }

    public void staticFailure() {
        try {
            audit.bestEffort(audit.prepare(null, "AUTH_LOGIN", "SESSION", null, null), "REJECTED");
        } catch (RuntimeException ignored) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .error("SECURITY_AUDIT_WRITE_FAILED action=AUTH_LOGIN");
        }
    }

    public void staticSuccess(String username) {
        CurrentUserResponse actor = new CurrentUserResponse(username, username);
        try {
            audit.independent(
                    audit.prepare(actor, "AUTH_LOGIN", "SESSION", null, null), "SUCCESS", null);
        } catch (RuntimeException failure) {
            try {
                sessions.logout();
            } catch (RuntimeException ignored) {
            }
            throw failure;
        }
    }

    public void logout() {
        CurrentUserResponse actor = currentUser();
        sessions.logout();
        try {
            audit.bestEffort(audit.prepare(actor, "AUTH_LOGOUT", "SESSION", null, null), "SUCCESS");
        } catch (RuntimeException ignored) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .error("SECURITY_AUDIT_WRITE_FAILED action=AUTH_LOGOUT");
        }
    }

    private static CurrentUserResponse principal(AuthUser user) {
        return new CurrentUserResponse(
                user.getLoginId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoleCode(),
                AccountRolePolicy.permissions(user.getRoleCode()),
                Boolean.TRUE.equals(user.getMustChangePassword()));
    }

    private static java.util.Set<com.ifelio.ijob.contract.auth.AccountPermission>
            staticPermissions() {
        var permissions =
                java.util.EnumSet.copyOf(AccountRolePolicy.permissions(AccountRole.ADMIN));
        permissions.remove(com.ifelio.ijob.contract.auth.AccountPermission.USER_MANAGE);
        permissions.remove(com.ifelio.ijob.contract.auth.AccountPermission.SELF_PASSWORD_CHANGE);
        return permissions;
    }
}
