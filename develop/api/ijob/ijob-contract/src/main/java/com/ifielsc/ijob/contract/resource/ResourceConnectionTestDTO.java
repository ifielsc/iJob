// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.resource;

import com.ifielsc.ijob.contract.enums.ResourceType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceConnectionTestDTO {
    private Long resourceId;
    private ResourceType type;
    private boolean successful;
}
