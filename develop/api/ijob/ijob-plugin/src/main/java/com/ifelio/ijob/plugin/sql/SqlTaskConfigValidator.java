package com.ifelio.ijob.plugin.sql;

import org.springframework.stereotype.Component;

@Component
public class SqlTaskConfigValidator {
    private static final int MAX_DELIMITER_LENGTH = 16;
    private static final int MAX_SCRIPT_LENGTH = 1_000_000;
    private static final int MAX_QUERY_TIMEOUT_SECONDS = 86400;
    private static final int MAX_VARIABLE_LENGTH = 128;

    public void validate(SqlTaskConfig config) {
        if (config == null) {
            throw invalid("SQL configuration is required");
        }
        if (config.getResourceId() == null || config.getResourceId() <= 0) {
            throw invalid("SQL resourceId must be positive");
        }
        if (config.getScript() == null || config.getScript().isBlank()) {
            throw invalid("SQL script is required");
        }
        if (config.getScript().length() > MAX_SCRIPT_LENGTH
                || config.getScript().indexOf('\0') >= 0) {
            throw invalid("SQL script is invalid");
        }
        if (config.getDelimiter() == null
                || config.getDelimiter().isBlank()
                || config.getDelimiter().length() > MAX_DELIMITER_LENGTH
                || containsTemplate(config.getDelimiter())) {
            throw invalid("SQL delimiter is invalid");
        }
        Integer queryTimeout = config.getQueryTimeoutSeconds();
        if (queryTimeout == null || queryTimeout <= 0 || queryTimeout > MAX_QUERY_TIMEOUT_SECONDS) {
            throw invalid("SQL query timeout must be between 1 and 86400 seconds");
        }
        validateRows(config);
        if (config.isTransaction() && config.isContinueOnError()) {
            throw invalid("SQL continueOnError requires transaction=false");
        }
        String resultVariable = config.getResultVariable();
        if (resultVariable != null
                && (resultVariable.isBlank()
                        || resultVariable.length() > MAX_VARIABLE_LENGTH
                        || containsTemplate(resultVariable))) {
            throw invalid("SQL result variable is invalid");
        }
    }

    private void validateRows(SqlTaskConfig config) {
        Integer minimum = config.getMinRows();
        Integer maximum = config.getMaxRows();
        if (minimum != null && minimum < 0) {
            throw invalid("SQL minRows cannot be negative");
        }
        if (maximum != null && maximum < 0) {
            throw invalid("SQL maxRows cannot be negative");
        }
        if (minimum != null && maximum != null && minimum > maximum) {
            throw invalid("SQL minRows cannot exceed maxRows");
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private boolean containsTemplate(String value) {
        return value.contains("${");
    }
}
