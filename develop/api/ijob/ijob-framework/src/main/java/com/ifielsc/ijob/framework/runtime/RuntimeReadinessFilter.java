// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RuntimeReadinessFilter extends OncePerRequestFilter {
    private final ApplicationRuntimeState runtimeState;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    public RuntimeReadinessFilter(
            ApplicationRuntimeState runtimeState,
            MessageSource messageSource,
            ObjectMapper objectMapper) {
        this.runtimeState = runtimeState;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || runtimeState.isReady();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        String message =
                messageSource.getMessage(
                        "error.runtime.not-ready",
                        null,
                        "Service is not ready",
                        request.getLocale());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", 503);
        body.put("errorCode", "IJOB-RUNTIME-0503");
        body.put("messageKey", "error.runtime.not-ready");
        body.put("messageArgs", java.util.List.of());
        body.put("message", message);
        body.put("traceId", MDC.get("traceId"));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
