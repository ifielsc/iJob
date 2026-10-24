// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.pack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Export projections never copy encrypted envelopes or credential values. */
final class PackageSecrets {
    private static final Set<String> SECRET =
            Set.of(
                    "password",
                    "passwd",
                    "pwd",
                    "privatekey",
                    "passphrase",
                    "accesskey",
                    "secretkey",
                    "sessiontoken",
                    "token",
                    "oauth2clientsecret",
                    "clientsecret",
                    "secret",
                    "authorization",
                    "apikey",
                    "webhook");
    private static final Pattern EMBEDDED =
            Pattern.compile(
                    "(?is)(?:-----BEGIN [A-Z ]*PRIVATE"
                        + " KEY|ifjenc:v[0-9]+:|(?:password|passwd|pwd|secret|token|api[_-]?key)\\s*[=:]\\s*[^\\s;&]+|://[^/\\s:@]+:[^/\\s@]+@|\\bBearer\\s+[A-Za-z0-9._~-]+)");

    private PackageSecrets() {}

    static List<String> strip(ObjectNode body) {
        var paths = new ArrayList<String>();
        visit(body, "", paths);
        return paths;
    }

    private static void visit(JsonNode value, String path, List<String> paths) {
        if (value instanceof ObjectNode object) {
            if (object.get("variables") instanceof ObjectNode variables) {
                for (JsonNode field : object.path("parameterForm").path("fields")) {
                    String name = field.path("name").asText();
                    if (field.path("sensitive").asBoolean(false) && variables.hasNonNull(name)) {
                        variables.putNull(name);
                        paths.add(path + "/variables/" + PackageJson.pointer(name));
                    }
                }
            }
            boolean sensitive =
                    object.path("sensitive").asBoolean(false)
                            || object.path("secret").asBoolean(false);
            for (String key : PackageJson.names(object)) {
                JsonNode child = object.get(key);
                String p = path + "/" + PackageJson.pointer(key);
                String lower = key.toLowerCase(Locale.ROOT);
                boolean credential =
                        SECRET.contains(lower)
                                || sensitive && Set.of("defaultValue", "value").contains(key);
                if (credential && !child.isNull() && !child.isBoolean()) {
                    object.putNull(key);
                    paths.add(p);
                } else if (child.isTextual() && EMBEDDED.matcher(child.textValue()).find()) {
                    object.putNull(key);
                    paths.add(p);
                } else if (key.equals("headers") && child.isObject()) {
                    for (String name : PackageJson.names((ObjectNode) child))
                        if (!child.get(name).isNull()) {
                            ((ObjectNode) child).putNull(name);
                            paths.add(p + "/" + PackageJson.pointer(name));
                        }
                } else visit(child, p, paths);
            }
        } else if (value.isArray())
            for (int i = 0; i < value.size(); i++) visit(value.get(i), path + "/" + i, paths);
    }

    static ObjectNode safe(ObjectNode node, List<String> secretPaths) {
        ObjectNode safe = node.deepCopy();
        strip(safe);
        for (String path : secretPaths)
            if (!safe.at(path).isMissingNode())
                PackageJson.put(safe, path, PackageJson.JSON.nullNode());
        return safe;
    }
}
