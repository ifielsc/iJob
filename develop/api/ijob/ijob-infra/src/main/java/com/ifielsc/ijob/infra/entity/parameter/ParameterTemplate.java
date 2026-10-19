package com.ifielsc.ijob.infra.entity.parameter;

import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParameterTemplate {
    @AutoId(IdNamespace.PARAMETER_TEMPLATE)
    private Long id;

    private String name;
    private String description;
    private String fieldsJson;
    private Long revision;
}
