package com.ifelio.ijob.common.diagnostic;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Cause types and code locations only: never exception messages, SQL, URLs or payloads. */
public final class SafeDiagnostic {
    private SafeDiagnostic() {}

    public static RuntimeException sanitized(Throwable failure) {
        // Do not retain the original cause or suppressed exceptions: appenders render them.
        return new RuntimeException(describe(failure));
    }

    public static String describe(Throwable failure) {
        StringBuilder result = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; failure != null && depth < 8 && seen.add(failure); depth++) {
            if (depth > 0) result.append(" <- ");
            result.append(failure.getClass().getName());
            StackTraceElement[] frames = failure.getStackTrace();
            for (int index = 0; index < Math.min(frames.length, 4); index++) {
                StackTraceElement frame = frames[index];
                result.append(" at ")
                        .append(symbol(frame.getClassName()))
                        .append('.')
                        .append(symbol(frame.getMethodName()))
                        .append(':')
                        .append(frame.getLineNumber());
            }
            result.append(" suppressed=").append(failure.getSuppressed().length);
            failure = failure.getCause();
        }
        return result.toString();
    }

    private static String symbol(String value) {
        return value.substring(0, Math.min(value.length(), 180))
                .replaceAll("[^a-zA-Z0-9_.$<>]", "_");
    }
}
