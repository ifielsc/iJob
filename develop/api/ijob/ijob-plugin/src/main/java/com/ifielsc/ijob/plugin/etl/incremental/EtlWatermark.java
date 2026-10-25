// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.incremental;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

/** Shared exact comparisons; file values are stored as canonical strings. */
public final class EtlWatermark {
    private EtlWatermark() {}

    public static int compare(Object left, Object right, String type) {
        if (right == null) return left == null ? 0 : 1;
        if (left == null) throw new IllegalArgumentException("ETL watermark is null");
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "LONG", "DECIMAL" ->
                    new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
            case "DATE" -> date(left).compareTo(date(right));
            case "TIMESTAMP" -> {
                Instant leftInstant = timestampInstant(left);
                Instant rightInstant = timestampInstant(right);
                if (leftInstant != null && rightInstant != null)
                    yield leftInstant.compareTo(rightInstant);
                yield dateTime(left).compareTo(dateTime(right));
            }
            case "STRING" -> left.toString().compareTo(right.toString());
            default -> throw new IllegalArgumentException("ETL watermark type is unsupported");
        };
    }

    public static String normalize(Object raw, String type, String format, String zone) {
        if (raw == null || raw.toString().isEmpty() || raw.toString().length() > 1024)
            throw new IllegalArgumentException("ETL file marker is invalid");
        String value = raw.toString();
        DateTimeFormatter formatter = format == null ? null : formatter(format);
        try {
            return switch (type.toUpperCase(Locale.ROOT)) {
                case "LONG" -> {
                    if (!value.matches("-?[0-9]+")) throw new IllegalArgumentException();
                    yield Long.toString(Long.parseLong(value));
                }
                case "DECIMAL" -> {
                    if (!value.matches("-?[0-9]+(?:\\.[0-9]+)?"))
                        throw new IllegalArgumentException();
                    yield new BigDecimal(value).stripTrailingZeros().toPlainString();
                }
                case "DATE" ->
                        LocalDate.parse(
                                        value,
                                        formatter == null
                                                ? DateTimeFormatter.ISO_LOCAL_DATE
                                                : formatter)
                                .toString();
                case "TIMESTAMP" -> {
                    if (formatter == null) {
                        try {
                            yield Instant.parse(value).toString();
                        } catch (java.time.DateTimeException ignored) {
                            /* Local values use the configured zone. */
                        }
                        formatter = DateTimeFormatter.ISO_DATE_TIME;
                    }
                    var parsed = formatter.parse(value);
                    try {
                        yield OffsetDateTime.from(parsed).toInstant().toString();
                    } catch (java.time.DateTimeException ignored) {
                        /* Offset-free values must be unambiguous. */
                    }
                    LocalDateTime local = LocalDateTime.from(parsed);
                    var offsets = ZoneId.of(zone).getRules().getValidOffsets(local);
                    if (offsets.size() != 1) throw new IllegalArgumentException();
                    yield local.toInstant(offsets.getFirst()).toString();
                }
                case "STRING" -> value;
                default -> throw new IllegalArgumentException();
            };
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("ETL file marker does not match its type or format");
        }
    }

    public static String subtract(String watermark, String amount, String type) {
        try {
            return switch (type.toUpperCase(Locale.ROOT)) {
                case "LONG" -> {
                    long n = Long.parseLong(amount);
                    if (n <= 0) throw new IllegalArgumentException();
                    yield Long.toString(Math.subtractExact(Long.parseLong(watermark), n));
                }
                case "DECIMAL" -> {
                    BigDecimal n = new BigDecimal(amount);
                    if (n.signum() <= 0) throw new IllegalArgumentException();
                    yield new BigDecimal(watermark).subtract(n).toPlainString();
                }
                case "DATE" -> {
                    Period p = Period.parse(amount);
                    if (p.isNegative() || p.isZero()) throw new IllegalArgumentException();
                    yield LocalDate.parse(watermark).minus(p).toString();
                }
                case "TIMESTAMP" -> {
                    Duration d = Duration.parse(amount);
                    if (d.isNegative() || d.isZero()) throw new IllegalArgumentException();
                    yield Instant.parse(watermark).minus(d).toString();
                }
                default -> throw new IllegalArgumentException();
            };
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("ETL file lookback is invalid for the marker type");
        }
    }

    public static DateTimeFormatter formatter(String format) {
        return DateTimeFormatter.ofPattern(format.replace("yyyy", "uuuu"), Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    private static LocalDate date(Object value) {
        return value instanceof java.sql.Date d
                ? d.toLocalDate()
                : LocalDate.parse(value.toString());
    }

    private static LocalDateTime dateTime(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        Instant instant = timestampInstant(value);
        // Earlier checkpoints serialized JDBC timestamps with the JSON mapper's offset.
        // Recover the JDBC wall time in the same JVM zone used by Timestamp.valueOf/getTimestamp.
        return instant == null
                ? LocalDateTime.parse(value.toString())
                : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static Instant timestampInstant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        try {
            return OffsetDateTime.parse(value.toString()).toInstant();
        } catch (java.time.format.DateTimeParseException ignored) {
            return null;
        }
    }
}
