// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.resource;

import com.ifielsc.ijob.contract.enums.ResourceType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResourceListDTO {
    private Long id;
    private String code;
    private String name;
    private ResourceType type;
    private Boolean enabled;
    private String description;
    private LocalDateTime updateTime;
}
