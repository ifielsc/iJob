package com.ifelio.ijob.core.auth;

import com.ifelio.ijob.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;

public final class AccountRules {
    private AccountRules() {}

    public static String username(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.@-]{2,63}")) {
            throw new BusinessException(SecurityError.INPUT);
        }
        return value;
    }

    public static String displayName(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        if (value.length() > 128 || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(SecurityError.INPUT);
        }
        return value.trim();
    }

    public static void password(String value) {
        if (value == null
                || value.length() < 12
                || value.length() > 72
                || value.getBytes(StandardCharsets.UTF_8).length > 72
                || value.isBlank()
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new BusinessException(SecurityError.PASSWORD);
        }
    }

    public static long revision(Long value) {
        if (value == null || value < 0) throw new BusinessException(SecurityError.INPUT);
        return value;
    }
}
