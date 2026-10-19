package com.ifielsc.ijob.monitor.agent.collector.application.matcher;

import com.ifielsc.ijob.contract.monitor.application.ProcessMatchType;

import oshi.software.os.OSProcess;

public interface ProcessMatcher {

    ProcessMatchType type();

    boolean matches(OSProcess process, String matchValue);
}
