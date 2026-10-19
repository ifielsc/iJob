package com.ifielsc.ijob.core.notify.channel.dingtalk;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ifielsc.ijob.core.notify.NotifyAuditFailure;
import com.ifielsc.ijob.core.notify.NotifyMessage;
import com.ifielsc.ijob.core.notify.channel.NotifyDeliveryException;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class DingTalkClientTest {
    @Test
    void requiresAnExplicitIntegralZeroBusinessCode() {
        for (String body :
                new String[] {
                    "{\"errcode\":310000,\"errmsg\":\"canary-secret\"}",
                    "",
                    "{}",
                    "null",
                    "[]",
                    "{\"errcode\":\"0\"}",
                    "{\"errcode\":0.1}",
                    "{\"errcode\":4294967296}",
                    "not-json"
                }) {
            RestTemplate rest = new RestTemplate();
            MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
            server.expect(request -> {}).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            var failure =
                    assertThrows(
                            NotifyDeliveryException.class,
                            () -> new DingTalkClient(rest).send(config(), message()),
                            body);
            assertFalse(failure.getMessage().contains("canary"));
            server.verify();
        }
    }

    @Test
    void distinguishesPermanentRefusalRateLimitAndUnknownBusinessFailure() {
        String[] codes = {"310000", "40035", "130101", "-1", "4294967296"};
        NotifyAuditFailure[] expected = {
            NotifyAuditFailure.REMOTE_REJECTED,
            NotifyAuditFailure.REMOTE_REJECTED,
            NotifyAuditFailure.RATE_LIMITED,
            NotifyAuditFailure.REMOTE_FAILURE,
            NotifyAuditFailure.REMOTE_FAILURE
        };
        for (int i = 0; i < codes.length; i++) {
            RestTemplate rest = new RestTemplate();
            var server = MockRestServiceServer.bindTo(rest).build();
            server.expect(request -> {})
                    .andRespond(
                            withSuccess(
                                    "{\"errcode\":" + codes[i] + "}", MediaType.APPLICATION_JSON));
            var failure =
                    assertThrows(
                            NotifyDeliveryException.class,
                            () -> new DingTalkClient(rest).send(config(), message()));
            assertEquals(expected[i], failure.failure());
            server.verify();
        }
    }

    @Test
    void classifiesHttpAndTransportFailuresWithoutExposingResponseOrUrl() {
        for (int status : new int[] {400, 401, 403, 408, 429, 500, 503}) {
            RestTemplate rest = new RestTemplate();
            var server = MockRestServiceServer.bindTo(rest).build();
            server.expect(request -> {})
                    .andRespond(
                            withStatus(org.springframework.http.HttpStatus.valueOf(status))
                                    .body("canary-secret"));
            var failure =
                    assertThrows(
                            NotifyDeliveryException.class,
                            () -> new DingTalkClient(rest).send(config(), message()));
            assertEquals(
                    status == 408 || status == 429 || status >= 500,
                    failure.failure().isRetryable());
            assertFalse(failure.getMessage().contains("canary"));
            server.verify();
        }
        for (var cause :
                new java.io.IOException[] {
                    new java.net.SocketTimeoutException("canary"),
                    new java.net.ConnectException("canary")
                }) {
            RestTemplate rest = new RestTemplate();
            var server = MockRestServiceServer.bindTo(rest).build();
            server.expect(request -> {}).andRespond(withException(cause));
            var failure =
                    assertThrows(
                            NotifyDeliveryException.class,
                            () -> new DingTalkClient(rest).send(config(), message()));
            assertEquals(
                    cause instanceof java.net.SocketTimeoutException
                            ? NotifyAuditFailure.DELIVERY_TIMEOUT
                            : NotifyAuditFailure.TRANSPORT_FAILED,
                    failure.failure());
            server.verify();
        }
    }

    @Test
    void acceptsSuccessfulBusinessResponse() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(request -> {})
                .andRespond(
                        withSuccess(
                                "{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));
        assertDoesNotThrow(() -> new DingTalkClient(rest).send(config(), message()));
        server.verify();
    }

    private DingTalkConfig config() {
        DingTalkConfig config = new DingTalkConfig();
        config.setWebhook("https://test.invalid/robot?access_token=canary-token");
        return config;
    }

    private NotifyMessage message() {
        return NotifyMessage.builder().subject("test").content("content").build();
    }
}
