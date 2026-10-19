// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.model;

import lombok.Getter;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Getter
public class AgentRuntime {

    private final LocalDateTime startTime = LocalDateTime.now();
}
