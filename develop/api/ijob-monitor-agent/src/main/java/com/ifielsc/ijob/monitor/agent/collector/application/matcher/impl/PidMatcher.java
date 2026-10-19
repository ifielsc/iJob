package com.ifielsc.ijob.monitor.agent.collector.application.matcher.impl;

import com.ifielsc.ijob.contract.monitor.application.ProcessMatchType;
import com.ifielsc.ijob.monitor.agent.collector.application.matcher.ProcessMatcher;

import org.springframework.stereotype.Component;

import oshi.software.os.OSProcess;

@Component
public class PidMatcher implements ProcessMatcher {

    @Override
    public ProcessMatchType type() {
        return ProcessMatchType.PID;
    }

    @Override
    public boolean matches(OSProcess process, String matchValue) {

        if (process == null || matchValue == null) {
            return false;
        }

        try {

            int pid = Integer.parseInt(matchValue);

            return process.getProcessID() == pid;

        } catch (NumberFormatException e) {

            return false;
        }
    }
}
