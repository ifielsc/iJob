// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.notify;

import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MailChannel {
    @AutoId(IdNamespace.MAIL_CHANNEL)
    private Long id;

    private String name;
    private String config;
    private Boolean enabled;
    private Long revision;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
