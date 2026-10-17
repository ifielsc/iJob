package com.ifelio.ijob.core.job.definition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.ExecuteCondition;
import com.ifelio.ijob.contract.enums.FailStrategy;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.job.JobEdgeRequest;
import com.ifelio.ijob.contract.job.JobNodePolicyRequest;
import com.ifelio.ijob.contract.job.JobNodeRequest;
import com.ifelio.ijob.contract.job.JobUpsertRequest;
import com.ifelio.ijob.core.execute.graph.JobGraphDefinitionValidator;

import org.junit.jupiter.api.Test;

import java.util.List;

class JobDefinitionValidatorTest {
    private final JobDefinitionValidator validator =
            new JobDefinitionValidator(new JobGraphDefinitionValidator());

    @Test
    void shouldAcceptCompleteDefinitionAndOptionalJobFields() {
        JobUpsertRequest request = validRequest();
        request.setTimeout(null);
        request.setEnabled(null);
        request.setDescription(null);

        assertDoesNotThrow(() -> validator.validate(request));
        assertTrue(JobDefinitionValidator.DEFAULT_ENABLED);
    }

    @Test
    void shouldRejectMissingRequestBody() {
        assertValidationError(null, "Job request body is required");
    }

    @Test
    void shouldRejectInvalidJobName() {
        JobUpsertRequest blank = validRequest();
        blank.setName(" ");
        assertValidationError(blank, "jobName is required");

        JobUpsertRequest padded = validRequest();
        padded.setName(" demo");
        assertValidationError(padded, "jobName must not have leading or trailing whitespace");

        JobUpsertRequest tooLong = validRequest();
        tooLong.setName("n".repeat(JobDefinitionValidator.MAX_NAME_LENGTH + 1));
        assertValidationError(tooLong, "jobName must not exceed 128 characters");
    }

    @Test
    void shouldRejectNonPositiveTimeout() {
        JobUpsertRequest zero = validRequest();
        zero.setTimeout(0);
        assertValidationError(zero, "Job timeout must be greater than zero");

        JobUpsertRequest negative = validRequest();
        negative.setTimeout(-1);
        assertValidationError(negative, "Job timeout must be greater than zero");
    }

    @Test
    void shouldRejectDescriptionBeyondSchemaLimit() {
        JobUpsertRequest request = validRequest();
        request.setDescription("d".repeat(JobDefinitionValidator.MAX_DESCRIPTION_LENGTH + 1));

        assertValidationError(request, "Job description must not exceed 500 characters");
    }

    @Test
    void shouldRejectInvalidNodeName() {
        JobUpsertRequest blank = validRequest();
        blank.getNodes().get(0).setName(" ");
        assertValidationError(blank, "nodeName is required");

        JobUpsertRequest padded = validRequest();
        padded.getNodes().get(0).setName("task ");
        assertValidationError(padded, "nodeName must not have leading or trailing whitespace");

        JobUpsertRequest tooLong = validRequest();
        tooLong.getNodes().get(0).setName("n".repeat(JobDefinitionValidator.MAX_NAME_LENGTH + 1));
        assertValidationError(tooLong, "nodeName must not exceed 128 characters");
    }

    @Test
    void shouldRejectMissingNodeTypeOrReference() {
        JobUpsertRequest missingType = validRequest();
        missingType.getNodes().get(0).setNodeType(null);
        assertValidationError(missingType, "Node type is required");

        JobUpsertRequest missingReference = validRequest();
        missingReference.getNodes().get(0).setRefId(null);
        assertValidationError(missingReference, "Node reference ID must be greater than zero");

        JobUpsertRequest invalidReference = validRequest();
        invalidReference.getNodes().get(0).setRefId(0L);
        assertValidationError(invalidReference, "Node reference ID must be greater than zero");
    }

    @Test
    void shouldRejectMissingNodePolicy() {
        JobUpsertRequest request = validRequest();
        request.getNodes().get(0).setPolicy(null);

        assertValidationError(request, "Node policy is required");
    }

    @Test
    void shouldRejectIncompleteNodePolicy() {
        JobUpsertRequest missingFailureStrategy = validRequest();
        missingFailureStrategy.getNodes().get(0).getPolicy().setFailStrategy(null);
        assertValidationError(missingFailureStrategy, "Node failure policy is required");

        JobUpsertRequest missingExecuteCondition = validRequest();
        missingExecuteCondition.getNodes().get(0).getPolicy().setExecuteCondition(null);
        assertValidationError(missingExecuteCondition, "Node execution condition is required");
    }

    @Test
    void shouldDelegateGraphIntegrityValidation() {
        JobUpsertRequest request = validRequest();
        request.setEdges(List.of(edge("task", "job"), edge("job", "task")));

        assertValidationError(request, "Job graph must not contain cycles");
    }

    private JobUpsertRequest validRequest() {
        JobUpsertRequest request = new JobUpsertRequest();
        request.setName("demo");
        request.setTimeout(60);
        request.setEnabled(true);
        request.setDescription("description");
        request.setNodes(
                List.of(
                        node("task", "task", TargetType.TASK, 1L),
                        node("job", "job", TargetType.JOB, 2L)));
        request.setEdges(List.of(edge("task", "job")));
        return request;
    }

    private JobNodeRequest node(String nodeId, String name, TargetType nodeType, long refId) {
        JobNodePolicyRequest policy = new JobNodePolicyRequest();
        policy.setFailStrategy(FailStrategy.STOP);
        policy.setExecuteCondition(ExecuteCondition.ALL_SUCCESS);

        JobNodeRequest node = new JobNodeRequest();
        node.setNodeId(nodeId);
        node.setName(name);
        node.setNodeType(nodeType);
        node.setRefId(refId);
        node.setPolicy(policy);
        return node;
    }

    private JobEdgeRequest edge(String fromNodeId, String toNodeId) {
        JobEdgeRequest edge = new JobEdgeRequest();
        edge.setFromNodeId(fromNodeId);
        edge.setToNodeId(toNodeId);
        return edge;
    }

    private void assertValidationError(JobUpsertRequest request, String expectedMessage) {
        BusinessException exception =
                assertThrows(BusinessException.class, () -> validator.validate(request));
        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
        assertEquals(expectedMessage, exception.getMessage());
    }
}
