// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.security;

import java.io.Serial;

public class NotifyConfigProtectionException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    public NotifyConfigProtectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
