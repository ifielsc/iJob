// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.security;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.auth.CurrentUserResponse;
import com.ifielsc.ijob.core.auth.AuditService;
import com.ifielsc.ijob.core.auth.AuthService;
import com.ifielsc.ijob.core.auth.SecurityError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiSecurityInterceptor implements HandlerInterceptor {
    public static final String ACTOR = ApiSecurityInterceptor.class.getName() + ".actor";
    private final AppProperties properties;
    private final AuthService auth;
    private final AuditService audit;
    private long denialWindow;
    private int denialCount;

    public ApiSecurityInterceptor(AppProperties properties, AuthService auth, AuditService audit) {
        this.properties = properties;
        this.auth = auth;
        this.audit = audit;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.getRuntime().getRoles().contains(AppProperties.RuntimeRole.API))
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE);
        ApiAccess policy =
                handler instanceof HandlerMethod method
                        ? method.getMethodAnnotation(ApiAccess.class)
                        : null;
        if (policy == null) {
            deny(null);
            throw new BusinessException(SecurityError.DENIED);
        }
        if (policy.publicEndpoint()) return true;
        if (properties.getAuth().getMode() == AppProperties.AuthMode.NONE) return true;
        CurrentUserResponse actor = auth.currentUser();
        request.setAttribute(ACTOR, actor);
        if (policy.selfService()) return true;
        if (actor.isMustChangePassword()) {
            deny(actor);
            throw new BusinessException(SecurityError.CHANGE_REQUIRED);
        }
        if (!actor.getPermissions().contains(policy.value())) {
            deny(actor);
            throw new BusinessException(SecurityError.DENIED);
        }
        return true;
    }

    private void deny(CurrentUserResponse actor) {
        if (!allowDenialRecord()) return;
        try {
            audit.bestEffort(audit.prepare(actor, "HTTP_DENIED", "API", null, null), "REJECTED");
        } catch (RuntimeException ignored) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .error("SECURITY_AUDIT_WRITE_FAILED action=HTTP_DENIED");
        }
    }

    /** Bound denied-request write amplification independently of login protection. */
    private synchronized boolean allowDenialRecord() {
        long now = System.nanoTime();
        if (now - denialWindow >= 1_000_000_000L) {
            if (denialCount > 20)
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("SECURITY_DENIAL_AUDIT_SUPPRESSED count={}", denialCount - 20);
            denialWindow = now;
            denialCount = 0;
        }
        return denialCount++ < 20;
    }
}
