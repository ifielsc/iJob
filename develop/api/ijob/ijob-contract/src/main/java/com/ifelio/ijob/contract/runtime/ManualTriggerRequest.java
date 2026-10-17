package com.ifelio.ijob.contract.runtime;

import lombok.Data;

import java.util.Map;

@Data
public class ManualTriggerRequest {
    private String requestId;
    private Map<String, Object> variables;
    private com.ifelio.ijob.contract.parameter.ParameterForm parameterForm;
}
