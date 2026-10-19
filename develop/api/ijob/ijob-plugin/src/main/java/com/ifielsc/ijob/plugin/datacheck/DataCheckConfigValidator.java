package com.ifielsc.ijob.plugin.datacheck;

import com.ifielsc.ijob.plugin.datacheck.config.CheckType;
import com.ifielsc.ijob.plugin.datacheck.config.CompareType;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckConfig;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifielsc.ijob.plugin.datacheck.config.HandlerConfig;
import com.ifielsc.ijob.plugin.support.sql.ReadOnlySqlGuard;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class DataCheckConfigValidator {
    private static final int MAX_CHECKS = 100;
    private static final int MAX_HANDLERS = 8;
    private static final int MAX_SQL_LENGTH = 1_000_000;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_VARIABLE_LENGTH = 128;
    private static final int MAX_QUERY_TIMEOUT_SECONDS = 3_600;

    public void validate(DataCheckConfig config) {
        if (config == null) {
            throw invalid("data check config is required");
        }
        if (config.getResourceId() == null || config.getResourceId() <= 0) {
            throw invalid("data check resourceId must be positive");
        }
        Integer timeout = config.getQueryTimeoutSeconds();
        if (timeout == null || timeout <= 0 || timeout > MAX_QUERY_TIMEOUT_SECONDS) {
            throw invalid("data check queryTimeoutSeconds must be between 1 and 3600");
        }
        if (config.getChecks() == null
                || config.getChecks().isEmpty()
                || config.getChecks().size() > MAX_CHECKS) {
            throw invalid("data check checks must contain between 1 and 100 items");
        }
        Set<String> names = new HashSet<>();
        Set<String> variables = new HashSet<>();
        for (DataCheckItemConfig item : config.getChecks()) {
            validateItem(item, names, variables);
        }
        validateHandlers(config);
    }

    private void validateItem(DataCheckItemConfig item, Set<String> names, Set<String> variables) {
        if (item == null) {
            throw invalid("data check item is required");
        }
        String name =
                normalized(item.getName(), MAX_NAME_LENGTH, "data check item name is invalid");
        if (containsTemplate(name)) {
            throw invalid("data check item name is invalid");
        }
        if (!names.add(name)) {
            throw invalid("data check item names must be unique");
        }
        validateReadQuery(item.getSql());
        if (item.getType() == null || item.getLevel() == null) {
            throw invalid("data check item type and level are required");
        }
        if (item.getVariable() != null) {
            String variable =
                    normalized(
                            item.getVariable(),
                            MAX_VARIABLE_LENGTH,
                            "data check item variable is invalid");
            if (containsTemplate(variable)) {
                throw invalid("data check item variable is invalid");
            }
            if (!variables.add(variable)) {
                throw invalid("data check item variables must be unique");
            }
        }
        validateComparison(item);
    }

    public void validateReadQuery(String sql) {
        if (sql == null
                || sql.isBlank()
                || sql.length() > MAX_SQL_LENGTH
                || sql.indexOf('\0') >= 0) {
            throw invalid("data check SQL is invalid");
        }
        if (!ReadOnlySqlGuard.isReadQuery(sql)) {
            throw invalid("data check SQL must be a read query");
        }
    }

    private void validateComparison(DataCheckItemConfig item) {
        CheckType type = item.getType();
        CompareType compare = item.getCompareType();
        if (type == CheckType.EMPTY) {
            if (compare != null
                    || item.getExpected() != null
                    || item.getMinValue() != null
                    || item.getMaxValue() != null) {
                throw invalid("EMPTY check cannot define comparison values");
            }
            return;
        }
        if (type == CheckType.EXISTS) {
            if (compare != null && compare != CompareType.EQ && compare != CompareType.NE) {
                throw invalid("EXISTS check supports only EQ or NE");
            }
            if (item.getExpected() != null && !(item.getExpected() instanceof Boolean)) {
                throw invalid("EXISTS expected must be boolean");
            }
            requireNoRange(item);
            return;
        }
        CompareType effective = compare == null ? CompareType.EQ : compare;
        if (effective == CompareType.BETWEEN) {
            if (item.getMinValue() == null || item.getMaxValue() == null) {
                throw invalid("BETWEEN check requires minValue and maxValue");
            }
            if (type == CheckType.COUNT) {
                BigDecimal minimum = number(item.getMinValue());
                BigDecimal maximum = number(item.getMaxValue());
                if (minimum.compareTo(maximum) > 0) {
                    throw invalid("data check minValue cannot exceed maxValue");
                }
            }
            if (item.getExpected() != null) {
                throw invalid("BETWEEN check cannot define expected");
            }
        } else {
            if (item.getExpected() == null) {
                throw invalid("data check expected is required");
            }
            requireNoRange(item);
            if (type == CheckType.COUNT) {
                number(item.getExpected());
            }
        }
    }

    private void requireNoRange(DataCheckItemConfig item) {
        if (item.getMinValue() != null || item.getMaxValue() != null) {
            throw invalid("non-BETWEEN check cannot define range values");
        }
    }

    private BigDecimal number(Object value) {
        try {
            return new BigDecimal(value.toString());
        } catch (RuntimeException e) {
            throw invalid("COUNT comparison value must be numeric");
        }
    }

    private void validateHandlers(DataCheckConfig config) {
        if (config.getHandlers() == null) {
            throw invalid("data check handlers cannot be null");
        }
        if (config.getHandlers().size() > MAX_HANDLERS) {
            throw invalid("data check handlers cannot exceed 8");
        }
        Set<String> types = new HashSet<>();
        for (HandlerConfig handler : config.getHandlers()) {
            if (handler == null || handler.getType() == null || handler.getType().isBlank()) {
                throw invalid("data check handler type is required");
            }
            String type = handler.getType().trim().toUpperCase(Locale.ROOT);
            if (!handler.getType().equals(handler.getType().trim())
                    || (!"VARIABLE".equals(type) && !"NOTIFY".equals(type))) {
                throw invalid("data check handler type is not supported");
            }
            if (!types.add(type)) {
                throw invalid("data check handler types must be unique");
            }
            if ("NOTIFY".equals(type)) {
                validateNotify(handler.getParams());
            }
        }
    }

    private void validateNotify(Map<String, Object> params) {
        Object id = params == null ? null : params.get("notifyConfigId");
        try {
            if (!(id instanceof Number)
                    || id instanceof Float
                    || id instanceof Double
                    || new BigDecimal(id.toString()).longValueExact() <= 0) {
                throw invalid("data check notifyConfigId must be positive");
            }
        } catch (ArithmeticException exception) {
            throw invalid("data check notifyConfigId must be an exact positive long");
        }
        Object notifyOnSuccess = params.get("notifyOnSuccess");
        if (notifyOnSuccess != null && !(notifyOnSuccess instanceof Boolean)) {
            throw invalid("data check notifyOnSuccess must be boolean");
        }
    }

    private String normalized(String value, int maximum, String message) {
        if (value == null
                || value.isBlank()
                || value.length() > maximum
                || !value.equals(value.trim())) {
            throw invalid(message);
        }
        return value;
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private boolean containsTemplate(String value) {
        return value.contains("${");
    }
}
