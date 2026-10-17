package com.ifelio.ijob.plugin.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;

import org.junit.jupiter.api.Test;

class PushExactConfigTest {
    private final PluginConfigCodec codec =
            new PluginConfigCodec(
                    new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

    @Test
    void retainsLongIdentifiersAndRejectsCoercion() {
        assertEquals(9007199254740993L, read("9007199254740993", "500").getSourceResourceId());
        for (String bad :
                new String[] {
                    "1.2", "1e2", "\"1.2\"", "\"1e2\"", "9223372036854775808", "true", "\" 12 \""
                }) {
            assertThrows(IllegalArgumentException.class, () -> read(bad, "500"));
            assertThrows(IllegalArgumentException.class, () -> read("1", bad));
        }
    }

    @Test
    void kafkaReservedTopicNamesAreRejected() {
        var validator = new PushTaskConfigValidator();
        for (String topic : new String[] {".", ".."}) {
            var config = read("1", "500");
            config.getTarget().getProperties().put("topic", topic);
            assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
        }
    }

    @Test
    void rejectsNonObjectsAndTrailingJson() {
        for (String value : new String[] {"null", "[]", "1", "{} {}"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec.read(value, PushTaskConfig.class, "Push"));
        }
    }

    private PushTaskConfig read(String id, String batch) {
        return codec.read(
                "{\"sourceResourceId\":"
                        + id
                        + ",\"sql\":\"SELECT 1\",\"batchSize\":"
                        + batch
                        + ",\"target\":{\"resourceId\":2,\"type\":\"KAFKA\",\"properties\":{\"topic\":\"orders\"}},\"future\":9007199254740993}",
                PushTaskConfig.class,
                "Push");
    }
}
