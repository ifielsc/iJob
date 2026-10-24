// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.pack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Kind;

import java.util.LinkedHashMap;
import java.util.Map;

/** Known reference paths are explicit; business numbers and payload IDs are never rewritten. */
final class PackageReferences {
    private PackageReferences() {}

    static Map<String, Kind> paths(Kind kind, ObjectNode body) {
        Map<String, Kind> paths = new LinkedHashMap<>();
        switch (kind) {
            case TASK -> {
                JsonNode policy = body.get("concurrencyPolicy");
                if (policy != null) {
                    if (!policy.isObject()
                            || !policy.path("resourceIds").isArray()
                            || policy.path("resourceIds").size() > 500) throw PackageJson.invalid();
                    for (int i = 0; i < policy.path("resourceIds").size(); i++)
                        paths.put("/concurrencyPolicy/resourceIds/" + i, Kind.CONCURRENCY_RESOURCE);
                }
                PluginType type;
                try {
                    type = PluginType.valueOf(body.path("pluginType").asText());
                } catch (Exception e) {
                    throw PackageJson.invalid();
                }
                String p = "/pluginConfig";
                JsonNode config = body.path("pluginConfig");
                if (!config.isObject()) throw PackageJson.invalid();
                switch (type) {
                    case SQL, API_CALL, DATA_CHECK, SERVER_MONITOR ->
                            paths.put(p + "/resourceId", Kind.RESOURCE);
                    case SCRIPT -> paths.put(p + "/sshResourceId", Kind.RESOURCE);
                    case EVENT -> paths.put(p + "/fileResourceId", Kind.RESOURCE);
                    case FILE_TRANSFER, BATCH_ETL -> {
                        paths.put(p + "/sourceResourceId", Kind.RESOURCE);
                        paths.put(p + "/targetResourceId", Kind.RESOURCE);
                    }
                    case PUSH -> {
                        paths.put(p + "/sourceResourceId", Kind.RESOURCE);
                        paths.put(p + "/target/resourceId", Kind.RESOURCE);
                    }
                    case ETL -> {
                        paths.put(p + "/source/resourceId", Kind.RESOURCE);
                        paths.put(p + "/target/resourceId", Kind.RESOURCE);
                        for (String section : new String[] {"transforms", "validations"})
                            for (int i = 0; i < config.path(section).size(); i++)
                                paths.put(
                                        p + "/" + section + "/" + i + "/properties/resourceId",
                                        Kind.RESOURCE);
                    }
                    case LOCAL_JAR -> {
                        /* External artifact requirements remain visible in configuration. */
                    }
                }
                if (type == PluginType.SERVER_MONITOR)
                    paths.put(p + "/notify/notifyConfigId", Kind.NOTIFY);
                if (type == PluginType.DATA_CHECK)
                    for (int i = 0; i < config.path("handlers").size(); i++)
                        if ("NOTIFY"
                                .equalsIgnoreCase(
                                        config.path("handlers").get(i).path("type").asText()))
                            paths.put(p + "/handlers/" + i + "/params/notifyConfigId", Kind.NOTIFY);
            }
            case JOB -> {
                for (int i = 0; i < body.path("nodes").size(); i++) {
                    JsonNode n = body.path("nodes").get(i);
                    if ("EXECUTION".equals(n.path("kind").asText())) {
                        Kind target = target(n.path("nodeType").asText());
                        paths.put("/nodes/" + i + "/refId", target);
                    }
                }
            }
            case SCHEDULE -> {
                paths.put("/targetId", target(body.path("targetType").asText()));
                paths.put("/calendarId", Kind.CALENDAR);
                paths.put("/parameterForm/templateId", Kind.PARAMETER);
            }
            case PARAMETER_BINDING -> {
                paths.put("/targetId", target(body.path("targetType").asText()));
                paths.put("/templateId", Kind.PARAMETER);
            }
            case NOTIFY_BINDING -> {
                paths.put("/targetId", target(body.path("targetType").asText()));
                paths.put("/notifyConfigId", Kind.NOTIFY);
            }
            default -> {}
        }
        return paths;
    }

    private static Kind target(String type) {
        if ("TASK".equals(type)) return Kind.TASK;
        if ("JOB".equals(type)) return Kind.JOB;
        throw PackageJson.invalid();
    }
}
