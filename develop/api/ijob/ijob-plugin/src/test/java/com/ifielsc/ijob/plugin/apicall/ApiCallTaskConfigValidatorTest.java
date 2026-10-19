// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.apicall;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ApiCallTaskConfigValidatorTest {
    private final ApiCallTaskConfigValidator validator = new ApiCallTaskConfigValidator();

    @Test
    void incompleteJsonPathMustNotSilentlySelectItsParent() {
        for (String path : List.of("$.nested[", "$.nested[0", "$.nested['key'", "$.nested]")) {
            ApiCallConfig config = base();
            config.setExtractors(List.of(extractor("value", path)));
            assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
            config.setExtractors(null);
            config.setSuccessCondition(condition(path, "EQ", "true"));
            assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
        }
        for (String path :
                List.of(
                        "$.nested.items[0].id",
                        "$['key[with]brackets']",
                        "$[?(@.name =~ /[a-z]+/)]")) {
            ApiCallConfig config = base();
            config.setExtractors(List.of(extractor("value", path)));
            assertDoesNotThrow(() -> validator.validate(config));
        }
    }

    @Test
    void completeHttpConfigurationShouldPass() {
        ApiCallConfig config = base();
        config.setPath("/users/${id!}");
        config.setMethod("patch");
        config.setHeaders(Map.of("Authorization", "Bearer ${token!}"));
        config.setExtractors(List.of(extractor("userId", "$.data.id")));
        config.setSuccessCondition(condition("$.code", "eq", "0"));

        assertDoesNotThrow(() -> validator.validate(config));
    }

    @Test
    void missingResourceAndUnsupportedMethodShouldFail() {
        ApiCallConfig missing = base();
        missing.setResourceId(null);
        ApiCallConfig unsupported = base();
        unsupported.setMethod("TRACE");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(missing));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(unsupported));
    }

    @Test
    void lineBreakHeaderAndOversizedCollectionShouldFail() {
        ApiCallConfig lineBreak = base();
        lineBreak.setHeaders(Map.of("X-Test", "safe\r\ninjected"));
        ApiCallConfig oversized = base();
        Map<String, String> headers = new LinkedHashMap<>();
        for (int index = 0; index < 101; index++) {
            headers.put("X-" + index, "value");
        }
        oversized.setHeaders(headers);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(lineBreak));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(oversized));
    }

    @Test
    void duplicateReservedOrBlankExtractorShouldFail() {
        ApiCallConfig duplicate = base();
        duplicate.setExtractors(
                List.of(extractor("token", "$.token"), extractor("token", "$.backup")));
        ApiCallConfig reserved = base();
        reserved.setExtractors(List.of(extractor("response", "$.data")));
        ApiCallConfig blank = base();
        blank.setExtractors(List.of(extractor("token", " ")));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(duplicate));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(reserved));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(blank));
    }

    @Test
    void invalidSuccessConditionShouldFail() {
        ApiCallConfig operator = base();
        operator.setSuccessCondition(condition("$.code", "CONTAINS", "0"));
        ApiCallConfig expected = base();
        expected.setSuccessCondition(condition("$.code", "EQ", null));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(operator));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(expected));
    }

    @Test
    void responseLimitShouldRemainWithinSupportedRange() {
        ApiCallConfig tooSmall = base();
        tooSmall.setMaxResponseBytes(1023);
        ApiCallConfig tooLarge = base();
        tooLarge.setMaxResponseBytes(16 * 1024 * 1024 + 1);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(tooSmall));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(tooLarge));
    }

    @Test
    void webServiceParametersMustUseXmlNames() {
        ApiCallConfig valid = base();
        WebServiceCallConfig webService = new WebServiceCallConfig();
        webService.setOperation("lookup");
        webService.setAction("urn:${action}");
        webService.setParameters(Map.of("user-id", "${id}"));
        valid.setWebService(webService);

        ApiCallConfig invalid = base();
        WebServiceCallConfig bad = new WebServiceCallConfig();
        bad.setOperation("lookup");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("bad name", 1);
        bad.setParameters(parameters);
        invalid.setWebService(bad);

        assertDoesNotThrow(() -> validator.validate(valid));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalid));
    }

    private ApiCallConfig base() {
        ApiCallConfig config = new ApiCallConfig();
        config.setResourceId(12L);
        return config;
    }

    private ResponseExtractor extractor(String variable, String expression) {
        ResponseExtractor extractor = new ResponseExtractor();
        extractor.setVariable(variable);
        extractor.setExpression(expression);
        return extractor;
    }

    private SuccessCondition condition(String expression, String operator, String expected) {
        SuccessCondition condition = new SuccessCondition();
        condition.setExpression(expression);
        condition.setOperator(operator);
        condition.setExpected(expected);
        return condition;
    }
}
