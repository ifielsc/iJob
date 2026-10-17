package com.ifelio.ijob.framework.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class TraceIdFilterTest {
    @Test
    void shouldReuseValidIncomingTraceIdAndClearMdc() throws Exception {
        String incoming = "ABCDEF0123456789ABCDEF0123456789";
        Map<String, Object> attributes = new HashMap<>();
        AtomicReference<String> responseHeader = new AtomicReference<>();
        TraceIdFilter filter = new TraceIdFilter();

        filter.doFilterInternal(
                request(incoming, attributes),
                response(responseHeader),
                (request, response) -> assertEquals(incoming.toLowerCase(), MDC.get("traceId")));

        assertEquals(incoming.toLowerCase(), responseHeader.get());
        assertEquals(incoming.toLowerCase(), attributes.get("traceId"));
        assertNull(MDC.get("traceId"));
    }

    @Test
    void shouldReplaceInvalidIncomingTraceId() throws Exception {
        AtomicReference<String> responseHeader = new AtomicReference<>();
        new TraceIdFilter()
                .doFilterInternal(
                        request("unsafe trace value", new HashMap<>()),
                        response(responseHeader),
                        (request, response) -> {});

        assertTrue(responseHeader.get().matches("[a-f0-9]{32}"));
    }

    private HttpServletRequest request(String traceId, Map<String, Object> attributes) {
        return (HttpServletRequest)
                Proxy.newProxyInstance(
                        HttpServletRequest.class.getClassLoader(),
                        new Class<?>[] {HttpServletRequest.class},
                        (proxy, method, arguments) ->
                                switch (method.getName()) {
                                    case "getHeader" -> traceId;
                                    case "setAttribute" -> {
                                        attributes.put((String) arguments[0], arguments[1]);
                                        yield null;
                                    }
                                    default -> defaultValue(method.getReturnType());
                                });
    }

    private HttpServletResponse response(AtomicReference<String> responseHeader) {
        return (HttpServletResponse)
                Proxy.newProxyInstance(
                        HttpServletResponse.class.getClassLoader(),
                        new Class<?>[] {HttpServletResponse.class},
                        (proxy, method, arguments) -> {
                            if ("setHeader".equals(method.getName())
                                    && TraceIdFilter.HEADER_NAME.equals(arguments[0])) {
                                responseHeader.set((String) arguments[1]);
                            }
                            return defaultValue(method.getReturnType());
                        });
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
