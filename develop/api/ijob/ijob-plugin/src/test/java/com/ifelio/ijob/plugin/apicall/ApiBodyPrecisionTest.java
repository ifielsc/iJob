package com.ifelio.ijob.plugin.apicall;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

class ApiBodyPrecisionTest {
    @Test
    void literalBodyValuesRemainExactThroughExecutionConfigDecoding() {
        var config =
                new PluginConfigCodec(new ObjectMapper())
                        .read(
                                "{\"body\":{\"id\":9007199254740993,\"amount\":0.1234567890123456789,\"flag\":false}}",
                                ApiCallConfig.class,
                                "API_CALL");
        var body = (Map<?, ?>) config.getBody();
        assertEquals(new BigInteger("9007199254740993"), body.get("id"));
        assertEquals(new BigDecimal("0.1234567890123456789"), body.get("amount"));
        assertEquals(false, body.get("flag"));
    }
}
