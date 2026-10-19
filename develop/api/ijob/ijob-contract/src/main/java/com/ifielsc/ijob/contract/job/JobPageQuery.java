package com.ifielsc.ijob.contract.job;

import com.ifielsc.ijob.contract.common.BasePageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class JobPageQuery extends BasePageQuery {
    private Long id;
    private String name;
    private Boolean enabled;
    private String group;

    public String getNamePattern() {
        return name == null
                ? null
                : "%"
                        + name.trim()
                                .replace("!", "!!")
                                .replace("%", "!%")
                                .replace("_", "!_")
                                .replace("[", "![")
                        + "%";
    }
}
