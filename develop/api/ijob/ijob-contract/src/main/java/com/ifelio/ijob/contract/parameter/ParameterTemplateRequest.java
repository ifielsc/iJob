package com.ifelio.ijob.contract.parameter;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ParameterTemplateRequest {
    private String name;
    private String description;
    private Long revision;
    private List<ParameterField> fields;
}
