package com.ifielsc.ijob.common.exception;

import java.io.Serial;

public class StopException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    public StopException() {
        super("Execution stopped", null, false, false);
    }
}
