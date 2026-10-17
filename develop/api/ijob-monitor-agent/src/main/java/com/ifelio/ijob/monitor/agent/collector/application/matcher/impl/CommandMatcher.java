package com.ifelio.ijob.monitor.agent.collector.application.matcher.impl;

import com.ifelio.ijob.contract.monitor.application.ProcessMatchType;
import com.ifelio.ijob.monitor.agent.collector.application.matcher.MatcherSupport;
import com.ifelio.ijob.monitor.agent.collector.application.matcher.ProcessMatcher;

import org.springframework.stereotype.Component;

import oshi.software.os.OSProcess;

@Component
public class CommandMatcher implements ProcessMatcher {

    @Override
    public ProcessMatchType type() {
        return ProcessMatchType.COMMAND;
    }

    @Override
    public boolean matches(OSProcess process, String matchValue) {

        if (process == null || matchValue == null) {
            return false;
        }

        return MatcherSupport.contains(process.getCommandLine(), matchValue);
    }
}
