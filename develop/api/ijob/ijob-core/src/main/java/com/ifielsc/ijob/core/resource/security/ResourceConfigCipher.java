// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.security;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.core.security.AesGcmConfigProtector;
import com.ifielsc.ijob.core.security.ConfigProtectionException;

import org.springframework.stereotype.Component;

@Component
public class ResourceConfigCipher {
    private static final String AAD_PURPOSE = "ifj_resource.config:v1";

    private final AesGcmConfigProtector protector;

    public ResourceConfigCipher(AppProperties properties) {
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

    private ResourceConfigProtectionException translate(ConfigProtectionException exception) {
        String message =
                switch (exception.getReason()) {
                    case PLAINTEXT_REQUIRED -> "Resource configuration plaintext is required";
                    case INVALID_CONFIGURATION ->
                            "Resource configuration encryption is not configured correctly";
                    case INVALID_ENVELOPE -> "Resource configuration envelope is invalid";
                    case KEY_UNAVAILABLE -> "Resource configuration key is unavailable";
                    case ENCRYPTION_FAILED -> "Resource configuration encryption failed";
                    case DECRYPTION_FAILED -> "Resource configuration decryption failed";
                };
        return new ResourceConfigProtectionException(message, exception);
    }
}
