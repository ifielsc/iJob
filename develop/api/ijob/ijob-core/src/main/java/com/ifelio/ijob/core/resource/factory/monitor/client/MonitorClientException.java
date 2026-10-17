package com.ifelio.ijob.core.resource.factory.monitor.client;

public class MonitorClientException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final boolean retryable;

    public MonitorClientException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
