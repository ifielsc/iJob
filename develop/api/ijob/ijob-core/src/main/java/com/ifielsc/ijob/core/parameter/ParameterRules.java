// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.parameter;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.parameter.ParameterField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates editor input; values are never filled from a live template during scheduled execution.
 */
public final class ParameterRules {
    private static final Set<String> TYPES =
            Set.of(
                    "STRING",
                    "INTEGER",
                    "DECIMAL",
                    "BOOLEAN",
                    "DATE",
                    "DATETIME",
                    "ENUM",
                    "OBJECT",
                    "ARRAY");

    private ParameterRules() {}

    public static void definitions(List<ParameterField> fields) {
        if (fields == null) return;
        if (fields.size() > 100) throw invalid("fields");
        Set<String> names = new HashSet<>();
        for (ParameterField f : fields) {
            if (f == null
                    || f.getName() == null
                    || f.getName().isBlank()
                    || f.getName().length() > 128
                    || f.getName().startsWith("ijob.")
                    || !names.add(f.getName())) throw invalid("name");
            if (f.getType() == null || !TYPES.contains(f.getType())) throw invalid(f.getName());
            if (f.getDescription() != null && f.getDescription().length() > 500)
                throw invalid(f.getName());
            if (f.getMaxLength() != null && (f.getMaxLength() < 0 || f.getMaxLength() > 65536))
                throw invalid(f.getName());
            if (f.getMinimum() != null
                    && f.getMaximum() != null
                    && f.getMinimum().compareTo(f.getMaximum()) > 0) throw invalid(f.getName());
            if ("ENUM".equals(f.getType()) && (f.getOptions() == null || f.getOptions().isEmpty()))
                throw invalid(f.getName());
            if (f.getOptions() != null
                    && (f.getOptions().size() > 100
                            || f.getOptions().stream().anyMatch(x -> x == null || x.length() > 500)
                            || new HashSet<>(f.getOptions()).size() != f.getOptions().size()))
                throw invalid(f.getName());
            if (Boolean.TRUE.equals(f.getSensitive()) && Boolean.TRUE.equals(f.getHasDefault()))
                throw invalid(f.getName());
            if (Boolean.TRUE.equals(f.getHasDefault())) value(f, f.getDefaultValue());
            else f.setDefaultValue(null);
        }
    }

    public static void values(List<ParameterField> fields, Map<String, Object> values) {
        definitions(fields);
        if (fields == null) return;
        Map<String, Object> resolved = values == null ? Map.of() : values;
        Set<String> names = new HashSet<>();
        for (ParameterField f : fields) {
            names.add(f.getName());
            Object value = resolved.get(f.getName());
            if (value == null) {
                if (Boolean.TRUE.equals(f.getRequired())) throw invalid(f.getName());
            } else value(f, value);
        }
        for (String key : resolved.keySet())
            if (!names.contains(key)) throw invalid("undeclared parameter");
    }

    private static void value(ParameterField f, Object v) {
        if (v == null) throw invalid(f.getName());
        boolean valid =
                switch (f.getType()) {
                    case "STRING", "ENUM", "DATE", "DATETIME" -> v instanceof String;
                    case "INTEGER", "DECIMAL" -> v instanceof Number;
                    case "BOOLEAN" -> v instanceof Boolean;
                    case "OBJECT" -> v instanceof Map;
                    case "ARRAY" -> v instanceof List;
                    default -> false;
                };
        if (!valid) throw invalid(f.getName());
        try {
            if (v instanceof String text) {
                if (Boolean.TRUE.equals(f.getRequired()) && text.isEmpty())
                    throw invalid(f.getName());
                if (f.getMaxLength() != null && text.length() > f.getMaxLength())
                    throw invalid(f.getName());
                if ("DATE".equals(f.getType())) LocalDate.parse(text);
                if ("DATETIME".equals(f.getType())) LocalDateTime.parse(text);
                if ("ENUM".equals(f.getType()) && !f.getOptions().contains(text))
                    throw invalid(f.getName());
            }
            if (v instanceof Number) {
                BigDecimal n = new BigDecimal(v.toString());
                if ("INTEGER".equals(f.getType())) n.toBigIntegerExact();
                if (f.getMinimum() != null && n.compareTo(f.getMinimum()) < 0
                        || f.getMaximum() != null && n.compareTo(f.getMaximum()) > 0)
                    throw invalid(f.getName());
            }
        } catch (RuntimeException e) {
            throw invalid(f.getName());
        }
    }

    public static BusinessException invalid(String field) {
        return new BusinessException(ResultCode.PARAM_ERROR, ParameterMessage.INVALID, field);
    }
}
