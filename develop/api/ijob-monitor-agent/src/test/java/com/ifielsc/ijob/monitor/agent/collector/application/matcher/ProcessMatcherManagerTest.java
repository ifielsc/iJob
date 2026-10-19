package com.ifielsc.ijob.monitor.agent.collector.application.matcher;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.contract.monitor.application.ProcessMatchType;

import org.junit.jupiter.api.Test;

import oshi.software.os.OSProcess;

import java.util.List;

class ProcessMatcherManagerTest {

    @Test
    void duplicateMatcherTypeShouldFailAtStartup() {
        ProcessMatcher first = matcher();
        ProcessMatcher second = matcher();

        assertThrows(
                IllegalStateException.class,
                () -> new ProcessMatcherManager(List.of(first, second)));
    }

    private ProcessMatcher matcher() {
        return new ProcessMatcher() {
            @Override
            public ProcessMatchType type() {
                return ProcessMatchType.PROCESS_NAME;
            }

            @Override
            public boolean matches(OSProcess process, String matchValue) {
                return false;
            }
        };
    }
}
