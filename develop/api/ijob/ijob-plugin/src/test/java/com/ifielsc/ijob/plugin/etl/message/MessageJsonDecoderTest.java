package com.ifielsc.ijob.plugin.etl.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.core.resource.mq.MessageDelivery;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;

import org.junit.jupiter.api.Test;

import java.util.Map;

class MessageJsonDecoderTest {
    @Test
    void preservesExactNumbersAndNull() {
        var row =
                new MessageJsonDecoder(new EtlTaskConfig())
                        .decode(
                                MessageTestSupport.delivery(
                                        "{\"id\":9007199254740993,\"amount\":0.1234567890123456789,\"empty\":null}"));
        assertEquals(new java.math.BigInteger("9007199254740993"), row.get("id"));
        assertEquals(new java.math.BigDecimal("0.1234567890123456789"), row.get("amount"));
        assertNull(row.get("empty"));
    }

    @Test
    void rejectsNonObjectsDuplicatesTrailingTokensAndInvalidUtf8() {
        var decoder = new MessageJsonDecoder(new EtlTaskConfig());
        for (String json : new String[] {"[]", "null", "1", "{\"x\":1,\"x\":2}", "{} {}"})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> decoder.decode(MessageTestSupport.delivery(json)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        decoder.decode(
                                new MessageDelivery(new byte[] {(byte) 0xff}, null, Map.of(), 1)));
    }

    @Test
    void enforcesDepthAndMetadataCollision() {
        var config = new EtlTaskConfig();
        config.getMessage().setMaxJsonDepth(1);
        var decoder = new MessageJsonDecoder(config);
        assertThrows(
                IllegalArgumentException.class,
                () -> decoder.decode(MessageTestSupport.delivery("{\"a\":{\"b\":1}}")));
        config.getMessage().getMetadataColumns().put("offset", "offset");
        assertThrows(
                IllegalArgumentException.class,
                () -> decoder.decode(MessageTestSupport.delivery("{\"offset\":2}")));
    }

    @Test
    void acceptsPushGzipAndRejectsExpansionOverLimit() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(output)) {
            gzip.write("{\"value\":\"hello\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        var message = new MessageDelivery(output.toByteArray(), "gzip", Map.of(), 1);
        var config = new EtlTaskConfig();
        assertEquals("hello", new MessageJsonDecoder(config).decode(message).get("value"));
        config.getMessage().setMaxExpandedBytes(5);
        assertThrows(
                IllegalArgumentException.class,
                () -> new MessageJsonDecoder(config).decode(message));
    }
}
