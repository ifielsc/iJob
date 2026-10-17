package com.ifelio.ijob.core.notify.channel.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.contract.enums.NotifyType;
import com.ifelio.ijob.contract.notify.MailChannelPageQuery;
import com.ifelio.ijob.contract.notify.MailChannelRequest;
import com.ifelio.ijob.contract.notify.NotifyConfigRequest;
import com.ifelio.ijob.contract.notify.NotifyConfigUpsertRequest;
import com.ifelio.ijob.core.notify.NotifyConfigValueService;
import com.ifelio.ijob.core.notify.NotifyMessage;
import com.ifelio.ijob.core.notify.channel.NotifyDeliveryException;
import com.ifelio.ijob.core.notify.security.NotifyConfigCipher;
import com.ifelio.ijob.infra.entity.notify.MailChannel;
import com.ifelio.ijob.infra.entity.notify.NotifyConfig;
import com.ifelio.ijob.infra.mapper.notify.MailChannelMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

class MailChannelServiceTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final MemoryMapper mapper = new MemoryMapper();
    private final AppProperties properties = properties();
    private final MailChannelService service =
            new MailChannelService(mapper, json, properties, new NotifyConfigCipher(properties));

    @Test
    void protectsCredentialsAndNeverReturnsThem() throws Exception {
        Long id = service.create(request());
        assertFalse(mapper.rows.get(id).getConfig().contains("canary-authorization-code"));
        assertTrue(mapper.rows.get(id).getConfig().startsWith("ifjenc:v1:"));
        String dto = json.writeValueAsString(service.getDetail(id));
        assertFalse(dto.contains("canary-authorization-code"));
        assertFalse(dto.contains("\"password\":"));
        assertTrue(service.getDetail(id).isPasswordConfigured());
    }

    @Test
    void preservesSecretButRejectsStaleEditsAndCredentialReuseAcrossAccounts() {
        Long id = service.create(request());
        MailChannelRequest update = request();
        update.setRevision(1L);
        update.setPassword(null);
        update.setName("Renamed");
        service.update(id, update);
        assertEquals("canary-authorization-code", service.delivery(id).sender().getPassword());
        assertThrows(BusinessException.class, () -> service.update(id, update));
        update.setRevision(2L);
        update.setUsername("another@example.test");
        assertThrows(BusinessException.class, () -> service.update(id, update));
        update.setPassword("replacement-code");
        service.update(id, update);
        assertEquals("replacement-code", service.delivery(id).sender().getPassword());
    }

    @Test
    void requiresTlsAndBoundsIoWithoutTrustBypass() {
        Long id = service.create(request());
        var sender = service.delivery(id).sender();
        assertEquals(
                "true", sender.getJavaMailProperties().getProperty("mail.smtp.starttls.required"));
        assertEquals(
                "true",
                sender.getJavaMailProperties().getProperty("mail.smtp.ssl.checkserveridentity"));
        assertNull(sender.getJavaMailProperties().getProperty("mail.smtp.ssl.trust"));
        assertEquals("10000", sender.getJavaMailProperties().getProperty("mail.smtp.timeout"));
        var update = request();
        update.setRevision(1L);
        update.setAuthentication(false);
        service.update(id, update);
        assertFalse(service.getDetail(id).isPasswordConfigured());
        assertNull(service.delivery(id).sender().getPassword());
    }

    @Test
    void validatesPortsSecurityAndSenderHeaderInjection() {
        var request = request();
        request.setPort(0);
        assertThrows(BusinessException.class, () -> service.create(request));
        request.setPort(465);
        request.setSecurity(null);
        assertThrows(BusinessException.class, () -> service.create(request));
        request.setSecurity("TLS");
        request.setFrom("sender@example.test\r\nBcc: injected@example.test");
        assertThrows(BusinessException.class, () -> service.create(request));
        request.setFrom("valid@example.test");
        request.setHost("smtp://example.test/path");
        assertThrows(BusinessException.class, () -> service.create(request));
    }

    @Test
    void notificationReferencesRetainChannelAndMissingChannelIsRejected() throws Exception {
        Long id = service.create(request());
        var values =
                new NotifyConfigValueService(new NotifyConfigCipher(properties), json, service);
        var request = new NotifyConfigUpsertRequest();
        request.setType(NotifyType.EMAIL);
        var email = new NotifyConfigRequest.Email();
        email.setMailChannelId(id);
        email.setFrom("ignored@example.test");
        email.setTo(List.of("receiver@example.test"));
        request.setEmailConfig(email);
        var stored = new NotifyConfig();
        stored.setType(NotifyType.EMAIL);
        stored.setConfig(values.protect(request, null));
        assertEquals(
                "sender@example.test",
                json.readTree(values.decryptForRuntime(stored)).path("from").asText());
        var channelUpdate = request();
        channelUpdate.setRevision(1L);
        channelUpdate.setFrom("updated@example.test");
        service.update(id, channelUpdate);
        assertEquals(
                "updated@example.test",
                json.valueToTree(values.view(stored)).path("from").asText());
        email.setPreserveExisting(true);
        email.setMailChannelId(null);
        email.setFrom(null);
        stored.setConfig(values.protect(request, stored));
        assertEquals(
                id.longValue(),
                json.readTree(values.decryptForRuntime(stored)).path("mailChannelId").asLong());
        email.setPreserveExisting(false);
        assertThrows(BusinessException.class, () -> values.protect(request, null));
        var legacy = new NotifyConfig();
        legacy.setType(NotifyType.EMAIL);
        legacy.setConfig(
                new NotifyConfigCipher(properties)
                        .encrypt(
                                "{\"from\":\"legacy@example.test\",\"to\":[\"receiver@example.test\"]}"));
        email.setPreserveExisting(true);
        assertThrows(BusinessException.class, () -> values.protect(request, legacy));
    }

    @Test
    void twoRealSmtpServersReceiveOnlyTheirSelectedChannelAndDisabledNeverFallsBack()
            throws Exception {
        try (Smtp first = new Smtp();
                Smtp second = new Smtp()) {
            var a = request();
            a.setAuthentication(false);
            a.setHost("127.0.0.1");
            a.setSecurity("NONE");
            a.setPort(first.server.getLocalPort());
            Long aId = service.create(a);
            var b = request();
            b.setAuthentication(false);
            b.setHost("127.0.0.1");
            b.setSecurity("NONE");
            b.setPort(second.server.getLocalPort());
            b.setFrom("second@example.test");
            Long bId = service.create(b);
            assertTrue(service.testConnection(aId).connected());
            assertTrue(first.messages.isEmpty());
            var channel = new EmailNotifyChannel(service, json);
            var config = new NotifyConfig();
            var message =
                    NotifyMessage.builder().subject("isolated-test").content("local-only").build();
            config.setConfig("{\"mailChannelId\":" + aId + ",\"to\":[\"receiver@example.test\"]}");
            channel.send(config, message);
            config.setConfig("{\"mailChannelId\":" + bId + ",\"to\":[\"receiver@example.test\"]}");
            channel.send(config, message);
            assertEquals(1, first.messages.size());
            assertEquals(1, second.messages.size());
            assertTrue(first.messages.getFirst().contains("sender@example.test"));
            assertTrue(second.messages.getFirst().contains("second@example.test"));
            b.setEnabled(false);
            b.setRevision(1L);
            service.update(bId, b);
            assertThrows(NotifyDeliveryException.class, () -> channel.send(config, message));
            config.setConfig(
                    "{\"from\":\"legacy@example.test\",\"to\":[\"receiver@example.test\"]}");
            assertThrows(NotifyDeliveryException.class, () -> channel.send(config, message));
            assertEquals(1, second.messages.size());
        }
    }

    private static AppProperties properties() {
        var p = new AppProperties();
        p.getSecurity().getAes().setKey(Base64.getEncoder().encodeToString(new byte[32]));
        p.getSecurity().getAes().setKeyId("mail-test");
        return p;
    }

    private MailChannelRequest request() {
        var r = new MailChannelRequest();
        r.setName("Test channel");
        r.setHost("smtp.example.test");
        r.setPort(587);
        r.setSecurity("STARTTLS");
        r.setAuthentication(true);
        r.setUsername("sender@example.test");
        r.setPassword("canary-authorization-code");
        r.setFrom("sender@example.test");
        r.setEnabled(true);
        return r;
    }

    private static class MemoryMapper implements MailChannelMapper {
        final Map<Long, MailChannel> rows = new LinkedHashMap<>();

        public List<MailChannel> selectList(MailChannelPageQuery q) {
            return new ArrayList<>(rows.values());
        }

        public MailChannel selectById(Long id) {
            return rows.get(id);
        }

        public MailChannel selectByIdForUpdate(Long id) {
            return selectById(id);
        }

        public List<String> selectEmailConfigurations() {
            return List.of();
        }

        public List<String> selectPendingEmailSnapshots() {
            return List.of();
        }

        public int deleteByIds(List<Long> ids) {
            int count = 0;
            for (Long id : ids) if (rows.remove(id) != null) count++;
            return count;
        }

        public int insert(MailChannel row) {
            row.setId((long) rows.size() + 1);
            rows.put(row.getId(), row);
            return 1;
        }

        public int updateById(MailChannel row) {
            if (!rows.get(row.getId()).getRevision().equals(row.getRevision())) return 0;
            var next = new MailChannel();
            BeanUtils.copyProperties(row, next);
            next.setRevision(row.getRevision() + 1);
            rows.put(row.getId(), next);
            return 1;
        }
    }

    private static class Smtp implements AutoCloseable {
        final ServerSocket server = new ServerSocket();
        final List<String> messages = new CopyOnWriteArrayList<>();
        final Thread thread;

        Smtp() throws Exception {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            thread =
                    Thread.ofPlatform()
                            .daemon()
                            .start(
                                    () -> {
                                        while (!server.isClosed()) {
                                            try (var socket = server.accept();
                                                    var input =
                                                            new BufferedReader(
                                                                    new InputStreamReader(
                                                                            socket.getInputStream(),
                                                                            StandardCharsets
                                                                                    .UTF_8));
                                                    var output =
                                                            new PrintWriter(
                                                                    socket.getOutputStream(),
                                                                    true,
                                                                    StandardCharsets.UTF_8)) {
                                                socket.setSoTimeout(5000);
                                                output.print("220 local-test\r\n");
                                                output.flush();
                                                boolean data = false;
                                                StringBuilder message = new StringBuilder();
                                                String line;
                                                while ((line = input.readLine()) != null) {
                                                    if (data && !line.equals(".")) {
                                                        message.append(line).append('\n');
                                                        continue;
                                                    }
                                                    if (data) {
                                                        messages.add(message.toString());
                                                        data = false;
                                                        output.print("250 accepted\r\n");
                                                    } else if (line.startsWith("DATA")) {
                                                        data = true;
                                                        output.print("354 continue\r\n");
                                                    } else if (line.startsWith("QUIT")) {
                                                        output.print("221 bye\r\n");
                                                        output.flush();
                                                        break;
                                                    } else output.print("250 OK\r\n");
                                                    output.flush();
                                                }
                                            } catch (Exception ignored) {
                                                /* A closed loopback server terminates this fixture. */
                                            }
                                        }
                                    });
        }

        public void close() throws Exception {
            server.close();
            thread.join(2000);
        }
    }
}
