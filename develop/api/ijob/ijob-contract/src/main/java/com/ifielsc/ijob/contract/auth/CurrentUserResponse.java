package com.ifielsc.ijob.contract.auth;

public class CurrentUserResponse {
    private final String loginId;
    private final String username;
    private final String displayName;
    private final AccountRole roleCode;
    private final java.util.Set<AccountPermission> permissions;
    private final boolean mustChangePassword;

    public CurrentUserResponse(String loginId, String username) {
        this(loginId, username, username, null, java.util.Set.of(), false);
    }

    public CurrentUserResponse(
            String loginId,
            String username,
            String displayName,
            AccountRole roleCode,
            java.util.Set<AccountPermission> permissions,
            boolean mustChangePassword) {
        this.loginId = loginId;
        this.username = username;
        this.displayName = displayName;
        this.roleCode = roleCode;
        this.permissions = java.util.Set.copyOf(permissions);
        this.mustChangePassword = mustChangePassword;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountRole getRoleCode() {
        return roleCode;
    }

    public java.util.Set<AccountPermission> getPermissions() {
        return permissions;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getUsername() {
        return username;
    }
}
