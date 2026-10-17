package com.ifelio.ijob.infra.entity.auth;

import com.ifelio.ijob.contract.auth.AccountRole;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Persistence-only object. Never serialize into an API response or a diagnostic log. */
@Getter
@Setter
public class AuthUser {
    @AutoId(IdNamespace.AUTH_USER)
    private Long id;

    private String loginId;
    private String username;
    private String displayName;
    private String passwordHash;
    private AccountRole roleCode;
    private Boolean enabled;
    private Boolean mustChangePassword;
    private Long securityVersion;
    private Long revision;
    private String createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
