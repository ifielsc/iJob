package com.ifielsc.ijob.core.execute;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.sql.SQLTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public final class TimeoutExceptionClassifier {
    private TimeoutExceptionClassifier() {}

    public static boolean isTimeout(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && visited.add(current)) {
            if (current instanceof ExecutionTimeoutException
                    || current instanceof SQLTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof TimeoutException
                    // OkHttp total call deadlines use this exact exception/message pair.
                    || (current instanceof InterruptedIOException
                            && "timeout".equals(current.getMessage()))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
