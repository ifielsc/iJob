package com.ifielsc.ijob.core.security;

import java.io.Serial;

public class ConfigProtectionException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    private final Reason reason;

    public ConfigProtectionException(Reason reason) {
        super("configuration protection failed: " + reason.name());
        this.reason = reason;
    }

    public ConfigProtectionException(Reason reason, Throwable cause) {
        super("configuration protection failed: " + reason.name(), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        PLAINTEXT_REQUIRED,
        INVALID_CONFIGURATION,
        INVALID_ENVELOPE,
        KEY_UNAVAILABLE,
        ENCRYPTION_FAILED,
        DECRYPTION_FAILED
    }
}
