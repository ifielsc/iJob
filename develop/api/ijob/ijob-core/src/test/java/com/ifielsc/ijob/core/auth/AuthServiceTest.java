package com.ifielsc.ijob.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.auth.CurrentUserResponse;
import com.ifielsc.ijob.contract.auth.LoginRequest;
import com.ifielsc.ijob.contract.auth.LoginResponse;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    @Test
    void oversizedCredentialsAreRejectedBeforePasswordHashing() {
        AppProperties properties = new AppProperties();
        properties.getAuth().setMode(AppProperties.AuthMode.STATIC);
        properties.getAuth().setUsername(USERNAME);
        properties.getAuth().setPasswordHash(PASSWORD_HASH);
        java.util.concurrent.atomic.AtomicInteger calls =
                new java.util.concurrent.atomic.AtomicInteger();
        PasswordEncoder encoder =
                new PasswordEncoder() {
                    public String encode(CharSequence value) {
                        throw new UnsupportedOperationException();
                    }

                    public boolean matches(CharSequence value, String hash) {
                        calls.incrementAndGet();
                        return false;
                    }
                };
        var service = new AuthService(properties, encoder, new FakeAuthSession());
        assertThrows(
                BusinessException.class,
                () -> service.login(new LoginRequest("x".repeat(129), "x")));
        assertThrows(
                BusinessException.class,
                () -> service.login(new LoginRequest(USERNAME, "密".repeat(25))));
        assertEquals(0, calls.get());
        assertThrows(
                BusinessException.class,
                () -> service.login(new LoginRequest("nonexistent", "wrong")));
        assertThrows(
                BusinessException.class, () -> service.login(new LoginRequest(USERNAME, "wrong")));
        assertEquals(2, calls.get());
    }

    private static final String USERNAME = "test-user";
    private static final String PASSWORD = "test-password";
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(4);
    private static final String PASSWORD_HASH = PASSWORD_ENCODER.encode(PASSWORD);

    @Test
    void disabledAuthenticationShouldRejectLogin() {
        Fixture fixture = fixture(false, USERNAME, PASSWORD_HASH);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> fixture.service.login(new LoginRequest(USERNAME, PASSWORD)));

        assertEquals(ResultCode.SERVICE_UNAVAILABLE, exception.getCode());
        assertFalse(fixture.session.loginCalled);
    }

    @Test
    void blankCredentialsShouldReturnParameterError() {
        Fixture fixture = fixture(true, USERNAME, PASSWORD_HASH);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> fixture.service.login(new LoginRequest(USERNAME, " ")));

        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
        assertFalse(fixture.session.loginCalled);
    }

    @Test
    void incompleteBootstrapConfigurationShouldRejectLogin() {
        Fixture fixture = fixture(true, USERNAME, "not-a-bcrypt-hash");

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> fixture.service.login(new LoginRequest(USERNAME, PASSWORD)));

        assertEquals(ResultCode.SERVICE_UNAVAILABLE, exception.getCode());
        assertFalse(fixture.session.loginCalled);
    }

    @Test
    void invalidCredentialsShouldReturnUnauthorized() {
        Fixture fixture = fixture(true, USERNAME, PASSWORD_HASH);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                fixture.service.login(
                                        new LoginRequest("wrong-user", "wrong-password")));

        assertEquals(ResultCode.UNAUTHORIZED, exception.getCode());
        assertFalse(fixture.session.loginCalled);
    }

    @Test
    void validCredentialsShouldCreateSession() {
        Fixture fixture = fixture(true, USERNAME, PASSWORD_HASH);

        LoginResponse response = fixture.service.login(new LoginRequest(USERNAME, PASSWORD));

        assertSame(fixture.session.loginResponse, response);
        assertEquals(USERNAME, fixture.session.loggedInUsername);
    }

    @Test
    void currentUserAndLogoutShouldDelegateToSessionBoundary() {
        Fixture fixture = fixture(true, USERNAME, PASSWORD_HASH);

        CurrentUserResponse currentUser = fixture.service.currentUser();
        fixture.service.logout();

        assertSame(fixture.session.currentUserResponse, currentUser);
        assertEquals(1, fixture.session.logoutCalls);
    }

    private Fixture fixture(boolean enabled, String username, String passwordHash) {
        AppProperties properties = new AppProperties();
        properties
                .getAuth()
                .setMode(enabled ? AppProperties.AuthMode.STATIC : AppProperties.AuthMode.NONE);
        properties.getAuth().setUsername(username);
        properties.getAuth().setPasswordHash(passwordHash);
        FakeAuthSession session = new FakeAuthSession();
        AuthService service = new AuthService(properties, PASSWORD_ENCODER, session);
        return new Fixture(service, session);
    }

    private record Fixture(AuthService service, FakeAuthSession session) {}

    private static final class FakeAuthSession implements AuthSession {
        private final LoginResponse loginResponse = new LoginResponse("ijob-token", "test-token");
        private final CurrentUserResponse currentUserResponse =
                new CurrentUserResponse(USERNAME, USERNAME);
        private boolean loginCalled;
        private String loggedInUsername;
        private int logoutCalls;

        @Override
        public LoginResponse login(String username) {
            loginCalled = true;
            loggedInUsername = username;
            return loginResponse;
        }

        @Override
        public CurrentUserResponse currentUser() {
            return currentUserResponse;
        }

        @Override
        public void logout() {
            logoutCalls++;
        }
    }
}
