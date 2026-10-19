// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.message;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.core.resource.mq.MessageDelivery;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.model.DataRecord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class MessageJsonDecoder {
    private final EtlTaskConfig config;
    private final ObjectMapper mapper;
    private int decodedBytes;

    public int decodedBytes() {
        return decodedBytes;
    }

    public MessageJsonDecoder(EtlTaskConfig config) {
        this.config = config;
        var factory =
                com.fasterxml.jackson.core.JsonFactory.builder()
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .streamReadConstraints(
                                StreamReadConstraints.builder()
                                        .maxNestingDepth(config.getMessage().getMaxJsonDepth())
                                        .maxStringLength(
                                                config.getExecution().getMaxTextFieldChars())
                                        .build())
                        .build();
        mapper =
                new ObjectMapper(factory)
                        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public DataRecord decode(MessageDelivery message) {
        try {
            byte[] bytes = message.body();
            if (bytes == null || bytes.length > config.getMessage().getMaxMessageBytes())
                throw new IllegalArgumentException("Message byte limit exceeded");
            if (message.encoding() != null
                    && !message.encoding().isBlank()
                    && !"identity".equalsIgnoreCase(message.encoding())) {
                if (!"gzip".equalsIgnoreCase(message.encoding()))
                    throw new IllegalArgumentException("Unsupported message encoding");
                try (var input = new GZIPInputStream(new ByteArrayInputStream(bytes));
                        var output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int size;
                    while ((size = input.read(buffer)) != -1) {
                        if ((long) output.size() + size > config.getMessage().getMaxExpandedBytes())
                            throw new IllegalArgumentException(
                                    "Expanded message byte limit exceeded");
                        output.write(buffer, 0, size);
                    }
                    bytes = output.toByteArray();
                }
            }
            if (bytes.length > config.getMessage().getMaxExpandedBytes())
                throw new IllegalArgumentException("Expanded message byte limit exceeded");
            decodedBytes = bytes.length;
            String text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
            Map<String, Object> body =
                    mapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            if (body == null) throw new IllegalArgumentException("Message must be a JSON object");
            for (var entry : config.getMessage().getMetadataColumns().entrySet()) {
                if (body.containsKey(entry.getValue()))
                    throw new IllegalArgumentException("Message metadata field collides with body");
                Object value = message.metadata().get(entry.getKey());
                if (value instanceof Map<?, ?> || value instanceof java.util.Collection<?>)
                    value = mapper.writeValueAsString(value);
                body.put(entry.getValue(), value);
            }
            if (body.size() > config.getExecution().getMaxRecordFields())
                throw new IllegalArgumentException("Message field limit exceeded");
            return new DataRecord(body);
        } catch (Exception exception) {
            // Jackson diagnostics can contain business payload; never attach them to a public
            // error.
            throw new IllegalArgumentException(
                    "Message must be a bounded UTF-8 JSON object with valid metadata");
        }
    }
}
