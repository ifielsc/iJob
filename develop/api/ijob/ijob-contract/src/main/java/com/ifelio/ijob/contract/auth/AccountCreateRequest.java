package com.ifelio.ijob.contract.auth;

import lombok.Getter;
import lombok.Setter;

/** Minimal input. Passwords deliberately have no generated toString or default value. */
@Getter
@Setter
public class AccountCreateRequest {
    private String username;
    private String password;
    private String displayName;
    private AccountRole roleCode = AccountRole.VIEWER;
    private Boolean enabled = true;
}
