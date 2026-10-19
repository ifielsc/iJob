// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.api.concurrency.ConcurrencyPolicyController;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountRole;
import com.ifielsc.ijob.contract.auth.CurrentUserResponse;
import com.ifielsc.ijob.contract.concurrency.ConcurrencyResourcePageQuery;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

class SafeReadProjectionTest {
    @Test
    void nestedVariablesRawMessagesAndFutureFieldsAreNeverPassedThrough() throws Exception {
        var json = new ObjectMapper();
        var input =
                json.readTree(
                        """
{"list":[{"id":1,"name":"safe","inputVariables":"secret","outputVariables":"secret",
"message":"secret","displayMessage":"secret","messageArgs":"secret",
"variables":{"id":"secret"},"script":"secret","passwordHash":"secret",
"futureSensitiveField":"secret","decisionComment":"secret","taskName":"secret",
"children":[{"id":2,"message":"secret"}]}],"total":1}
""");
        String projected = new SafeReadProjection(json).project(input).toString();
        assertFalse(projected.contains("secret"));
        assertTrue(projected.contains("safe"));
        assertTrue(projected.contains("\"id\":2"));
        assertTrue(projected.contains("\"total\":1"));
    }

    @Test
    void concurrencyPageProfileKeepsCountsButRejectsFutureNestedSecretsForBothReadRoles()
            throws Exception {
        var json = new ObjectMapper();
        var projection = new SafeReadProjection(json);
        var parameter =
                new MethodParameter(
                        ConcurrencyPolicyController.class.getMethod(
                                "resourcePage", ConcurrencyResourcePageQuery.class),
                        -1);
        for (AccountRole role :
                new AccountRole[] {AccountRole.VIEWER, AccountRole.OPERATOR, AccountRole.ADMIN}) {
            var input =
                    json.readTree(
                            """
{"list":[{"id":"9007199254740993","code":"pool","name":"safe","enabled":true,
"version":"2","maxConcurrency":3,"activeInstanceCount":1,"referenceCount":2,
"futureSensitiveField":{"name":"secret"},"config":{"id":"secret"},"messageArgs":["secret"]}],
"total":21,"pageNum":2,"pageSize":20,"pages":2,"futureSensitiveField":"secret"}
""");
            var body = ApiResponse.success(input);
            var request = new MockHttpServletRequest();
            request.setAttribute(
                    ApiSecurityInterceptor.ACTOR,
                    new CurrentUserResponse("actor", "actor", "actor", role, Set.of(), false));
            Object output =
                    projection.beforeBodyWrite(
                            body,
                            parameter,
                            MediaType.APPLICATION_JSON,
                            MappingJackson2HttpMessageConverter.class,
                            new ServletServerHttpRequest(request),
                            null);
            if (role == AccountRole.ADMIN) {
                assertSame(body, output);
            } else {
                var data = json.valueToTree(((ApiResponse<?>) output).getData());
                assertFalse(data.toString().contains("secret"));
                assertEquals(21, data.path("total").asInt());
                assertEquals(2, data.path("pageNum").asInt());
                assertEquals(20, data.path("pageSize").asInt());
                assertEquals(2, data.path("pages").asInt());
                assertEquals("9007199254740993", data.path("list").get(0).path("id").asText());
                assertEquals("safe", data.path("list").get(0).path("name").asText());
                assertEquals(3, data.path("list").get(0).path("maxConcurrency").asInt());
                assertEquals(1, data.path("list").get(0).path("activeInstanceCount").asInt());
                assertEquals(2, data.path("list").get(0).path("referenceCount").asInt());
            }
        }
    }
}
