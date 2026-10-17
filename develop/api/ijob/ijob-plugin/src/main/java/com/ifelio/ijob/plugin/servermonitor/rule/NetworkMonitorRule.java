package com.ifelio.ijob.plugin.servermonitor.rule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkMonitorRule extends AbstractMonitorRule {
    private Threshold receiveRate;
    private Threshold sendRate;
}
