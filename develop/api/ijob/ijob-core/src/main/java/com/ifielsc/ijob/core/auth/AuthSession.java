package com.ifielsc.ijob.core.auth;

import com.ifielsc.ijob.contract.auth.CurrentUserResponse;
import com.ifielsc.ijob.contract.auth.LoginResponse;

public interface AuthSession {
    LoginResponse login(String username);

    CurrentUserResponse currentUser();

    void logout();

    default LoginResponse loginAccount(String loginId, String username, long securityVersion) {
        throw new UnsupportedOperationException("Versioned sessions are required");
    }

    default Long securityVersion() {
        return null;
    }

    default void revoke(String loginId) {
        throw new UnsupportedOperationException("Shared session revocation is required");
    }
}
