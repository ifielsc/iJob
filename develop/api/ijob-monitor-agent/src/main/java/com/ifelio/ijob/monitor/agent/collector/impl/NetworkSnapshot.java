package com.ifelio.ijob.monitor.agent.collector.impl;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NetworkSnapshot {

    private long receiveBytes;

    private long sendBytes;

    /** Sample timestamp in epoch milliseconds. */
    private long timestamp;
}
