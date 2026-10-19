// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.resource;

import com.ifielsc.ijob.contract.common.BasePageQuery;
import com.ifielsc.ijob.contract.enums.ResourceType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ResourcePageQuery extends BasePageQuery {
    private String code;
    private String name;
    private ResourceType type;
    private Boolean enabled;
}
