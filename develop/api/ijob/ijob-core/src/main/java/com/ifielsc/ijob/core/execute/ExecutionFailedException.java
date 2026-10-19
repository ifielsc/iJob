// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

/** Execution failure with a stable, non-sensitive boundary message. */
public final class ExecutionFailedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExecutionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
