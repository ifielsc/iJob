package com.ifielsc.ijob.plugin.etl.validator;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ValidatorConfig {
    /** REQUIRED / SQL ... */
    private ValidatorType type;

    private String ruleId;

    private ValidationFailurePolicy onFailure = ValidationFailurePolicy.FILTER;

    private Map<String, Object> properties = new HashMap<>();
}
