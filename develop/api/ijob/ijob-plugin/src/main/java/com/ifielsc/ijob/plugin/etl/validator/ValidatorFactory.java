// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.validator;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ValidatorFactory {
    private final Map<String, Validator> validators;

    public ValidatorFactory(List<Validator> list) {
        validators = list.stream().collect(Collectors.toMap(Validator::type, v -> v));
    }

    public Validator get(ValidatorType type) {
        Validator validator = validators.get(type.name());

        if (validator == null) {
            throw new IllegalArgumentException("Validator not found:" + type);
        }

        return validator;
    }
}
