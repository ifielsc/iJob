package com.ifelio.ijob.common.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Executors;

class DiagnosticSafetyTest {
    @Test
    void diagnosticsKeepRootCauseAndLocationWithoutSensitiveMessages() {
        var failure =
                new IllegalStateException(
                        "password=synthetic-secret /private/path SELECT token",
                        new java.io.IOException("https://secret.example/api?token=hidden"));
        String diagnostic = SafeDiagnostic.describe(failure);
        assertTrue(diagnostic.contains("java.io.IOException"));
        assertTrue(diagnostic.contains("DiagnosticSafetyTest"));
        for (String secret :
                new String[] {"synthetic-secret", "/private", "SELECT", "secret.example", "hidden"})
            assertFalse(diagnostic.contains(secret));
    }

    @Test
    void contextPropagatesAndRestoresOnWorkerReuseAndFailure() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            MDC.put("traceId", "first");
            try {
                executor.submit(
                                DiagnosticContext.wrap(
                                        () -> assertEquals("first", MDC.get("traceId"))))
                        .get();
                assertEquals("first", MDC.get("traceId"));
                assertNull(executor.submit(() -> MDC.get("traceId")).get());
                try (var ignored = new DiagnosticContext(Map.of("traceId", "nested"))) {
                    assertEquals("nested", MDC.get("traceId"));
                }
                assertEquals("first", MDC.get("traceId"));
            } finally {
                MDC.clear();
            }
        }
    }
}
