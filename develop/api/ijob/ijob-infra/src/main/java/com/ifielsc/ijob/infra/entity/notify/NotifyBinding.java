// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.notify;

import com.ifielsc.ijob.contract.enums.NotifyPolicy;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class NotifyBinding {
    @AutoId(IdNamespace.NOTIFY_BINDING)
    private Long id;

    private TargetType targetType;
    private Long targetId;
    private Long notifyConfigId;
    private NotifyPolicy notifyPolicy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
