// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.calendar;

import lombok.Data;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

@Data
public class CalendarUpsertRequest {
    private String name;
    private Set<DayOfWeek> workdays;
    private String description;
    private List<CalendarExceptionRequest> exceptions;
}
