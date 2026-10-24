// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.pack;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

final class PackageJson {
    static final ObjectMapper JSON =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .disable(
                            com.fasterxml.jackson.databind.SerializationFeature
                                    .WRITE_DATES_AS_TIMESTAMPS);

    static {
        JSON.getFactory()
                .setStreamReadConstraints(
                        StreamReadConstraints.builder()
                                .maxNestingDepth(64)
                                .maxStringLength(8 * 1024 * 1024)
                                .build());
        JSON.getFactory().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
    }

    private PackageJson() {}

    static BusinessException invalid() {
        return new BusinessException(ResultCode.PARAM_ERROR);
    }

    static BusinessException conflict() {
        return new BusinessException(ResultCode.CONFLICT);
    }

    static Conflict conflict(String reason) {
        return new Conflict(reason);
    }

    static final class Conflict extends BusinessException {
        private static final long serialVersionUID = 1L;
        final String reason;

        Conflict(String reason) {
            super(ResultCode.CONFLICT);
            this.reason = reason;
        }
    }

    static <T> T read(String text, Class<T> type) {
        try {
            return JSON.readValue(text, type);
        } catch (Exception e) {
            throw invalid();
        }
    }

    static <T> T convert(Object value, Class<T> type) {
        try {
            return JSON.convertValue(value, type);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    static ObjectNode object(Object value) {
        return JSON.valueToTree(value);
    }

    static ObjectNode fields(JsonNode source, String... names) {
        ObjectNode result = JSON.createObjectNode();
        for (String name : names)
            if (source.has(name)) result.set(name, source.get(name).deepCopy());
        return result;
    }

    static JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            var sorted = new TreeMap<String, JsonNode>();
            value.fields().forEachRemaining(e -> sorted.put(e.getKey(), canonical(e.getValue())));
            return JSON.valueToTree(sorted);
        }
        if (value.isArray()) {
            var a = JSON.createArrayNode();
            value.forEach(v -> a.add(canonical(v)));
            return a;
        }
        return value;
    }

    static String hash(JsonNode value) {
        return hash(canonical(value).toString().getBytes(StandardCharsets.UTF_8));
    }

    static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static long id(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid();
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw invalid();
        }
    }

    static void put(ObjectNode body, String pointer, JsonNode value) {
        if (pointer == null || !pointer.startsWith("/") || pointer.length() > 512) throw invalid();
        int split = pointer.lastIndexOf('/');
        JsonNode parent = split == 0 ? body : body.at(pointer.substring(0, split));
        String field = pointer.substring(split + 1).replace("~1", "/").replace("~0", "~");
        if (parent instanceof ObjectNode object) object.set(field, value);
        else if (parent instanceof com.fasterxml.jackson.databind.node.ArrayNode array) {
            int index;
            try {
                index = Integer.parseInt(field);
            } catch (NumberFormatException e) {
                throw invalid();
            }
            if (index < 0 || index >= array.size()) throw invalid();
            array.set(index, value);
        } else throw invalid();
    }

    static String pointer(String field) {
        return field.replace("~", "~0").replace("/", "~1");
    }

    static List<String> names(ObjectNode node) {
        return java.util.stream.StreamSupport.stream(
                        java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false)
                .toList();
    }
}
