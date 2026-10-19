package com.ifielsc.ijob.core.execute;

public class ExecutionTimeoutException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExecutionTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
