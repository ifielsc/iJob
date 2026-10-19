// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.parameter;

import com.ifielsc.ijob.contract.common.BasePageQuery;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParameterTemplateQuery extends BasePageQuery {
    private String name;
}
