package com.ifielsc.ijob.contract.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetRequest {
    private Long expectedRevision;
    private String newPassword;
}
