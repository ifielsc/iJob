package com.ifelio.ijob.contract.parameter;

import com.ifelio.ijob.contract.common.BasePageQuery;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParameterTemplateQuery extends BasePageQuery {
    private String name;
}
