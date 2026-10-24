// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.pack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifielsc.ijob.contract.calendar.CalendarUpsertRequest;
import com.ifielsc.ijob.contract.job.JobUpsertRequest;
import com.ifielsc.ijob.contract.notify.NotifyBindingCreateRequest;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Kind;
import com.ifielsc.ijob.contract.parameter.ParameterTemplateRequest;
import com.ifielsc.ijob.contract.resource.ResourceUpsertRequest;
import com.ifielsc.ijob.contract.schedule.ScheduleUpsertRequest;
import com.ifielsc.ijob.contract.task.TaskUpsertRequest;
import com.ifielsc.ijob.core.calendar.CalendarService;
import com.ifielsc.ijob.core.job.JobService;
import com.ifielsc.ijob.core.notify.NotifyBindingService;
import com.ifielsc.ijob.core.notify.NotifyConfigService;
import com.ifielsc.ijob.core.parameter.ParameterTemplateService;
import com.ifielsc.ijob.core.resource.ResourceService;
import com.ifielsc.ijob.core.schedule.ScheduleService;
import com.ifielsc.ijob.core.task.TaskService;
import com.ifielsc.ijob.core.task.definition.TaskDefinitionValidator;
import com.ifielsc.ijob.core.task.definition.TaskPluginAvailabilityService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PackageObjects {
    private final TaskService tasks;
    private final JobService jobs;
    private final ResourceService resources;
    private final CalendarService calendars;
    private final ParameterTemplateService parameters;
    private final ScheduleService schedules;
    private final NotifyConfigService notifications;
    private final NotifyBindingService bindings;
    private final TaskPluginAvailabilityService availability;
    private final TaskDefinitionValidator taskValidator;
    private final com.ifielsc.ijob.core.job.definition.JobDefinitionValidator jobValidator;
    private final List<com.ifielsc.ijob.core.task.definition.TaskPluginConfigPublicationValidator>
            pluginValidators;
    private final com.ifielsc.ijob.core.task.definition.TaskPluginConfigSnapshotService snapshots;
    private final com.ifielsc.ijob.infra.mapper.resource.ResourceMapper resourceMapper;
    private final com.ifielsc.ijob.infra.mapper.calendar.CalendarMapper calendarMapper;
    private final com.ifielsc.ijob.infra.mapper.parameter.ParameterTemplateMapper parameterMapper;
    private final com.ifielsc.ijob.infra.mapper.notify.NotifyConfigMapper notifyMapper;

    private final com.ifielsc.ijob.core.concurrency.ConcurrencyPolicyService concurrency;
    private final com.ifielsc.ijob.infra.coordination.RuntimeMutationGuard guard;

    /** Acquire before task publication/row locks, matching the policy service lock order. */
    void lockImport() {
        guard.lock(com.ifielsc.ijob.infra.coordination.RuntimeMutationGuard.CONCURRENCY);
    }

    ObjectNode detail(Kind kind, String id) {
        if (kind == Kind.PARAMETER_BINDING) {
            String[] parts = id.split(":");
            if (parts.length != 2) throw PackageJson.invalid();
            var template = parameters.getBinding(parts[0], PackageJson.id(parts[1]));
            return PackageJson.JSON
                    .createObjectNode()
                    .put("targetType", parts[0])
                    .put("targetId", parts[1])
                    .put("templateId", template == null ? null : template.getId().toString());
        }
        long key = PackageJson.id(id);
        ObjectNode result =
                PackageJson.object(
                        switch (kind) {
                            case TASK -> tasks.getDetail(key);
                            case JOB -> jobs.getDetail(key);
                            case RESOURCE -> resources.getDetail(key);
                            case CONCURRENCY_RESOURCE -> concurrency.getResource(key);
                            case CALENDAR -> calendars.getDetail(key);
                            case PARAMETER -> parameters.getDetail(key);
                            case SCHEDULE -> schedules.getDetail(key);
                            case NOTIFY -> notifications.getDetail(key);
                            case NOTIFY_BINDING -> bindings.getDetail(key);
                            default -> throw PackageJson.invalid();
                        });
        if (kind == Kind.TASK) {
            var policy = concurrency.getTaskPolicy(key);
            if (policy.getVersion() > 0) {
                ObjectNode config = PackageJson.JSON.createObjectNode();
                config.set(
                        "taskMaxConcurrency",
                        PackageJson.JSON.valueToTree(policy.getTaskMaxConcurrency()));
                config.put("version", policy.getVersion());
                var ids = config.putArray("resourceIds");
                policy.getResources().stream().map(r -> r.getId()).sorted().forEach(ids::add);
                result.set("concurrencyPolicy", config);
            }
        }
        if (kind == Kind.CONCURRENCY_RESOURCE)
            result.remove(List.of("activeInstanceCount", "referenceCount"));
        // Hash stored ciphertext, never expose it. Even secret-only edits invalidate stale plans.
        if (kind == Kind.RESOURCE)
            result.put(
                    "configurationFingerprint",
                    PackageJson.hash(
                            resourceMapper
                                    .selectById(key)
                                    .getConfig()
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (kind == Kind.NOTIFY)
            result.put(
                    "configurationFingerprint",
                    PackageJson.hash(
                            notifyMapper
                                    .selectById(key)
                                    .getConfig()
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return result;
    }

    ObjectNode body(Kind kind, ObjectNode detail) {
        ObjectNode body;
        switch (kind) {
            case TASK -> {
                body =
                        PackageJson.fields(
                                detail,
                                "name",
                                "pluginType",
                                "pluginConfig",
                                "retryTimes",
                                "retryInterval",
                                "timeout",
                                "enabled",
                                "description",
                                "groups");
                body.set(
                        "pluginConfig",
                        PackageJson.read(body.path("pluginConfig").asText(), ObjectNode.class));
                if (detail.has("concurrencyPolicy"))
                    body.set(
                            "concurrencyPolicy",
                            PackageJson.fields(
                                    detail.get("concurrencyPolicy"),
                                    "taskMaxConcurrency",
                                    "resourceIds"));
                // API_CALL detail exposes names only. Preserve missing inputs as explicit secret
                // slots.
                if ("API_CALL".equals(body.path("pluginType").asText())) {
                    ObjectNode config = (ObjectNode) body.get("pluginConfig");
                    addHeaders(config, detail.path("headerNames"));
                    if (config.get("remoteStop") instanceof ObjectNode stop)
                        addHeaders(stop, detail.path("remoteStopHeaderNames"));
                }
            }
            case JOB -> {
                body =
                        PackageJson.fields(
                                detail,
                                "name",
                                "layout",
                                "timeout",
                                "enabled",
                                "description",
                                "groups",
                                "nodes",
                                "edges");
                // Response-only version metadata is not part of publication input.
                Map<String, String> keys = new java.util.HashMap<>();
                var nodes = PackageJson.JSON.createArrayNode();
                for (JsonNode n : body.path("nodes")) {
                    String key = n.path("nodeKey").asText();
                    keys.put(n.path("id").asText(), key);
                    ObjectNode node =
                            PackageJson.fields(n, "name", "kind", "nodeType", "refId", "gateway");
                    node.put("nodeId", key);
                    if (n.path("policy").isObject())
                        node.set(
                                "policy",
                                PackageJson.fields(
                                        n.get("policy"), "failStrategy", "executeCondition"));
                    nodes.add(node);
                }
                body.set("nodes", nodes);
                var edges = PackageJson.JSON.createArrayNode();
                for (JsonNode e : body.path("edges")) {
                    ObjectNode edge =
                            PackageJson.fields(
                                    e, "routeKey", "priority", "defaultRoute", "condition");
                    edge.put("fromNodeId", keys.get(e.path("fromNodeId").asText()));
                    edge.put("toNodeId", keys.get(e.path("toNodeId").asText()));
                    edges.add(edge);
                }
                body.set("edges", edges);
            }
            case RESOURCE -> {
                body = PackageJson.fields(detail, "code", "name", "type", "enabled", "description");
                String field = resourceField(detail.path("type").asText());
                ObjectNode config = (ObjectNode) detail.path("config").deepCopy();
                for (String key : PackageJson.names(config))
                    if (key.endsWith("Configured")) {
                        String name = key.substring(0, key.length() - 10);
                        if (config.path(key).asBoolean() && !config.hasNonNull(name))
                            config.putNull(name);
                        config.remove(key);
                    }
                if (config.has("headerNames")) {
                    addHeaders(config, config.get("headerNames"));
                    config.remove("headerNames");
                }
                config.remove(List.of("hostKeyFingerprint", "hostKeyType", "webService", "oauth2"));
                if ("MANAGED".equals(config.path("hostKeyVerification").asText()))
                    config.putNull("hostKey");
                if ("SERVICE".equals(detail.path("type").asText())) {
                    ObjectNode service =
                            PackageJson.fields(
                                    config,
                                    "endpoint",
                                    "protocol",
                                    "authType",
                                    "connectTimeout",
                                    "readTimeout",
                                    "writeTimeout",
                                    "callTimeout",
                                    "headers");
                    if ("WEB_SERVICE".equals(config.path("protocol").asText()))
                        service.set(
                                "webService",
                                PackageJson.fields(
                                        config, "wsdl", "namespace", "serviceName", "portName"));
                    if (config.path("authType").asText().contains("OAUTH2")) {
                        ObjectNode oauth = PackageJson.JSON.createObjectNode();
                        for (String key :
                                List.of(
                                        "TokenEndpoint",
                                        "ClientId",
                                        "ClientSecret",
                                        "Scopes",
                                        "Audience",
                                        "ClientAuthenticationMethod",
                                        "RefreshSkewSeconds")) {
                            JsonNode value = config.get("oauth2" + key);
                            oauth.set(
                                    Character.toLowerCase(key.charAt(0)) + key.substring(1),
                                    value == null ? PackageJson.JSON.nullNode() : value);
                        }
                        service.set("oauth2", oauth);
                    }
                    config = service;
                }
                body.set(field, config);
            }
            case CONCURRENCY_RESOURCE ->
                    body =
                            PackageJson.fields(
                                    detail,
                                    "code",
                                    "name",
                                    "maxConcurrency",
                                    "enabled",
                                    "description");
            case CALENDAR -> {
                body = PackageJson.fields(detail, "name", "workdays", "description");
                var a = PackageJson.JSON.createArrayNode();
                for (JsonNode e : detail.path("exceptions"))
                    a.add(PackageJson.fields(e, "date", "type"));
                body.set("exceptions", a);
            }
            case PARAMETER -> body = PackageJson.fields(detail, "name", "description", "fields");
            case SCHEDULE ->
                    body =
                            PackageJson.fields(
                                    detail,
                                    "targetType",
                                    "targetId",
                                    "scheduleType",
                                    "cronExpr",
                                    "intervalSec",
                                    "calendarId",
                                    "startTime",
                                    "endTime",
                                    "enabled",
                                    "misfirePolicy",
                                    "misfireGraceSec",
                                    "maxCatchupCount",
                                    "maxQueueDelaySec",
                                    "variables",
                                    "parameterForm");
            case NOTIFY -> body = PackageJson.fields(detail, "name", "type");
            case PARAMETER_BINDING -> body = detail.deepCopy();
            case NOTIFY_BINDING ->
                    body =
                            PackageJson.fields(
                                    detail,
                                    "targetType",
                                    "targetId",
                                    "notifyConfigId",
                                    "notifyPolicy");
            default -> throw PackageJson.invalid();
        }
        if (kind == Kind.TASK || kind == Kind.JOB || kind == Kind.SCHEDULE)
            body.put("enabled", false);
        return body;
    }

    private static void addHeaders(ObjectNode config, JsonNode names) {
        if (names.isArray() && !names.isEmpty()) {
            ObjectNode headers = config.putObject("headers");
            names.forEach(n -> headers.putNull(n.asText()));
        }
    }

    static String resourceField(String type) {
        return switch (type) {
            case "DB" -> "dbConfig";
            case "FILE" -> "fileConfig";
            case "SSH" -> "sshConfig";
            case "SERVICE" -> "serviceConfig";
            case "RABBITMQ" -> "rabbitConfig";
            case "KAFKA" -> "kafkaConfig";
            case "MONITOR_SERVER" -> "monitorConfig";
            default -> throw PackageJson.invalid();
        };
    }

    void validate(Kind kind, ObjectNode body) {
        switch (kind) {
            case TASK -> {
                if (body.has("concurrencyPolicy")) concurrency.validateTaskImport(policy(body));
                var request = task(body);
                availability.requireEnabledForPublication(request.getPluginType());
                taskValidator.validate(request);
                var validator =
                        pluginValidators.stream()
                                .filter(v -> v.supportTypes().contains(request.getPluginType()))
                                .findFirst()
                                .orElseThrow(PackageJson::invalid);
                validator.validate(
                        request.getPluginType(),
                        request.getPluginConfig(),
                        new com.ifielsc.ijob.core.task.definition
                                .TaskPluginConfigPublicationContext(
                                request.getRetryTimes() == null ? 0 : request.getRetryTimes(),
                                request.getTimeout()));
                if (request.getPluginType() == com.ifielsc.ijob.contract.enums.PluginType.LOCAL_JAR)
                    snapshots.snapshot(request.getPluginType(), request.getPluginConfig());
            }
            case JOB -> jobValidator.validate(PackageJson.convert(body, JobUpsertRequest.class));
            case RESOURCE ->
                    resources.validateImport(
                            PackageJson.convert(body, ResourceUpsertRequest.class));
            case CONCURRENCY_RESOURCE ->
                    concurrency.validateResourceImport(
                            PackageJson.convert(
                                    body,
                                    com.ifielsc.ijob.contract.concurrency
                                            .ConcurrencyResourceUpsertRequest.class));
            case CALENDAR ->
                    calendars.validateImport(
                            PackageJson.convert(body, CalendarUpsertRequest.class));
            case PARAMETER ->
                    parameters.validateImport(
                            PackageJson.convert(body, ParameterTemplateRequest.class));
            case SCHEDULE ->
                    schedules.validateImport(
                            PackageJson.convert(body, ScheduleUpsertRequest.class));
            case NOTIFY_BINDING -> PackageJson.convert(body, NotifyBindingCreateRequest.class);
            default -> {}
        }
    }

    String create(Kind kind, ObjectNode body) {
        validate(kind, body);
        return switch (kind) {
            case TASK -> {
                Long id = tasks.create(task(body)).taskId();
                applyPolicy(id, body, 0L);
                yield id.toString();
            }
            case JOB ->
                    jobs.create(PackageJson.convert(body, JobUpsertRequest.class))
                            .jobId()
                            .toString();
            case RESOURCE ->
                    resources
                            .create(PackageJson.convert(body, ResourceUpsertRequest.class))
                            .toString();
            case CONCURRENCY_RESOURCE ->
                    concurrency
                            .createResource(
                                    PackageJson.convert(
                                            body,
                                            com.ifielsc.ijob.contract.concurrency
                                                    .ConcurrencyResourceUpsertRequest.class))
                            .toString();
            case CALENDAR ->
                    calendars
                            .create(PackageJson.convert(body, CalendarUpsertRequest.class))
                            .toString();
            case PARAMETER ->
                    parameters
                            .create(PackageJson.convert(body, ParameterTemplateRequest.class))
                            .toString();
            case SCHEDULE ->
                    schedules
                            .importDisabled(PackageJson.convert(body, ScheduleUpsertRequest.class))
                            .toString();
            case NOTIFY_BINDING ->
                    bindings.create(PackageJson.convert(body, NotifyBindingCreateRequest.class))
                            .toString();
            case PARAMETER_BINDING -> {
                String type = body.path("targetType").asText(), id = body.path("targetId").asText();
                parameters.bind(
                        type, PackageJson.id(id), PackageJson.id(body.path("templateId").asText()));
                yield type + ":" + id;
            }
            default -> throw PackageJson.invalid();
        };
    }

    void update(Kind kind, String id, ObjectNode before, ObjectNode body) {
        if (before.path("enabled").asBoolean()) throw PackageJson.conflict();
        for (JsonNode s : before.path("schedules"))
            if (s.path("enabled").asBoolean()) throw PackageJson.conflict();
        long revision = PackageJson.id(before.path("currentRevision").asText());
        if (kind == Kind.TASK) {
            tasks.update(PackageJson.id(id), revision, task(body));
            applyPolicy(
                    PackageJson.id(id),
                    body,
                    before.path("concurrencyPolicy").path("version").asLong(0));
        } else if (kind == Kind.JOB)
            jobs.update(
                    PackageJson.id(id),
                    revision,
                    PackageJson.convert(body, JobUpsertRequest.class));
        else throw PackageJson.conflict();
    }

    private static com.ifielsc.ijob.contract.concurrency.TaskConcurrencyPolicyUpdateRequest policy(
            ObjectNode body) {
        return PackageJson.convert(
                body.get("concurrencyPolicy"),
                com.ifielsc.ijob.contract.concurrency.TaskConcurrencyPolicyUpdateRequest.class);
    }

    private void applyPolicy(Long taskId, ObjectNode body, long expectedVersion) {
        // Absent in legacy packages: preserve any target policy rather than clearing it.
        if (!body.has("concurrencyPolicy")) return;
        var request = policy(body);
        request.setExpectedVersion(expectedVersion);
        concurrency.putTaskPolicy(taskId, request);
    }

    private static TaskUpsertRequest task(ObjectNode body) {
        ObjectNode input = body.deepCopy();
        input.remove("concurrencyPolicy");
        input.put("pluginConfig", body.path("pluginConfig").toString());
        return PackageJson.convert(input, TaskUpsertRequest.class);
    }

    String timezone() {
        return schedules.metadata().zoneId();
    }

    void lock(Kind kind, String id) {
        if (kind == Kind.RESOURCE) resourceMapper.selectIdsForUpdate(List.of(PackageJson.id(id)));
        if (kind == Kind.CALENDAR) calendarMapper.selectIdsForUpdate(List.of(PackageJson.id(id)));
        if (kind == Kind.PARAMETER) parameterMapper.selectByIdForUpdate(PackageJson.id(id));
        if (kind == Kind.NOTIFY) notifyMapper.selectIdsForUpdate(List.of(PackageJson.id(id)));
    }

    List<?> candidates(Kind kind, int page) {
        return switch (kind) {
            case TASK -> {
                var q = new com.ifielsc.ijob.contract.task.TaskPageQuery();
                q.setPageNum(page);
                q.setPageSize(100);
                yield tasks.queryPage(q).getList();
            }
            case JOB -> {
                var q = new com.ifielsc.ijob.contract.job.JobPageQuery();
                q.setPageNum(page);
                q.setPageSize(100);
                yield jobs.queryPage(q).getList();
            }
            case RESOURCE -> {
                var q = new com.ifielsc.ijob.contract.resource.ResourcePageQuery();
                q.setPageNum(page);
                q.setPageSize(100);
                yield resources.queryPage(q).getList();
            }
            case CONCURRENCY_RESOURCE -> {
                var q = new com.ifielsc.ijob.contract.concurrency.ConcurrencyResourcePageQuery();
                q.setPageNum(page);
                q.setPageSize(100);
                yield concurrency.queryResources(q).getList();
            }
            case PARAMETER -> {
                var q = new com.ifielsc.ijob.contract.parameter.ParameterTemplateQuery();
                q.setPageNum(page);
                q.setPageSize(100);
                yield parameters.queryPage(q).getList();
            }
            case CALENDAR -> {
                var q = new com.ifielsc.ijob.contract.calendar.CalendarPageQuery();
                q.setPageNum(page);
                q.setPageSize(100);
                yield calendars.queryPage(q).getList();
            }
            case NOTIFY -> {
                var q = new com.ifielsc.ijob.contract.notify.NotifyConfigPageQuery();
                q.setPageNum(page);
                q.setPageSize(100);
                yield notifications.queryPage(q).getList();
            }
            default -> List.of();
        };
    }
}
