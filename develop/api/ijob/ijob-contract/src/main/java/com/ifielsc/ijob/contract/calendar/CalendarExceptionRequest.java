// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.calendar;

import com.ifielsc.ijob.contract.enums.CalendarDateType;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CalendarExceptionRequest {
    private LocalDate date;
    private CalendarDateType type;
}
