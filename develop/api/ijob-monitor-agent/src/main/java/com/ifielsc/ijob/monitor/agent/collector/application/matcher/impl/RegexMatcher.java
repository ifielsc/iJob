// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.collector.application.matcher.impl;

import com.ifielsc.ijob.contract.monitor.application.ProcessMatchType;
import com.ifielsc.ijob.monitor.agent.collector.application.matcher.ProcessMatcher;

import org.springframework.stereotype.Component;

import oshi.software.os.OSProcess;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class RegexMatcher implements ProcessMatcher {

    private static final int MAX_CACHE_SIZE = 256;
    private static final int MAX_INPUT_LENGTH = 4096;

    private final Map<String, Pattern> patterns =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(16, 0.75F, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
                            return size() > MAX_CACHE_SIZE;
                        }
                    });

    @Override
    public ProcessMatchType type() {
        return ProcessMatchType.REGEX;
    }

    @Override
    public boolean matches(OSProcess process, String matchValue) {

        if (process == null || matchValue == null) {
            return false;
        }

        Pattern pattern;
        synchronized (patterns) {
            pattern =
                    patterns.computeIfAbsent(
                            matchValue, value -> Pattern.compile(value, Pattern.CASE_INSENSITIVE));
        }

        String command = process.getCommandLine();
        if (command == null) {
            return false;
        }
        String bounded =
                command.length() <= MAX_INPUT_LENGTH
                        ? command
                        : command.substring(0, MAX_INPUT_LENGTH);
        return pattern.matcher(bounded).find();
    }
}
