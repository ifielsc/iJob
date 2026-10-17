package com.ifelio.ijob.monitor.agent.security;

import com.ifelio.ijob.monitor.agent.config.AgentProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentAuthenticationFilter extends OncePerRequestFilter {

    private static final String MONITOR_PATH_PREFIX = "/api/monitor";
    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedToken;

    public AgentAuthenticationFilter(AgentProperties properties) {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("agent token must be configured");
        }
        if (!token.equals(token.trim())) {
            throw new IllegalStateException("agent token cannot have surrounding whitespace");
        }
        if (token.length() > 4096) {
            throw new IllegalStateException("agent token is too long");
        }
        expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.equals(MONITOR_PATH_PREFIX) && !path.startsWith(MONITOR_PATH_PREFIX + "/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isAuthorized(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            unauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    boolean isAuthorized(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] actualToken =
                authorization.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedToken, actualToken);
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"Unauthorized\"}");
    }
}
