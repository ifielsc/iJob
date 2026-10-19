// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.collector.application.matcher.impl;

import com.ifielsc.ijob.contract.monitor.application.ProcessMatchType;
import com.ifielsc.ijob.monitor.agent.collector.application.matcher.MatcherSupport;
import com.ifielsc.ijob.monitor.agent.collector.application.matcher.ProcessMatcher;

import org.springframework.stereotype.Component;

import oshi.software.os.OSProcess;

@Component
public class ProcessNameMatcher implements ProcessMatcher {

    @Override
    public ProcessMatchType type() {
        return ProcessMatchType.PROCESS_NAME;
    }

    @Override
    public boolean matches(OSProcess process, String matchValue) {

        if (process == null || matchValue == null) {
            return false;
        }

        return MatcherSupport.equals(process.getName(), matchValue);
    }
}
