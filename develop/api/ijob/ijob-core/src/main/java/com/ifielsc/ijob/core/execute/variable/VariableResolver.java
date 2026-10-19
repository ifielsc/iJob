// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.variable;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class VariableResolver {
    private static final Pattern PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern SCRIPT_PATTERN =
            Pattern.compile("\\$\\$\\{([^}]*)}|\\$\\{([^}]+)}");

    /** Matches a single placeholder occupying the entire string. */
    private static final Pattern SINGLE_PATTERN = Pattern.compile("^\\$\\{([^}]+)}$");

    /** Interpolates placeholders into text, for example {@code Bearer ${token}}. */
    public String resolve(String text, Map<String, Object> variables) {
        return resolve(text, variables, false);
    }

    /**
     * Resolves script placeholders while allowing native ${...} expressions to be escaped as
     * $${...}. The extra dollar sign is removed before the script is written.
     */
    public String resolveScript(String text, Map<String, Object> variables) {
        return resolve(text, variables, true);
    }

    private String resolve(String text, Map<String, Object> variables, boolean scriptEscapes) {
        if (text == null || text.isBlank()) {
            return text;
        }

        Matcher matcher = (scriptEscapes ? SCRIPT_PATTERN : PATTERN).matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String escapedExpression = scriptEscapes ? matcher.group(1) : null;
            String replacement;
            if (escapedExpression != null) {
                replacement = "${" + escapedExpression + "}";
            } else {
                String expression = scriptEscapes ? matcher.group(2) : matcher.group(1);
                VariableExpression variable = parse(expression);
                Object value = variables.get(variable.getName());
                replacement = resolveStringValue(matcher.group(), variable, value);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /** Recursively resolves strings, map values and list elements; other values pass through. */
    public Object resolveObject(Object value, Map<String, Object> variables) {
        if (value == null) {
            return null;
        }

        if (value instanceof String str) {
            return resolveValue(str, variables);
        }
        if (value instanceof Map<?, ?> map) {
            return resolveMap(map, variables);
        }
        if (value instanceof List<?> list) {
            return resolveList(list, variables);
        }
        return value;
    }

    private Map<String, Object> resolveMap(Map<?, ?> source, Map<String, Object> variables) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach(
                (key, value) -> result.put(String.valueOf(key), resolveObject(value, variables)));
        return result;
    }

    private List<Object> resolveList(List<?> source, Map<String, Object> variables) {
        return source.stream()
                .map(item -> resolveObject(item, variables))
                .collect(Collectors.toList());
    }

    /**
     * A standalone placeholder preserves the resolved value type; embedded placeholders produce
     * text.
     */
    private Object resolveValue(String text, Map<String, Object> variables) {
        Matcher single = SINGLE_PATTERN.matcher(text);
        if (single.matches()) {
            VariableExpression expression = parse(single.group(1));
            Object value = variables.get(expression.getName());
            if (value != null) {
                return value;
            }
            if (expression.getDefaultValue() != null) {
                return expression.getDefaultValue();
            }
            if (expression.isRequired()) {
                throw missing(expression.getName());
            }
            return text;
        }
        return resolve(text, variables);
    }

    private String resolveStringValue(
            String original, VariableExpression expression, Object value) {
        if (value != null) {
            return String.valueOf(value);
        }
        if (expression.getDefaultValue() != null) {
            return expression.getDefaultValue();
        }
        if (expression.isRequired()) {
            throw missing(expression.getName());
        }
        return original;
    }

    private VariableExpression parse(String expression) {
        VariableExpression result = new VariableExpression();
        /*
         * ${token!}
         */
        if (expression.endsWith("!")) {
            result.setRequired(true);
            expression = expression.substring(0, expression.length() - 1);
        }
        /*
         * ${token:default}
         */
        int index = expression.indexOf(":");
        if (index > 0) {
            result.setName(expression.substring(0, index));
            result.setDefaultValue(expression.substring(index + 1));
        } else {
            result.setName(expression);
        }
        return result;
    }

    private BusinessException missing(String name) {
        return BusinessException.localized(
                ResultCode.PARAM_ERROR, "required variable missing:" + name);
    }

    public Map<String, Object> resolveObjectMap(
            Map<String, Object> source, Map<String, Object> variables) {
        if (source == null) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, resolveObject(value, variables)));
        return result;
    }
}
