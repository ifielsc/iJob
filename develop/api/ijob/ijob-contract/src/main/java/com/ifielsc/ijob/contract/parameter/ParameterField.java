package com.ifielsc.ijob.contract.parameter;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ParameterField {
    private String name;
    private String type;
    private Boolean required;
    private Boolean sensitive;
    private Boolean hasDefault;
    private Object defaultValue;
    private String description;
    private List<String> options;
    private BigDecimal minimum;
    private BigDecimal maximum;
    private Integer maxLength;
}
