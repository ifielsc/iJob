// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.collector.impl;

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
