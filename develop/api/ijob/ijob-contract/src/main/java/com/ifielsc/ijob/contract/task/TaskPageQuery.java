package com.ifielsc.ijob.contract.task;

import com.ifielsc.ijob.contract.common.BasePageQuery;
import com.ifielsc.ijob.contract.enums.PluginType;

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
