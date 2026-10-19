package com.ifielsc.ijob.core.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.contract.notify.NotifyConfigRequest;
import com.ifielsc.ijob.contract.notify.NotifyConfigUpsertRequest;
import com.ifielsc.ijob.contract.notify.NotifyConfigView;
import com.ifielsc.ijob.core.notify.security.NotifyConfigCipher;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

class NotifyConfigValueServiceTest {
    @Test
    void explicitEmailPreservationShouldKeepRecipientsAndSupportEmptyCc() throws Exception {
        NotifyConfigValueService service = service(cipher());
        NotifyConfigUpsertRequest request = new NotifyConfigUpsertRequest();
        request.setType(NotifyType.EMAIL);
        NotifyConfigRequest.Email email = new NotifyConfigRequest.Email();
        email.setMailChannelId(1L);
        request.setEmailConfig(email);
        email.setFrom("sender@example.test");
        email.setTo(List.of("to@example.test"));
        email.setCc(List.of("cc@example.test"));
        NotifyConfig existing = new NotifyConfig();
        existing.setType(NotifyType.EMAIL);
        existing.setConfig(service.protect(request, null));
        var view = (NotifyConfigView.Email) service.view(existing);
        assertEquals("sender@example.test", view.getFrom());
        assertEquals(List.of("to@example.test"), view.getTo());
        assertEquals(List.of("cc@example.test"), view.getCc());
        email.setFrom(null);
        email.setTo(null);
        email.setCc(null);
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
        email.setPreserveExisting(true);
        NotifyConfig updated = new NotifyConfig();
        updated.setType(NotifyType.EMAIL);
        updated.setConfig(service.protect(request, existing));
        var preserved = new ObjectMapper().readTree(service.decryptForRuntime(updated));
        assertEquals("sender@example.test", preserved.path("from").asText());
        assertEquals("to@example.test", preserved.path("to").get(0).asText());
        assertEquals(1, preserved.path("cc").size());
        email.setCc(List.of());
        updated.setConfig(service.protect(request, existing));
        assertEquals(
                0,
                new ObjectMapper().readTree(service.decryptForRuntime(updated)).path("cc").size());
        email.setTo(List.of());
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
        email.setTo(null);
        assertThrows(BusinessException.class, () -> service.protect(request, null));
    }

    @Test
    void explicitDingTalkPreservationShouldKeepMobilesUntilCleared() throws Exception {
        NotifyConfigValueService service = service(cipher());
        NotifyConfigUpsertRequest request =
                dingTalkRequest("https://example.test/hook", "signing-secret");
        request.getDingTalkConfig().setAtMobiles(List.of("13800000000"));
        request.getDingTalkConfig().setAtAll(true);
        NotifyConfig existing = stored(service.protect(request, null));
        request.getDingTalkConfig().setAtMobiles(null);
        request.getDingTalkConfig().setAtAll(null);
        request.getDingTalkConfig().setPreserveExisting(true);
        assertTrue(
                new ObjectMapper()
                        .readTree(
                                service.decryptForRuntime(
                                        stored(service.protect(request, existing))))
                        .path("atAll")
                        .asBoolean());
        request.getDingTalkConfig().setAtAll(false);
        assertFalse(
                new ObjectMapper()
                        .readTree(
                                service.decryptForRuntime(
                                        stored(service.protect(request, existing))))
                        .path("atAll")
                        .asBoolean());
        assertTrue(
                service.decryptForRuntime(stored(service.protect(request, existing)))
                        .contains("13800000000"));
        request.getDingTalkConfig().setAtMobiles(List.of());
        assertFalse(
                service.decryptForRuntime(stored(service.protect(request, existing)))
                        .contains("13800000000"));
    }

    @Test
    void dingTalkUpdateShouldPreserveAndExplicitlyClearSecret() {
        NotifyConfigCipher cipher = cipher();
        NotifyConfigValueService service = service(cipher);
        NotifyConfigUpsertRequest create =
                dingTalkRequest("https://example.com/hook/token", "signing-secret");
        NotifyConfig existing = stored(service.protect(create, null));

        NotifyConfigUpsertRequest preserve = dingTalkRequest(null, null);
        String preserved = service.decryptForRuntime(stored(service.protect(preserve, existing)));

        preserve.getDingTalkConfig().setClearSecret(true);
        String cleared = service.decryptForRuntime(stored(service.protect(preserve, existing)));

        assertTrue(preserved.contains("signing-secret"));
        assertTrue(preserved.contains("https://example.com/hook/token"));
        assertFalse(cleared.contains("signing-secret"));
        assertTrue(cleared.contains("\"secret\":null"));
    }

    @Test
    void shouldRejectUnsafeWebhookAndMismatchedTypedConfig() {
        NotifyConfigValueService service = service(cipher());
        NotifyConfigUpsertRequest unsafe = dingTalkRequest("http://example.com/hook", null);
        NotifyConfigUpsertRequest mismatched = dingTalkRequest("https://example.com/hook", null);
        mismatched.setType(NotifyType.EMAIL);

        BusinessException unsafeError =
                assertThrows(BusinessException.class, () -> service.protect(unsafe, null));
        BusinessException mismatchError =
                assertThrows(BusinessException.class, () -> service.protect(mismatched, null));

        assertEquals(ResultCode.PARAM_ERROR, unsafeError.getCode());
        assertEquals(ResultCode.PARAM_ERROR, mismatchError.getCode());
    }

    @Test
    void shouldRejectDuplicateOrCrossListedEmailRecipients() {
        NotifyConfigValueService service = service(cipher());
        NotifyConfigRequest.Email email = new NotifyConfigRequest.Email();
        email.setMailChannelId(1L);
        email.setFrom("sender@example.com");
        email.setTo(List.of("Ops@example.com"));
        email.setCc(List.of("ops@example.com"));
        NotifyConfigUpsertRequest request = new NotifyConfigUpsertRequest();
        request.setName("email");
        request.setType(NotifyType.EMAIL);
        request.setEmailConfig(email);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.protect(request, null));

        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
    }

    @Test
    void detailShouldRevealMobilesButMaskAllWebhookQueryValuesAndNeverReturnSecrets()
            throws Exception {
        NotifyConfigValueService service = service(cipher());
        for (String query :
                List.of(
                        "access_token=private-token",
                        "ACCESS_TOKEN=private-token&other=private-other",
                        "access%5Ftoken=private-token",
                        "private-token")) {
            String webhook = "https://example.test/robot/send?" + query;
            NotifyConfigUpsertRequest request = dingTalkRequest(webhook, "private-signing-secret");
            NotifyConfig stored = stored(service.protect(request, null));
            var view = (NotifyConfigView.DingTalk) service.view(stored);
            assertEquals("https://example.test/robot/send?••••", view.getWebhookDisplay());
            assertEquals(List.of("13800000000"), view.getAtMobiles());
            assertTrue(view.isWebhookConfigured());
            assertTrue(view.isSecretConfigured());
            var json = new ObjectMapper().valueToTree(view);
            assertFalse(json.has("webhook"));
            assertFalse(json.has("secret"));
            assertFalse(json.toString().contains("private-"));
            request.getDingTalkConfig().setWebhook(null);
            request.getDingTalkConfig().setSecret(null);
            var saved =
                    new ObjectMapper()
                            .readTree(
                                    service.decryptForRuntime(
                                            stored(service.protect(request, stored))));
            assertEquals(webhook, saved.path("webhook").asText());
            assertEquals("private-signing-secret", saved.path("secret").asText());
        }
        var plain =
                (NotifyConfigView.DingTalk)
                        service.view(
                                stored(
                                        service.protect(
                                                dingTalkRequest(
                                                        "https://example.test:8443/hook", null),
                                                null)));
        assertEquals("https://example.test:8443/hook", plain.getWebhookDisplay());
    }

    private NotifyConfigUpsertRequest dingTalkRequest(String webhook, String secret) {
        NotifyConfigRequest.DingTalk config = new NotifyConfigRequest.DingTalk();
        config.setWebhook(webhook);
        config.setSecret(secret);
        config.setAtMobiles(List.of("13800000000"));
        NotifyConfigUpsertRequest request = new NotifyConfigUpsertRequest();
        request.setName("ding talk");
        request.setType(NotifyType.DINGTALK);
        request.setDingTalkConfig(config);
        return request;
    }

    private NotifyConfig stored(String config) {
        NotifyConfig stored = new NotifyConfig();
        stored.setId(1L);
        stored.setType(NotifyType.DINGTALK);
        stored.setConfig(config);
        return stored;
    }

    private NotifyConfigValueService service(NotifyConfigCipher cipher) {
        return new NotifyConfigValueService(
                cipher,
                new ObjectMapper(),
                com.ifielsc.ijob.core.notify.channel.email.MailChannelTestSupport.channel(
                        "sender@example.test"));
    }

    private NotifyConfigCipher cipher() {
        AppProperties properties = new AppProperties();
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 22);
        properties.getSecurity().getAes().setKey(Base64.getEncoder().encodeToString(key));
        properties.getSecurity().getAes().setKeyId("test");
        return new NotifyConfigCipher(properties);
    }
}
