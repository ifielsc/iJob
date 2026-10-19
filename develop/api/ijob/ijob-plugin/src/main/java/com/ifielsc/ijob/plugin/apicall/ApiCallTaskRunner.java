// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.apicall;

import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.execute.TimeoutExceptionClassifier;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.task.TaskRunner;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.resource.factory.service.ServiceRequest;
import com.ifielsc.ijob.core.resource.factory.service.ServiceResponse;
import com.ifielsc.ijob.core.resource.factory.service.WebServiceRequest;
import com.ifielsc.ijob.core.resource.factory.service.client.ServiceClient;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.apicall.response.ResponseExtractorService;
import com.ifielsc.ijob.plugin.apicall.response.SuccessEvaluator;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.common.result.PluginMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.plugin.api-call",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ApiCallTaskRunner implements TaskRunner {
    private final ResourceResolver resourceResolver;
    private final ResponseExtractorService extractorService;
    private final SuccessEvaluator successEvaluator;
    private final ApiCallTaskConfigValidator configValidator;
    private final PluginConfigCodec configCodec;
    private final ApiRemoteStopDispatcher remoteStopDispatcher;

    @Override
    public PluginType supportType() {
        return PluginType.API_CALL;
    }

    @Override
    public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
        boolean requestValidated = false;
        try {
            ApiCallConfig config =
                    configCodec.read(spec.getPluginConfig(), ApiCallConfig.class, "API call");
            configValidator.validate(config);
            context.checkExecution();

            Map<String, Object> requestVariables =
                    new java.util.LinkedHashMap<>(context.getVariables());
            if (config.getRemoteStop() != null)
                requestVariables.put(
                        "ijob.apiCall.operationId", java.util.UUID.randomUUID().toString());
            ServiceRequest request = buildRequest(config, context, requestVariables);
            ServiceRequest stopRequest = buildStopRequest(config, context, requestVariables);
            if (stopRequest != null) configValidator.validateResolved(stopRequest);
            configValidator.validateResolved(request);
            requestValidated = true;
            var lease = resourceResolver.leaseServiceClient(config.getResourceId());
            try (var attempt =
                    new ApiCallAttempt(
                            lease,
                            stopRequest,
                            config.getRemoteStop() == null
                                    ? 5
                                    : config.getRemoteStop().getTimeoutSeconds(),
                            context,
                            remoteStopDispatcher)) {
                ServiceClient client = lease.getClient();
                context.checkExecution();
                context.logInfo("api call start resource=" + config.getResourceId());

                ServiceResponse response = attempt.invoke(request);
                context.checkStop();
                context.logInfo("api call finished status=" + response.getStatus());

                if (!client.success(response)) {
                    return TaskResult.fail(PluginMessage.API_STATUS, response.getStatus());
                }
                if (!successEvaluator.evaluate(response, config.getSuccessCondition())) {
                    return TaskResult.fail(PluginMessage.API_BUSINESS_FAILED);
                }

                extractorService.extract(response.getBody(), config.getExtractors(), context);
                context.logInfo("API response validation and variable extraction completed");
                return TaskResult.success();
            }
        } catch (StopException e) {
            return TaskResult.stop();
        } catch (Exception e) {
            try {
                context.checkStop();
            } catch (StopException stopException) {
                return TaskResult.stop();
            }
            context.logError("api call failed type=" + e.getClass().getSimpleName());
            log.error(
                    "API call failed, instanceId={}",
                    context.getInstanceId(),
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.sanitized(e));
            return TimeoutExceptionClassifier.isTimeout(e)
                    ? TaskResult.timeout(PluginMessage.API_TIMEOUT)
                    : requestValidated
                            ? TaskResult.fail(PluginMessage.API_FAILED)
                            : TaskResult.failure(e, PluginMessage.API_FAILED);
        }
    }

    @Override
    public void stop(TaskExecutionSpec spec, TaskContext context) {
        context.cleanupResources();
    }

    private ServiceRequest buildStopRequest(
            ApiCallConfig config, TaskContext context, Map<String, Object> variables) {
        RemoteStopConfig stop = config.getRemoteStop();
        if (stop == null) return null;
        ApiCallConfig requestConfig = new ApiCallConfig();
        requestConfig.setMethod(stop.getMethod());
        requestConfig.setPath(stop.getPath());
        requestConfig.setHeaders(stop.getHeaders());
        requestConfig.setBody(stop.getBody());
        requestConfig.setWebService(stop.getWebService());
        requestConfig.setMaxResponseBytes(64 * 1024);
        ServiceRequest request = buildRequest(requestConfig, context, variables);
        request.setControlRequest(true);
        return request;
    }

    private ServiceRequest buildRequest(
            ApiCallConfig config, TaskContext context, Map<String, Object> variables) {
        ServiceRequest request = new ServiceRequest();
        request.setPath(context.getVariableResolver().resolve(config.getPath(), variables));
        request.setMethod(config.getMethod());
        request.setHeaders(resolveHeaders(config.getHeaders(), context, variables));
        request.setBody(context.getVariableResolver().resolveObject(config.getBody(), variables));
        request.setMaxResponseBytes(config.getMaxResponseBytes());
        request.setWebService(buildWebServiceRequest(config, context, variables));
        return request;
    }

    private WebServiceRequest buildWebServiceRequest(
            ApiCallConfig config, TaskContext context, Map<String, Object> variables) {
        if (config.getWebService() == null) {
            return null;
        }

        WebServiceCallConfig ws = config.getWebService();
        WebServiceRequest request = new WebServiceRequest();
        request.setOperation(context.getVariableResolver().resolve(ws.getOperation(), variables));
        request.setAction(context.getVariableResolver().resolve(ws.getAction(), variables));
        request.setParameters(
                context.getVariableResolver().resolveObjectMap(ws.getParameters(), variables));
        return request;
    }

    private Map<String, String> resolveHeaders(
            Map<String, String> headers, TaskContext context, Map<String, Object> variables) {
        if (headers == null) {
            return null;
        }

        return headers.entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e ->
                                        context.getVariableResolver()
                                                .resolve(e.getValue(), variables)));
    }
}
