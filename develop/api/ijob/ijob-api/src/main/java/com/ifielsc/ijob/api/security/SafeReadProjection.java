// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifielsc.ijob.api.diagnostics.DiagnosticsController;
import com.ifielsc.ijob.api.event.EventWaitController;
import com.ifielsc.ijob.api.job.JobDesignController;
import com.ifielsc.ijob.api.schedule.ScheduleController;
import com.ifielsc.ijob.api.workbench.WorkbenchController;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.contract.auth.AccountRole;
import com.ifielsc.ijob.contract.auth.CurrentUserResponse;
import com.ifielsc.ijob.contract.diagnostics.DiagnosticsDTO;
import com.ifielsc.ijob.contract.job.JobExecutionContextDTO;
import com.ifielsc.ijob.contract.job.JobMetadataDTO;
import com.ifielsc.ijob.contract.schedule.ScheduleMetadataDTO;
import com.ifielsc.ijob.contract.schedule.SchedulePreviewDTO;
import com.ifielsc.ijob.contract.task.TaskExecutionContextDTO;
import com.ifielsc.ijob.contract.workbench.WorkbenchDTO;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Non-admin response allowlist: no raw message, log, variable, script or connection fields. */
@RestControllerAdvice
public class SafeReadProjection implements ResponseBodyAdvice<Object> {
    private static final Set<String> CONCURRENCY_FIELDS =
            Set.of(
                    "list",
                    "total",
                    "pageNum",
                    "pageSize",
                    "pages",
                    "id",
                    "code",
                    "name",
                    "version",
                    "enabled",
                    "description",
                    "maxConcurrency",
                    "referenceCount",
                    "activeInstanceCount",
                    "taskId",
                    "taskName",
                    "pluginType",
                    "taskMaxConcurrency",
                    "resources",
                    "createTime",
                    "updateTime",
                    "plugins",
                    "type",
                    "minConcurrency",
                    "permitWaitTimeoutMillis",
                    "retryIntervalMillis",
                    "maxRetryIntervalMillis",
                    "retryJitterMillis",
                    "configurationSource");
    private static final Set<String> APPROVAL_FIELDS =
            Set.of(
                    "list",
                    "total",
                    "pageNum",
                    "pageSize",
                    "pages",
                    "id",
                    "taskName",
                    "rootTargetName",
                    "taskId",
                    "taskDefinitionId",
                    "attemptNo",
                    "instanceId",
                    "rootInstanceId",
                    "waitType",
                    "status",
                    "acceptAfter",
                    "timeoutAt",
                    "matchedEventId",
                    "decisionBy",
                    "decisionComment",
                    "resolvedAt",
                    "createTime",
                    "updateTime");
    private static final Set<String> FIELDS =
            Set.of(
                    "list",
                    "total",
                    "pageNum",
                    "pageSize",
                    "pages",
                    "children",
                    "nodes",
                    "roots",
                    "id",
                    "code",
                    "name",
                    "type",
                    "enabled",
                    "pluginType",
                    "groups",
                    "retryTimes",
                    "retryInterval",
                    "timeout",
                    "currentTaskDefinitionId",
                    "currentRevision",
                    "currentDefinitionId",
                    "nodeCount",
                    "createTime",
                    "updateTime",
                    "targetType",
                    "targetId",
                    "scheduleId",
                    "scheduleType",
                    "cronExpr",
                    "intervalSec",
                    "calendarId",
                    "startTime",
                    "endTime",
                    "lastFireTime",
                    "nextFireTime",
                    "misfirePolicy",
                    "misfireGraceSec",
                    "maxCatchupCount",
                    "maxQueueDelaySec",
                    "triggerType",
                    "status",
                    "triggerTime",
                    "businessDate",
                    "businessTime",
                    "businessZoneId",
                    "jobDefinitionId",
                    "taskDefinitionId",
                    "rootInstanceId",
                    "targetName",
                    "nodeId",
                    "parentInstanceId",
                    "triggerId",
                    "progress",
                    "retryCount",
                    "timeoutAt",
                    "permitWaitDeadline",
                    "definitionNodeId",
                    "requestId",
                    "instanceId",
                    "commandType",
                    "requestedBy",
                    "expiresAt",
                    "processedAt",
                    "replayed",
                    "waitType",
                    "acceptAfter",
                    "matchedEventId",
                    "decisionBy",
                    "resolvedAt",
                    "capacity",
                    "maxConcurrency",
                    "resourceId",
                    "taskId",
                    "permits",
                    "priority",
                    "revision",
                    "zoneId",
                    "dates",
                    "date",
                    "included",
                    "excluded",
                    "fireTimes",
                    "times",
                    "calendarType",
                    "weekdays",
                    "monthDays",
                    "months",
                    "count",
                    "policy",
                    "resourceCode",
                    "truncated",
                    "maxNodes",
                    "gatewayDecisionsTruncated",
                    "workdays",
                    "exceptions",
                    "taskMaxConcurrency",
                    "version",
                    "resources");
    private final ObjectMapper mapper;

    public SafeReadProjection(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(
            MethodParameter parameter, Class<? extends HttpMessageConverter<?>> converter) {
        ApiAccess access = parameter.getMethodAnnotation(ApiAccess.class);
        return access != null && !access.publicEndpoint() && !access.selfService();
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter parameter,
            MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converter,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servlet)) return body;
        HttpServletRequest http = servlet.getServletRequest();
        Object actor = http.getAttribute(ApiSecurityInterceptor.ACTOR);
        if (!(actor instanceof CurrentUserResponse user) || user.getRoleCode() == AccountRole.ADMIN)
            return body;
        if (!(body instanceof ApiResponse<?> envelope) || !envelope.isSuccess()) return body;
        // This dedicated DTO contains only validated engine/mode and public identifiers/names.
        // Do not widen the recursive allowlist for similarly named fields on other endpoints.
        if (envelope.getData() instanceof TaskExecutionContextDTO) return body;
        if (parameter.getContainingClass() == com.ifielsc.ijob.api.task.TaskHistoryController.class
                && parameter.getMethod().getName().equals("versions")) return body;
        // Dedicated aggregates contain no configuration/log content; approval totals are actor
        // scoped.
        if (parameter.getContainingClass() == WorkbenchController.class
                && envelope.getData() instanceof WorkbenchDTO) return body;
        // This exact diagnostics endpoint returns only explicitly selected coordination facts.
        if (parameter.getContainingClass() == DiagnosticsController.class
                && envelope.getData() instanceof DiagnosticsDTO) return body;
        if (parameter.getContainingClass()
                        == com.ifielsc.ijob.api.runtime.RuntimeQueryController.class
                && envelope.getData() instanceof com.ifielsc.ijob.contract.runtime.RunGraphDTO)
            return body;
        if (parameter.getContainingClass() == JobDesignController.class
                && (envelope.getData() instanceof JobMetadataDTO
                        || envelope.getData() instanceof JobExecutionContextDTO
                        || parameter.getMethod().getName().equals("versions"))) return body;
        // These scheduling DTOs contain only public settings and computed calendar decisions.
        // Shared templates contain public parameter definitions; sensitive defaults are rejected on
        // write.
        // Preserve only this controller's typed DTOs, never arbitrary runtime variable objects.
        if (parameter.getContainingClass()
                == com.ifielsc.ijob.api.parameter.ParameterTemplateController.class) return body;
        if (parameter.getContainingClass() == ScheduleController.class
                && (envelope.getData() instanceof ScheduleMetadataDTO
                        || envelope.getData() instanceof SchedulePreviewDTO)) return body;
        if (parameter.getContainingClass() == EventWaitController.class)
            return ApiResponse.success(
                    project(mapper.valueToTree(envelope.getData()), APPROVAL_FIELDS));
        if (parameter.getContainingClass()
                == com.ifielsc.ijob.api.concurrency.ConcurrencyPolicyController.class)
            return ApiResponse.success(
                    project(mapper.valueToTree(envelope.getData()), CONCURRENCY_FIELDS));
        return ApiResponse.success(project(mapper.valueToTree(envelope.getData())));
    }

    JsonNode project(JsonNode node) {
        return project(node, FIELDS);
    }

    private JsonNode project(JsonNode node, Set<String> fields) {
        if (node.isArray()) {
            node.forEach(child -> project(child, fields));
            return node;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> remove = new ArrayList<>();
            object.fieldNames()
                    .forEachRemaining(
                            key -> {
                                if (!fields.contains(key)) remove.add(key);
                            });
            object.remove(remove);
            object.elements().forEachRemaining(child -> project(child, fields));
        }
        return node;
    }
}
