// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;

import org.junit.jupiter.api.Test;

class AccountRulesTest {
    @Test
    void requiredFieldsAreMinimalAndIdentityRulesAreExplicit() {
        assertEquals("valid.user-1", AccountRules.username("valid.user-1"));
        assertEquals("user", AccountRules.displayName(null, "user"));
        assertThrows(BusinessException.class, () -> AccountRules.username("user:reserved"));
        assertThrows(BusinessException.class, () -> AccountRules.username("管理员"));
        assertThrows(
                BusinessException.class, () -> AccountRules.displayName("injected\nline", "user"));
    }

    @Test
    void passwordsAreNeverSilentlyTruncatedAtBcryptByteBoundary() {
        AccountRules.password("密".repeat(24));
        assertThrows(BusinessException.class, () -> AccountRules.password("密".repeat(25)));
        assertThrows(BusinessException.class, () -> AccountRules.password("short"));
        assertThrows(BusinessException.class, () -> AccountRules.password(" ".repeat(16)));
    }
}
