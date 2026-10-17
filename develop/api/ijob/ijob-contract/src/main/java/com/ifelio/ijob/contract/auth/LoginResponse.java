package com.ifelio.ijob.contract.auth;

public class LoginResponse {
    private final String tokenName;
    private final String tokenValue;

    public LoginResponse(String tokenName, String tokenValue) {
        this.tokenName = tokenName;
        this.tokenValue = tokenValue;
    }

    public String getTokenName() {
        return tokenName;
    }

    public String getTokenValue() {
        return tokenValue;
    }
}
