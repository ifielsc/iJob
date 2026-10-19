package com.ifielsc.ijob.plugin.datacheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckConfig;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class DataCheckPrecisionTest {
    private final PluginConfigCodec codec = new PluginConfigCodec(new ObjectMapper());

    private String config(String id, String expected) {
        return "{\"resourceId\":1,\"checks\":[{\"name\":\"amount\",\"type\":\"NORMAL\",\"sql\":\"select"
                   + " 1\",\"expected\":"
                + expected
                + "}],\"handlers\":[{\"type\":\"NOTIFY\",\"params\":{\"notifyConfigId\":"
                + id
                + "}}]}";
    }

    @Test
    void decimalAndLongArePreserved() {
        var c =
                codec.read(
                        config("9223372036854775807", "0.123456789012345678901"),
                        DataCheckConfig.class,
                        "Data check");
        assertEquals(
                new BigDecimal("0.123456789012345678901"), c.getChecks().getFirst().getExpected());
        assertEquals(
                "9223372036854775807",
                c.getHandlers().getFirst().getParams().get("notifyConfigId").toString());
        new DataCheckConfigValidator().validate(c);
    }

    @Test
    void fractionalAndOverflowNotificationIdsAreRejected() {
        for (String id : new String[] {"9007199254740993.1", "9223372036854775808", "-1"}) {
            var c = codec.read(config(id, "1"), DataCheckConfig.class, "Data check");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new DataCheckConfigValidator().validate(c));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        codec.read(
                                config("1", "1").replace("\"resourceId\":1", "\"resourceId\":1.5"),
                                DataCheckConfig.class,
                                "Data check"));
    }
}
