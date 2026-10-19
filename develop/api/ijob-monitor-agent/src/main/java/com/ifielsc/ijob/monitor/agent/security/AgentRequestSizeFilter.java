// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AgentRequestSizeFilter extends OncePerRequestFilter {

    static final int MAX_REQUEST_BYTES = 64 * 1024;

    private static final String COLLECT_PATH = "/api/monitor/collect";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !COLLECT_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            payloadTooLarge(response);
            return;
        }
        byte[] body = readBounded(request.getInputStream());
        if (body == null) {
            payloadTooLarge(response);
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    static byte[] readBounded(InputStream input) throws IOException {
        byte[] body = input.readNBytes(MAX_REQUEST_BYTES + 1);
        return body.length > MAX_REQUEST_BYTES ? null : body;
    }

    private void payloadTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"Monitor request is too large\"}");
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ByteArrayServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class ByteArrayServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream input;

        private ByteArrayServletInputStream(byte[] body) {
            input = new ByteArrayInputStream(body);
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
        public void setReadListener(ReadListener readListener) {
            if (readListener == null) {
                throw new IllegalArgumentException("readListener is required");
            }
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return input.read(bytes, offset, length);
        }
    }
}
