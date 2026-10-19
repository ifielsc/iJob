// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.calendar;

import com.ifielsc.ijob.contract.common.BasePageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CalendarPageQuery extends BasePageQuery {
    private String name;
}
