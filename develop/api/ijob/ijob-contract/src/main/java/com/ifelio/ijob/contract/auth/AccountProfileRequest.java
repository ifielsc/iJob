package com.ifelio.ijob.contract.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountProfileRequest {
    private Long expectedRevision;
    private String displayName;
}
