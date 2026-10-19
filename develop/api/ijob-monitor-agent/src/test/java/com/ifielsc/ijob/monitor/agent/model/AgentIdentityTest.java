// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.contract.monitor.AgentInfo;
import com.ifielsc.ijob.contract.monitor.OSType;
import com.ifielsc.ijob.monitor.agent.config.AgentProperties;

import org.junit.jupiter.api.Test;

class AgentIdentityTest {

    @Test
    void shouldBuildCompleteIdentityWithConfiguredHostname() {
        AgentProperties properties = new AgentProperties();
        properties.setHostname("worker-01");

        AgentInfo info = new AgentIdentity(properties).info(new AgentRuntime());

        assertEquals("1.0.0", info.getVersion());
        assertEquals("worker-01", info.getHostname());
        assertNotNull(info.getOsType());
    }

    @Test
    void shouldMapSupportedOperatingSystems() {
        assertEquals(OSType.WINDOWS, AgentIdentity.resolveOsType("Windows Server"));
        assertEquals(OSType.LINUX, AgentIdentity.resolveOsType("Linux"));
        assertEquals(OSType.MACOS, AgentIdentity.resolveOsType("Mac OS X"));
        assertThrows(IllegalStateException.class, () -> AgentIdentity.resolveOsType("unknown"));
    }
}
