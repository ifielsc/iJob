package com.ifielsc.ijob.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.contract.resource.ResourceConfigRequest;
import com.ifielsc.ijob.contract.resource.ResourceConfigView;
import com.ifielsc.ijob.contract.resource.ResourceUpsertRequest;
import com.ifielsc.ijob.core.resource.security.ResourceConfigCipher;
import com.ifielsc.ijob.infra.entity.resource.Resource;

import org.junit.jupiter.api.Test;

import java.util.Base64;

class MqResourceConfigTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ResourceConfigCipher cipher = cipher();
    private final ResourceConfigService service = new ResourceConfigService(cipher);

    @Test
    void rabbitPreservesPasswordUnknownNumbersAndLegacyRoutesWithoutExposingSecrets()
            throws Exception {
        var existing =
                resource(
                        ResourceType.RABBITMQ,
                        "{\"host\":\"rabbit.invalid\",\"port\":5672,\"username\":\"publisher\",\"password\":\"secret-old\",\"virtualHost\":\"/\",\"exchange\":\"legacy\",\"routingKey\":\"old\",\"publisherConfirm\":true,\"future\":9007199254740993}");
        var request = new ResourceUpsertRequest();
        request.setType(ResourceType.RABBITMQ);
        var rabbit = new ResourceConfigRequest.Rabbit();
        rabbit.setPreserveExisting(true);
        request.setRabbitConfig(rabbit);
        var saved = mapper.readTree(cipher.decrypt(service.protect(request, existing)));
        assertEquals("secret-old", saved.path("password").asText());
        assertEquals("9007199254740993", saved.path("future").asText());
        assertEquals("legacy", saved.path("exchange").asText());
        var view = (ResourceConfigView.Rabbit) service.view(existing);
        assertEquals("rabbit.invalid", view.getHost());
        assertEquals("publisher", view.getUsername());
        assertTrue(view.isPasswordConfigured());
        assertFalse(mapper.writeValueAsString(view).contains("secret-old"));
        rabbit.setPassword("secret-new");
        assertEquals(
                "secret-new",
                mapper.readTree(cipher.decrypt(service.protect(request, existing)))
                        .path("password")
                        .asText());
        rabbit.setClearPassword(true);
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
    }

    @Test
    void kafkaPreservesUnexposedSerializerAndRejectsUnknownAcks() throws Exception {
        var existing =
                resource(
                        ResourceType.KAFKA,
                        "{\"bootstrapServers\":\"kafka.invalid:9092\",\"acks\":\"all\",\"keySerializer\":\"legacy.Serializer\",\"future\":9007199254740993}");
        var request = new ResourceUpsertRequest();
        request.setType(ResourceType.KAFKA);
        var kafka = new ResourceConfigRequest.Kafka();
        kafka.setPreserveExisting(true);
        request.setKafkaConfig(kafka);
        var saved = mapper.readTree(cipher.decrypt(service.protect(request, existing)));
        assertEquals("legacy.Serializer", saved.path("keySerializer").asText());
        assertEquals("9007199254740993", saved.path("future").asText());
        assertEquals(
                "kafka.invalid:9092",
                ((ResourceConfigView.Kafka) service.view(existing)).getBootstrapServers());
        kafka.setAcks("future");
        assertThrows(BusinessException.class, () -> service.protect(request, existing));
        assertThrows(BusinessException.class, () -> service.protect(request, null));
    }

    private Resource resource(ResourceType type, String json) {
        var r = new Resource();
        r.setType(type);
        r.setConfig(cipher.encrypt(json));
        return r;
    }

    private static ResourceConfigCipher cipher() {
        AppProperties p = new AppProperties();
        p.getSecurity().getAes().setKey(Base64.getEncoder().encodeToString(new byte[32]));
        p.getSecurity().getAes().setKeyId("test");
        return new ResourceConfigCipher(p);
    }
}
