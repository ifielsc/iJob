// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.calendar;

import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
public class CalendarDetailDTO {
    private Long id;
    private String name;
    private Set<DayOfWeek> workdays;
    private String description;
    private List<CalendarExceptionDTO> exceptions;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
