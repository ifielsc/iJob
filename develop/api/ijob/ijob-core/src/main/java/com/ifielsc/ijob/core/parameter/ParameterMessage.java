// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.parameter;

import com.ifielsc.ijob.common.response.MessageDefinition;

public enum ParameterMessage implements MessageDefinition {
    INVALID(
            "IJOB-PARAMETER-001",
            "parameter.invalid",
            "Parameter definition or value is invalid: {0}"),
    NOT_FOUND(
            "IJOB-PARAMETER-002",
            "parameter.notFound",
            "Parameter template or target does not exist"),
    CHANGED("IJOB-PARAMETER-003", "parameter.changed", "Template changed; reload before saving"),
    DUPLICATE(
            "IJOB-PARAMETER-004",
            "parameter.duplicate",
            "A template with this name already exists");
    private final String code;
    private final String key;
    private final String text;

    ParameterMessage(String code, String key, String text) {
        this.code = code;
        this.key = key;
        this.text = text;
    }

    public String errorCode() {
        return code;
    }

    public String messageKey() {
        return key;
    }

    public String defaultMessage() {
        return text;
    }
}
