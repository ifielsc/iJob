// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.monitor.agent.config.AgentProperties;

import org.junit.jupiter.api.Test;

class AgentAuthenticationFilterTest {

    @Test
    void shouldRejectMissingTokenConfiguration() {
        AgentProperties properties = new AgentProperties();

        assertThrows(IllegalStateException.class, () -> new AgentAuthenticationFilter(properties));
    }

    @Test
    void shouldRejectMissingOrIncorrectBearerToken() {
        AgentAuthenticationFilter filter = filter("secret-token");

        assertFalse(filter.isAuthorized(null));
        assertFalse(filter.isAuthorized("Basic secret-token"));
        assertFalse(filter.isAuthorized("Bearer wrong-token"));
    }

    @Test
    void shouldAllowMatchingBearerToken() {
        AgentAuthenticationFilter filter = filter("secret-token");

        assertTrue(filter.isAuthorized("Bearer secret-token"));
    }

    private AgentAuthenticationFilter filter(String token) {
        AgentProperties properties = new AgentProperties();
        properties.setToken(token);
        return new AgentAuthenticationFilter(properties);
    }
}
