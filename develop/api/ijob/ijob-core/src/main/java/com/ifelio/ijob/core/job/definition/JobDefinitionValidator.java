package com.ifelio.ijob.core.job.definition;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.MessageDefinition;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.common.routing.ExecutionGroupCodes;
import com.ifelio.ijob.contract.enums.GatewayMode;
import com.ifelio.ijob.contract.enums.JobNodeKind;
import com.ifelio.ijob.contract.job.JobNodePolicyRequest;
import com.ifelio.ijob.contract.job.JobNodeRequest;
import com.ifelio.ijob.contract.job.JobUpsertRequest;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.core.execute.graph.JobGraphDefinitionValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JobDefinitionValidator {
    public static final int MAX_NAME_LENGTH = 128;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final boolean DEFAULT_ENABLED = true;

    private final JobGraphDefinitionValidator graphValidator;

    public void validate(JobUpsertRequest request) {
        if (request == null) {
            throw parameterError(CoreMessage.JOB_REQUEST_BODY_IS_REQUIRED);
        }

        validateJobFields(request);
        validateGroups(request);
        validateNodeFields(request.getNodes());
        graphValidator.validate(request.getNodes(), request.getEdges());
        JobLayoutCodec.validate(request.getLayout(), request.getNodes(), request.getEdges());
    }

    private void validateGroups(JobUpsertRequest request) {
        try {
            request.setGroups(ExecutionGroupCodes.normalize(request.getGroups()));
        } catch (IllegalArgumentException exception) {
            throw parameterError(CoreMessage.EXECUTION_GROUPS_ARE_INVALID, exception.getMessage());
        }
    }

    private void validateJobFields(JobUpsertRequest request) {
        requireCanonicalName(request.getName(), "jobName");
        if (request.getTimeout() != null && request.getTimeout() <= 0) {
            throw parameterError(CoreMessage.JOB_TIMEOUT_MUST_BE_GREATER_THAN_ZERO);
        }
        if (request.getDescription() != null
                && request.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            throw parameterError(
                    CoreMessage.JOB_DESCRIPTION_MUST_NOT_EXCEED_VALUE_CHARACTERS,
                    MAX_DESCRIPTION_LENGTH);
        }
    }

    private void validateNodeFields(List<JobNodeRequest> nodes) {
        if (nodes == null) {
            return;
        }

        for (JobNodeRequest node : nodes) {
            if (node == null) {
                continue;
            }
            requireCanonicalName(node.getName(), "nodeName");
            JobNodeKind kind = node.getKind() == null ? JobNodeKind.EXECUTION : node.getKind();
            node.setKind(kind);
            if (kind == JobNodeKind.GATEWAY) {
                validateGateway(node);
            } else {
                validateExecutionNode(node);
            }
        }
    }

    private void validateExecutionNode(JobNodeRequest node) {
        if (node.getNodeType() == null) {
            throw parameterError(CoreMessage.NODE_TYPE_IS_REQUIRED);
        }
        if (node.getRefId() == null || node.getRefId() <= 0) {
            throw parameterError(CoreMessage.NODE_REFERENCE_ID_MUST_BE_GREATER_THAN_ZERO);
        }
        if (node.getGateway() != null) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Execution node cannot define a gateway");
        }
        validatePolicy(node.getPolicy());
    }

    private void validateGateway(JobNodeRequest node) {
        if (node.getNodeType() != null || node.getRefId() != null || node.getPolicy() != null) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR,
                    "Gateway node cannot define execution target or node policy");
        }
        if (node.getGateway() == null || node.getGateway().getMode() == null) {
            throw BusinessException.localized(ResultCode.PARAM_ERROR, "Gateway mode is required");
        }
        if (node.getGateway().getMode() != GatewayMode.EXCLUSIVE) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Only EXCLUSIVE gateway is supported in version 1");
        }
    }

    private void validatePolicy(JobNodePolicyRequest policy) {
        if (policy == null) {
            throw parameterError(CoreMessage.NODE_POLICY_IS_REQUIRED);
        }
        if (policy.getFailStrategy() == null) {
            throw parameterError(CoreMessage.NODE_FAILURE_POLICY_IS_REQUIRED);
        }
        if (policy.getExecuteCondition() == null) {
            throw parameterError(CoreMessage.NODE_EXECUTION_CONDITION_IS_REQUIRED);
        }
    }

    private void requireCanonicalName(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw parameterError(CoreMessage.VALUE_IS_REQUIRED, fieldName);
        }
        if (!value.equals(value.trim())) {
            throw parameterError(
                    CoreMessage.VALUE_MUST_NOT_HAVE_LEADING_OR_TRAILING_WHITESPACE, fieldName);
        }
        if (value.length() > MAX_NAME_LENGTH) {
            throw parameterError(
                    CoreMessage.VALUE_MUST_NOT_EXCEED_VALUE_CHARACTERS, fieldName, MAX_NAME_LENGTH);
        }
    }

    private BusinessException parameterError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.PARAM_ERROR, message, arguments);
    }
}
