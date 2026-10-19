package com.ifielsc.ijob.plugin.push;

import com.ifielsc.ijob.plugin.push.publisher.SerializedPayload;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
public class PushMessage {
    /** Kafka key */
    private String key;

    /** Original business payload before serialization. */
    private Object payload;

    private SerializedPayload serializedPayload;

    /** Header */
    @Builder.Default private Map<String, Object> headers = new LinkedHashMap<>();

    private boolean compressed;
}
