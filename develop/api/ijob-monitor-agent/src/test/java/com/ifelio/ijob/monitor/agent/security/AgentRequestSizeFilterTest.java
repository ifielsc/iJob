package com.ifelio.ijob.monitor.agent.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

class AgentRequestSizeFilterTest {

    @Test
    void shouldReadRequestWithinLimit() throws IOException {
        byte[] request = new byte[AgentRequestSizeFilter.MAX_REQUEST_BYTES];

        assertArrayEquals(
                request, AgentRequestSizeFilter.readBounded(new ByteArrayInputStream(request)));
    }

    @Test
    void shouldRejectRequestAboveLimit() throws IOException {
        byte[] request = new byte[AgentRequestSizeFilter.MAX_REQUEST_BYTES + 1];

        assertNull(AgentRequestSizeFilter.readBounded(new ByteArrayInputStream(request)));
    }
}
