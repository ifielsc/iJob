package com.ifelio.ijob.contract.task;

import com.ifelio.ijob.contract.common.BasePageQuery;
import com.ifelio.ijob.contract.enums.PluginType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TaskPageQuery extends BasePageQuery {
    private Long id;
    private String name;
    private PluginType pluginType;
    private Boolean enabled;
    private String group;
}
