package com.ifelio.ijob.core.resource.factory.file.client;

import lombok.Getter;

import java.io.IOException;
import java.io.Serial;

@Getter
public class FileClientException extends IOException {
    @Serial private static final long serialVersionUID = 1L;

    private final boolean retryable;

    public FileClientException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }
}
