package com.ifelio.ijob.contract.resource;

import com.ifelio.ijob.contract.common.BasePageQuery;
import com.ifelio.ijob.contract.enums.ResourceType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ResourcePageQuery extends BasePageQuery {
    private String code;
    private String name;
    private ResourceType type;
    private Boolean enabled;
}
