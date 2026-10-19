package com.ifielsc.ijob.core.notify.channel.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.core.notify.NotifyMessage;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class EmailNotifyFailureTest {
    @Test
    void configurationFailureReachesDispatcherInsteadOfBeingDiscarded() {
        NotifyConfig config = new NotifyConfig();
        config.setConfig("invalid-canary-config");
        EmailNotifyChannel channel =
                new EmailNotifyChannel(
                        MailChannelTestSupport.channel("sender@example.invalid"),
                        new ObjectMapper());
        var failure =
                assertThrows(
                        com.ifielsc.ijob.core.notify.channel.NotifyDeliveryException.class,
                        () ->
                                channel.send(
                                        config,
                                        NotifyMessage.builder()
                                                .subject("test")
                                                .content("content")
                                                .build()));
        assertEquals(
                com.ifielsc.ijob.core.notify.NotifyAuditFailure.CONFIG_INVALID, failure.failure());
    }

    @Test
    void preservesAuthenticationTimeoutAndTransportClassification() {
        var failures =
                java.util.List.of(
                        new org.springframework.mail.MailAuthenticationException("canary-secret"),
                        new org.springframework.mail.MailSendException(
                                "canary-secret", new java.net.SocketTimeoutException()),
                        new org.springframework.mail.MailSendException(
                                "canary-secret", new java.net.ConnectException()));
        var expected =
                java.util.List.of(
                        com.ifielsc.ijob.core.notify.NotifyAuditFailure.AUTHENTICATION_FAILED,
                        com.ifielsc.ijob.core.notify.NotifyAuditFailure.DELIVERY_TIMEOUT,
                        com.ifielsc.ijob.core.notify.NotifyAuditFailure.TRANSPORT_FAILED);
        for (int i = 0; i < failures.size(); i++) {
            var cause = failures.get(i);
            var sender =
                    new JavaMailSenderImpl() {
                        @Override
                        public void send(jakarta.mail.internet.MimeMessage message) {
                            throw cause;
                        }
                    };
            var config = new NotifyConfig();
            config.setConfig(
                    "{\"mailChannelId\":1,\"from\":\"sender@example.invalid\",\"to\":[\"receiver@example.invalid\"]}");
            var channel =
                    new EmailNotifyChannel(
                            MailChannelTestSupport.channel("sender@example.invalid", sender),
                            new ObjectMapper());
            var failure =
                    assertThrows(
                            com.ifielsc.ijob.core.notify.channel.NotifyDeliveryException.class,
                            () ->
                                    channel.send(
                                            config,
                                            NotifyMessage.builder()
                                                    .subject("test")
                                                    .content("test")
                                                    .build()));
            assertEquals(expected.get(i), failure.failure());
            assertEquals(expected.get(i).getSummary(), failure.getMessage());
        }
    }
}
