// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

class LoginProtectionFilterTest {
    private LoginProtectionFilter filter() {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        return new LoginProtectionFilter(new ObjectMapper(), messages);
    }

    private MockHttpServletRequest request(String body) {
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("127.0.0.8");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    void oversizedBodyIsRejectedBeforeDeserializationIncludingUnknownLength() throws Exception {
        var filter = filter();
        var request = request("x".repeat(4097));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (a, b) -> fail("Must reject before controller"));
        assertEquals(413, response.getStatus());
        var chunked =
                new jakarta.servlet.http.HttpServletRequestWrapper(request("x".repeat(4097))) {
                    @Override
                    public long getContentLengthLong() {
                        return -1;
                    }

                    @Override
                    public int getContentLength() {
                        return -1;
                    }
                };
        response = new MockHttpServletResponse();
        filter.doFilter(chunked, response, (a, b) -> fail("Must reject chunked body"));
        assertEquals(413, response.getStatus());
    }

    @Test
    void bodyIsPreservedAndForwardedHeadersCannotBypassPeerRateLimit() throws Exception {
        var filter = filter();
        for (int index = 0; index < 10; index++) {
            var request = request("{\"username\":\"test\"}");
            request.addHeader("X-Forwarded-For", "192.0.2." + index);
            var response = new MockHttpServletResponse();
            filter.doFilter(
                    request,
                    response,
                    (a, b) ->
                            assertEquals(
                                    "{\"username\":\"test\"}",
                                    new String(
                                            a.getInputStream().readAllBytes(),
                                            StandardCharsets.UTF_8)));
            assertEquals(200, response.getStatus());
        }
        var request = request("{\"password\":\"synthetic-secret\"}");
        request.addPreferredLocale(Locale.SIMPLIFIED_CHINESE);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (a, b) -> fail("Rate limit bypass"));
        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertFalse(response.getContentAsString().contains("synthetic-secret"));
        assertTrue(response.getContentAsString().contains("IJOB-COMMON-0429"));
    }
}
