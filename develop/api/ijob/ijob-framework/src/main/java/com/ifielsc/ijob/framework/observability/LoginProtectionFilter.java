package com.ifielsc.ijob.framework.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.ResultCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;

/** Local defence in depth; the trusted ingress must enforce a shared cluster budget. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class LoginProtectionFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger AUDIT =
            org.slf4j.LoggerFactory.getLogger(LoginProtectionFilter.class);
    static final int MAX_BODY_BYTES = 4096;
    private final Cache<String, Window> addresses =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterAccess(Duration.ofMinutes(2))
                    .build();
    private final Window account = new Window();
    private final ObjectMapper mapper;
    private final MessageSource messages;

    public LoginProtectionFilter(ObjectMapper mapper, MessageSource messages) {
        this.mapper = mapper;
        this.messages = messages;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !(request.getContextPath() + "/api/auth/login").equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Do not accept X-Forwarded-For from arbitrary clients. getRemoteAddr is the peer.
        if (!account.take(60)
                || !addresses.get(request.getRemoteAddr(), key -> new Window()).take(10)) {
            response.setHeader("Retry-After", "60");
            reject(request, response, ResultCode.TOO_MANY_REQUESTS);
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            reject(request, response, ResultCode.PAYLOAD_TOO_LARGE);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            reject(request, response, ResultCode.PAYLOAD_TOO_LARGE);
            return;
        }
        chain.doFilter(
                new HttpServletRequestWrapper(request) {
                    @Override
                    public ServletInputStream getInputStream() {
                        ByteArrayInputStream input = new ByteArrayInputStream(body);
                        return new ServletInputStream() {
                            @Override
                            public int read() {
                                return input.read();
                            }

                            @Override
                            public boolean isFinished() {
                                return input.available() == 0;
                            }

                            @Override
                            public boolean isReady() {
                                return true;
                            }

                            @Override
                            public void setReadListener(ReadListener listener) {
                                throw new UnsupportedOperationException(
                                        "Synchronous login body only");
                            }
                        };
                    }

                    @Override
                    public BufferedReader getReader() {
                        return new BufferedReader(
                                new InputStreamReader(
                                        getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    }
                },
                response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, ResultCode code)
            throws IOException {
        response.setStatus(code.code());
        // Only a validated network address is logged, never credentials or forwarded headers.
        String peer = request.getRemoteAddr();
        if (peer == null || !peer.matches("[0-9A-Fa-f:.%]{1,64}")) peer = "unknown";
        AUDIT.warn("Login request rejected, peer={}, status={}", peer, code.code());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        String message =
                messages.getMessage(code.messageKey(), null, code.message(), request.getLocale());
        mapper.writeValue(
                response.getOutputStream(),
                ApiResponse.fail(code, message, null, MDC.get("traceId")));
    }

    private static final class Window {
        private long started = System.nanoTime();
        private int attempts;

        synchronized boolean take(int limit) {
            long now = System.nanoTime();
            if (now - started >= Duration.ofMinutes(1).toNanos()) {
                started = now;
                attempts = 0;
            }
            if (attempts >= limit) return false;
            attempts++;
            return true;
        }
    }
}
