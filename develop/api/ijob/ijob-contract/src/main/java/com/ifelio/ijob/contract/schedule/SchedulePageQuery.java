package com.ifelio.ijob.contract.schedule;

import com.ifelio.ijob.contract.common.BasePageQuery;
import com.ifelio.ijob.contract.enums.ScheduleType;
import com.ifelio.ijob.contract.enums.TargetType;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SchedulePageQuery extends BasePageQuery {
    private TargetType targetType;
    private Long targetId;
    private String targetName;
    private ScheduleType scheduleType;
    private Boolean enabled;

    public String getTargetNamePattern() {
        return targetName == null
                ? null
                : "%"
                        + targetName
                                .trim()
                                .replace("!", "!!")
                                .replace("%", "!%")
                                .replace("_", "!_")
                                .replace("[", "![")
                        + "%";
    }
}
