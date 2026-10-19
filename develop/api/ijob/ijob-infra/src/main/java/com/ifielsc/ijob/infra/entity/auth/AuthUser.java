// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.auth;

import com.ifielsc.ijob.contract.auth.AccountRole;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

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
