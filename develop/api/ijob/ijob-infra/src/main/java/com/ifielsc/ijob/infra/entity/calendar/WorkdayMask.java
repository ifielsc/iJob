// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.calendar;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class WorkdayMask {
    private WorkdayMask() {}

    public static final int MONDAY = 1;
    public static final int TUESDAY = 2;
    public static final int WEDNESDAY = 4;
    public static final int THURSDAY = 8;
    public static final int FRIDAY = 16;
    public static final int SATURDAY = 32;
    public static final int SUNDAY = 64;
    public static final int ALL_DAYS =
            MONDAY | TUESDAY | WEDNESDAY | THURSDAY | FRIDAY | SATURDAY | SUNDAY;

    public static int bit(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> MONDAY;
            case TUESDAY -> TUESDAY;
            case WEDNESDAY -> WEDNESDAY;
            case THURSDAY -> THURSDAY;
            case FRIDAY -> FRIDAY;
            case SATURDAY -> SATURDAY;
            case SUNDAY -> SUNDAY;
        };
    }

    public static boolean contains(int mask, DayOfWeek day) {
        return (mask & bit(day)) != 0;
    }

    public static int fromDays(Set<DayOfWeek> days) {
        int mask = 0;
        for (DayOfWeek day : days) {
            mask |= bit(day);
        }
        return mask;
    }

    public static Set<DayOfWeek> toDays(Integer mask) {
        if (!isValid(mask)) {
            return Set.of();
        }
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (contains(mask, day)) {
                days.add(day);
            }
        }
        return Collections.unmodifiableSet(days);
    }

    public static boolean isValid(Integer mask) {
        return mask != null && mask > 0 && (mask & ~ALL_DAYS) == 0;
    }
}
