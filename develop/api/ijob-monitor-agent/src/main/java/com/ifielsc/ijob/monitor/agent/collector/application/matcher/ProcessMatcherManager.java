// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.collector.application.matcher;

import com.ifielsc.ijob.contract.monitor.application.ProcessMatchType;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProcessMatcherManager {

    private final Map<ProcessMatchType, ProcessMatcher> matcherMap;

    public ProcessMatcherManager(List<ProcessMatcher> matchers) {

        this.matcherMap = new EnumMap<>(ProcessMatchType.class);

        for (ProcessMatcher matcher : matchers) {
            ProcessMatcher existing = matcherMap.putIfAbsent(matcher.type(), matcher);
            if (existing != null) {
                throw new IllegalStateException("duplicate process matcher: " + matcher.type());
            }
        }
    }

    public ProcessMatcher getMatcher(ProcessMatchType type) {

        return matcherMap.get(type);
    }
}
