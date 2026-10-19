package com.ifielsc.ijob.api.auth;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;

import com.ifielsc.ijob.contract.auth.CurrentUserResponse;
import com.ifielsc.ijob.contract.auth.LoginResponse;
import com.ifielsc.ijob.core.auth.AuthSession;

import org.springframework.stereotype.Component;

@Component
public class SaTokenAuthSession implements AuthSession {
    private static final String SESSION_USERNAME = "username";
    private static final String SECURITY_VERSION = "ijob-security-version";

    @Override
    public LoginResponse loginAccount(String loginId, String username, long securityVersion) {
        StpUtil.login(loginId);
        StpUtil.getTokenSession().set(SESSION_USERNAME, username);
        StpUtil.getTokenSession().set(SECURITY_VERSION, securityVersion);
        SaTokenInfo info = StpUtil.getTokenInfo();
        return new LoginResponse(info.getTokenName(), info.getTokenValue());
    }

    @Override
    public Long securityVersion() {
        Object value = StpUtil.getTokenSession().get(SECURITY_VERSION);
        return value instanceof Number number ? number.longValue() : null;
    }

    @Override
    public void revoke(String loginId) {
        StpUtil.logout(loginId);
    }

    @Override
    public LoginResponse login(String username) {
        StpUtil.login(username);
        StpUtil.getSession().set(SESSION_USERNAME, username);
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return new LoginResponse(tokenInfo.getTokenName(), tokenInfo.getTokenValue());
    }

    @Override
    public CurrentUserResponse currentUser() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        Object storedUsername = StpUtil.getSession().get(SESSION_USERNAME);
        String username = storedUsername instanceof String value ? value : loginId;
        return new CurrentUserResponse(loginId, username);
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }
}
