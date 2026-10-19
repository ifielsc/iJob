// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.instance;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;

/** Query-only preview; never changes execution snapshots. Not a general-purpose DLP classifier. */
final class RuntimeVariablePreview {
    private static final ObjectMapper JSON =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private RuntimeVariablePreview() {}

    static Result render(String value) {
        if (value == null) return new Result(null, false, false);
        if (value.length() > 4096) return new Result(null, false, true);
        try {
            JsonNode node = JSON.readTree(value);
            if (node == null) return new Result(null, false, true);
            boolean redacted = redact(node, 0);
            String preview = JSON.writeValueAsString(node);
            if (preview.length() > 4096) return new Result(null, redacted, true);
            return new Result(preview, redacted, false);
        } catch (Exception ignored) {
            // Malformed/deep historical payloads cannot bypass preview controls as raw text.
            return new Result(null, false, true);
        }
    }

    private static boolean redact(JsonNode node, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Variable preview depth exceeded");
        boolean changed = false;
        if (node instanceof ObjectNode object) {
            var fields = object.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String key = field.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (key.contains("password")
                        || key.contains("secret")
                        || key.contains("token")
                        || key.contains("authorization")
                        || key.contains("credential")
                        || key.contains("cookie")
                        || key.contains("privatekey")
                        || key.contains("accesskey")
                        || key.contains("apikey")
                        || key.equals("pwd")
                        || key.equals("pass")) {
                    object.put(field.getKey(), "[REDACTED]");
                    changed = true;
                } else {
                    changed |= redact(field.getValue(), depth + 1);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) changed |= redact(child, depth + 1);
        }
        return changed;
    }

    record Result(String value, boolean redacted, boolean omitted) {}
}
