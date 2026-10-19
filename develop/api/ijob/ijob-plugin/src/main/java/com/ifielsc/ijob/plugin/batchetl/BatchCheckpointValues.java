// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.batchetl;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;

/** Keeps JDBC temporal checkpoints lossless across JSON persistence and query rebinding. */
final class BatchCheckpointValues {
    private BatchCheckpointValues() {}

    static Object stored(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toString();
        if (value instanceof Date date) return date.toLocalDate().toString();
        if (value instanceof Time time) return time.toLocalTime().toString();
        if (value instanceof java.time.temporal.TemporalAccessor) return value.toString();
        return value;
    }

    static Object jdbc(Object value, int type) {
        if (value == null) throw new IllegalArgumentException("Batch ETL checkpoint value is null");
        try {
            return switch (type) {
                case Types.DATE ->
                        value instanceof Number number
                                ? new Date(number.longValue())
                                : Date.valueOf(value.toString());
                case Types.TIMESTAMP ->
                        value instanceof Number number
                                ? new Timestamp(number.longValue())
                                : Timestamp.valueOf(
                                        LocalDateTime.parse(value.toString().replace(' ', 'T')));
                case Types.TIMESTAMP_WITH_TIMEZONE -> OffsetDateTime.parse(value.toString());
                case Types.TIME -> Time.valueOf(value.toString());
                case Types.TIME_WITH_TIMEZONE -> OffsetTime.parse(value.toString());
                case Types.NUMERIC, Types.DECIMAL -> new BigDecimal(value.toString());
                default -> value;
            };
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Batch ETL checkpoint value does not match the source column type");
        }
    }
}
