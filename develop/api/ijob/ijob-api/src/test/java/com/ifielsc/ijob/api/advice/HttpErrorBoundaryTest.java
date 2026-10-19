package com.ifielsc.ijob.api.advice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
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

        @GetMapping("/duplicate")
        String duplicate() {
            throw new org.springframework.dao.DuplicateKeyException(
                    "SELECT password FROM private_table");
        }

        @GetMapping("/failure")
        String failure() {
            throw new IllegalStateException("synthetic-secret");
        }

        @GetMapping("/business")
        String business() {
            throw BusinessException.localized(
                    ResultCode.CONFLICT,
                    "Concurrency policy cannot change while permits are active");
        }
    }

    @Test
    void actualMvcDispatchPreservesFrameworkStatusesAndSafeEnvelopes() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new Controller())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        var method = mvc.perform(get("/test")).andReturn().getResponse();
        assertEquals(405, method.getStatus());
        assertTrue(method.getHeader("Allow").contains("POST"));
        assertTrue(method.getContentAsString().contains("IJOB-COMMON-0405"));
        assertEquals(
                415,
                mvc.perform(post("/test").contentType("text/plain").content("secret"))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        assertEquals(
                400,
                mvc.perform(post("/test").contentType("application/json").content("{"))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        assertEquals(404, mvc.perform(get("/missing")).andReturn().getResponse().getStatus());
        var duplicate = mvc.perform(get("/duplicate")).andReturn().getResponse();
        assertEquals(409, duplicate.getStatus());
        assertFalse(duplicate.getContentAsString().contains("private_table"));
        var failure = mvc.perform(get("/failure")).andReturn().getResponse();
        assertEquals(500, failure.getStatus());
        assertFalse(failure.getContentAsString().contains("synthetic-secret"));
    }

    @Test
    void businessCallSiteUsesSelectedLanguageAndStableMetadata() throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(new Controller())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        var english =
                mvc.perform(get("/business").header("Accept-Language", "en-US"))
                        .andReturn()
                        .getResponse();
        var chinese =
                mvc.perform(get("/business").header("Accept-Language", "zh-CN"))
                        .andReturn()
                        .getResponse();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var en = mapper.readTree(english.getContentAsByteArray());
        var zh = mapper.readTree(chinese.getContentAsByteArray());
        assertEquals(en.get("errorCode"), zh.get("errorCode"));
        assertEquals("存在活动许可时不能修改并发策略", zh.get("message").asText());
        assertEquals(
                "Concurrency policy cannot change while permits are active",
                en.get("message").asText());
    }
}
