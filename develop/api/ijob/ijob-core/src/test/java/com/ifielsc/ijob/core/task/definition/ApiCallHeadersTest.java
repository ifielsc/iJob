// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.task.TaskDetailDTO;
import com.ifielsc.ijob.contract.task.TaskUpsertRequest;
import com.ifielsc.ijob.infra.entity.task.TaskDefinition;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

class ApiCallHeadersTest {
    @Test
    void remoteHeadersAreHiddenAndPreservedOnlyFromOwnedVersion() {
        var source = new TaskDefinition();
        source.setTaskId(7L);
        source.setRevision(3L);
        source.setPluginType(PluginType.API_CALL);
        source.setPluginConfig(
                "{\"remoteStop\":{\"path\":\"/stop\",\"headers\":{\"X-Stop\":\"stop-secret\"},\"body\":{\"id\":9007199254740993,\"empty\":null}}}");
        var detail = new TaskDetailDTO();
        detail.setPluginType(PluginType.API_CALL);
        detail.setPluginConfig(source.getPluginConfig());
        ApiCallHeaders.project(detail);
        assertFalse(detail.getPluginConfig().contains("stop-secret"));
        assertEquals(List.of("X-Stop"), detail.getRemoteStopHeaderNames());
        var request = new TaskUpsertRequest();
        request.setPluginType(PluginType.API_CALL);
        request.setPluginConfig(detail.getPluginConfig());
        request.setPreservedRemoteStopHeaderNames(List.of("X-Stop"));
        assertThrows(BusinessException.class, () -> ApiCallHeaders.merge(request, source, 7L, 4L));
        ApiCallHeaders.merge(request, source, 7L, 3L);
        assertEquals(
                "stop-secret",
                ApiCallHeaders.object(request.getPluginConfig())
                        .path("remoteStop")
                        .path("headers")
                        .path("X-Stop")
                        .asText());
        assertEquals(
                new BigInteger("9007199254740993"),
                ApiCallHeaders.object(request.getPluginConfig())
                        .path("remoteStop")
                        .path("body")
                        .path("id")
                        .bigIntegerValue());
    }

    @Test
    void projectionAndHeaderMergePreserveNestedBodyPrecision() {
        String config =
                "{\"headers\":{\"X-Key\":\"secret\"},\"body\":[{\"amount\":123456789012345678.123456789012,\"id\":9007199254740993,\"empty\":null}]}";
        var source = new TaskDefinition();
        source.setTaskId(7L);
        source.setRevision(3L);
        source.setPluginType(PluginType.API_CALL);
        source.setPluginConfig(config);
        var detail = new TaskDetailDTO();
        detail.setPluginType(PluginType.API_CALL);
        detail.setPluginConfig(config);
        ApiCallHeaders.project(detail);
        assertFalse(detail.getPluginConfig().contains("secret"));
        var request = new TaskUpsertRequest();
        request.setPluginType(PluginType.API_CALL);
        request.setPluginConfig(detail.getPluginConfig());
        request.setPreservedHeaderNames(List.of("X-Key"));
        ApiCallHeaders.merge(request, source, 7L, 3L);
        for (String roundTrip : List.of(detail.getPluginConfig(), request.getPluginConfig())) {
            var body = ApiCallHeaders.object(roundTrip).path("body").get(0);
            assertEquals(
                    new BigDecimal("123456789012345678.123456789012"),
                    body.path("amount").decimalValue());
            assertEquals(new BigInteger("9007199254740993"), body.path("id").bigIntegerValue());
            assertEquals(true, body.path("empty").isNull());
        }
        assertEquals(
                "secret",
                ApiCallHeaders.object(request.getPluginConfig())
                        .path("headers")
                        .path("X-Key")
                        .asText());
    }

    @Test
    void projectionNeverReturnsSavedHeaderValues() {
        var detail = new TaskDetailDTO();
        detail.setPluginType(PluginType.API_CALL);
        detail.setPluginConfig(
                "{\"headers\":{\"Authorization\":\"secret-token\"},\"body\":false,\"resourceId\":\"9223372036854775000\"}");
        ApiCallHeaders.project(detail);
        assertFalse(detail.getPluginConfig().contains("secret-token"));
        assertEquals(List.of("Authorization"), detail.getHeaderNames());
        assertEquals(
                false, ApiCallHeaders.object(detail.getPluginConfig()).path("body").booleanValue());
        assertEquals(
                "9223372036854775000",
                ApiCallHeaders.object(detail.getPluginConfig()).path("resourceId").asText());
    }

    @Test
    void retentionRequiresOwnedVersionAndRejectsAmbiguousHeaders() {
        var source = new TaskDefinition();
        source.setTaskId(7L);
        source.setRevision(3L);
        source.setPluginType(PluginType.API_CALL);
        source.setPluginConfig("{\"headers\":{\"X-Key\":\"old-secret\",\"X-Drop\":\"drop\"}}");
        var request = new TaskUpsertRequest();
        request.setPluginType(PluginType.API_CALL);
        request.setPluginConfig("{\"headers\":{\"X-New\":\"new\"},\"body\":0}");
        request.setPreservedHeaderNames(List.of("X-Key"));
        assertThrows(BusinessException.class, () -> ApiCallHeaders.merge(request, source, 8L, 3L));
        assertThrows(BusinessException.class, () -> ApiCallHeaders.merge(request, source, 7L, 4L));
        assertThrows(
                BusinessException.class, () -> ApiCallHeaders.merge(request, null, null, null));
        ApiCallHeaders.merge(request, source, 7L, 3L);
        assertEquals(
                "old-secret",
                ApiCallHeaders.object(request.getPluginConfig())
                        .path("headers")
                        .path("X-Key")
                        .asText());
        assertFalse(request.getPluginConfig().contains("X-Drop"));
        request.setPluginConfig("{\"headers\":{\"x-key\":\"collision\"}}");
        assertThrows(BusinessException.class, () -> ApiCallHeaders.merge(request, source, 7L, 3L));
        request.setPreservedHeaderNames(List.of());
        ApiCallHeaders.merge(request, null, null, null);
        assertEquals(
                "collision",
                ApiCallHeaders.object(request.getPluginConfig())
                        .path("headers")
                        .path("x-key")
                        .asText());
    }
}
