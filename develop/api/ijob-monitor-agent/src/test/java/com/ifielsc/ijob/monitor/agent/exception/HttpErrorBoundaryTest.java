// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.monitor.agent.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class HttpErrorBoundaryTest {
    @RestController
    static class Controller {
        @PostMapping(value = "/test", consumes = "application/json")
        String body(@RequestBody java.util.Map<String, Object> body) {
            return "ok";
        }
    }

    @Test
    void malformedJsonAndFrameworkFailuresAreNotServerErrors() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new Controller())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        var malformed =
                mvc.perform(post("/test").contentType("application/json").content("{secret"))
                        .andReturn()
                        .getResponse();
        assertEquals(400, malformed.getStatus());
        assertFalse(malformed.getContentAsString().contains("secret"));
        assertTrue(malformed.getContentAsString().contains("IJOB-AGENT-HTTP-400"));
        assertEquals(405, mvc.perform(get("/test")).andReturn().getResponse().getStatus());
        assertEquals(
                415,
                mvc.perform(post("/test").contentType("text/plain").content("secret"))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        assertEquals(404, mvc.perform(get("/missing")).andReturn().getResponse().getStatus());
    }
}
