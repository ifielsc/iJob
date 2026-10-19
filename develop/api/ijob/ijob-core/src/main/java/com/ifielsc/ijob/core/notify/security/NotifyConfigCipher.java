package com.ifielsc.ijob.core.notify.security;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.core.security.AesGcmConfigProtector;
import com.ifielsc.ijob.core.security.ConfigProtectionException;

import org.springframework.stereotype.Component;

@Component
public class NotifyConfigCipher {
    private static final String AAD_PURPOSE = "ifj_notify_config.config:v1";

    private final AesGcmConfigProtector protector;

    public NotifyConfigCipher(AppProperties properties) {
        this.protector = new AesGcmConfigProtector(properties, AAD_PURPOSE);
    }

    public String encrypt(String plaintext) {
        try {
            return protector.encrypt(plaintext);
        } catch (ConfigProtectionException e) {
            throw translate(e);
        }
    }

    public String decrypt(String protectedValue) {
        try {
            return protector.decrypt(protectedValue);
        } catch (ConfigProtectionException e) {
            throw translate(e);
        }
    }

    public boolean isProtected(String value) {
        return protector.isProtected(value);
    }

    private NotifyConfigProtectionException translate(ConfigProtectionException exception) {
        String message =
                switch (exception.getReason()) {
                    case PLAINTEXT_REQUIRED -> "Notification configuration plaintext is required";
                    case INVALID_CONFIGURATION ->
                            "Notification configuration encryption is not configured correctly";
                    case INVALID_ENVELOPE -> "Notification configuration envelope is invalid";
                    case KEY_UNAVAILABLE -> "Notification configuration key is unavailable";
                    case ENCRYPTION_FAILED -> "Notification configuration encryption failed";
                    case DECRYPTION_FAILED -> "Notification configuration decryption failed";
                };
        return new NotifyConfigProtectionException(message, exception);
    }
}
