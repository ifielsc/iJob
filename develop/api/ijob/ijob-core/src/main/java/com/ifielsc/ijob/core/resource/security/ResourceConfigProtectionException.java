// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.security;

import java.io.Serial;

public class ResourceConfigProtectionException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    public ResourceConfigProtectionException(String message) {
        super(message);
    }

    public ResourceConfigProtectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
