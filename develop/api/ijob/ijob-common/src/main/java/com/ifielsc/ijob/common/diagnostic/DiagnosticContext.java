// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.common.diagnostic;

import org.slf4j.MDC;

import java.util.Map;

/** Scoped MDC, including thread reuse and nested/direct execution. */
public final class DiagnosticContext implements AutoCloseable {
    private final Map<String, String> previous = MDC.getCopyOfContextMap();

    public DiagnosticContext(Map<String, String> values) {
        MDC.clear();
        if (values != null) MDC.setContextMap(values);
    }

    public static Runnable wrap(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            try (var ignored = new DiagnosticContext(captured)) {
                task.run();
            }
        };
    }

    @Override
    public void close() {
        MDC.clear();
        if (previous != null) MDC.setContextMap(previous);
    }
}
