package com.ifelio.ijob.plugin.datacheck.model;

import com.ifelio.ijob.plugin.datacheck.config.CheckLevel;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataCheckItemResult {
    private String name;

    private boolean success;

    private Object actual;

    private Object expected;

    private String message;

    private CheckLevel level;

    public static DataCheckItemResult success(
            String name, Object actual, Object expected, CheckLevel level) {
        return builder()
                .name(name)
                .success(true)
                .actual(actual)
                .expected(expected)
                .level(level)
                .build();
    }

    public static DataCheckItemResult failed(
            String name, Object actual, Object expected, String message, CheckLevel level) {
        return builder()
                .name(name)
                .success(false)
                .actual(actual)
                .expected(expected)
                .message(message)
                .level(level)
                .build();
    }
}
