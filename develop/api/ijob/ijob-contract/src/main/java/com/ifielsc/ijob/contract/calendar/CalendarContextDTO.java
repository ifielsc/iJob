// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.calendar;

import java.time.LocalDate;

/** Current date in the same business timezone used by calendar previews. */
public record CalendarContextDTO(LocalDate today, String timezone) {}
