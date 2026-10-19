package com.ifielsc.ijob.plugin.servermonitor.rule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskMonitorRule extends AbstractMonitorRule {
    /** Default usage threshold for disks without a path override. */
    private Threshold usage;

    /** Usage threshold overrides keyed by disk path. */
    private Map<String, Threshold> paths;
}
