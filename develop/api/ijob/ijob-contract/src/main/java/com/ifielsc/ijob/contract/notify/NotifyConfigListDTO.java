// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.notify;

import com.ifielsc.ijob.contract.enums.NotifyType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotifyConfigListDTO {
    private Long id;
    private String name;
    private NotifyType type;
    private Boolean enabled;
    private LocalDateTime updateTime;
}
