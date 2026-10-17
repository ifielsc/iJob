package com.ifelio.ijob.monitor.agent.collector.application.matcher.impl;

import com.ifelio.ijob.contract.monitor.application.ProcessMatchType;
import com.ifelio.ijob.monitor.agent.collector.application.matcher.MatcherSupport;
import com.ifelio.ijob.monitor.agent.collector.application.matcher.ProcessMatcher;

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
