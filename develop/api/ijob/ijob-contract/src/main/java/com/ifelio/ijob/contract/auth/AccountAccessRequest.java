package com.ifelio.ijob.contract.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountAccessRequest {
    private Long expectedRevision;
    private AccountRole roleCode;
    private Boolean enabled;
}
