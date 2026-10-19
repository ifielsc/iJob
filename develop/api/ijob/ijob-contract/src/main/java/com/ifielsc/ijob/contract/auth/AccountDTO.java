package com.ifielsc.ijob.contract.auth;

import java.time.LocalDateTime;

/**
 * Deliberately excludes password hash. Identifiers and revision are decimal strings on the wire.
 */
public record AccountDTO(
        String id,
        String loginId,
        String username,
        String displayName,
        AccountRole roleCode,
        boolean enabled,
        boolean mustChangePassword,
        String revision,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
